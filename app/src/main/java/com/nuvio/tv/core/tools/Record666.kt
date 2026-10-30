package com.nuvio.tv.core.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 666TVF : enregistrer un film ou un épisode pour le regarder sans internet.
 *  - Shield du bas seulement (la tube n'en a pas besoin) ;
 *  - sur la clé USB (stockage amovible) si elle est branchée, sinon dans la mémoire de la Shield
 *    en gardant toujours de la place libre ;
 *  - clé en FAT32 → 4 Go max par fichier (vérifié avant de lancer) ;
 *  - se met en pause tout seul pendant un film (le film passe avant), reprend où il en était.
 */
object Record666 {
    private const val TAG = "Record666"
    private const val INDEX = "enregistrements666.json"
    const val FAT_MAX_BYTES = 4L * 1024 * 1024 * 1024 - 1
    const val MARGIN_INTERNAL = 2L * 1024 * 1024 * 1024
    const val MARGIN_USB = 200L * 1024 * 1024

    enum class Status { QUEUED, RUNNING, DONE, FAILED }

    data class Rec(
        val id: String,
        val title: String,
        val contentName: String,
        val contentId: String?,
        val contentType: String?,
        val videoId: String?,
        val season: Int?,
        val episode: Int?,
        val episodeTitle: String?,
        val year: String?,
        val quality: String,
        val url: String,
        val headers: Map<String, String>,
        val path: String,
        val posterUrl: String?,
        val backdropUrl: String?,
        val logoUrl: String?,
        val total: Long,
        val status: Status,
        val error: String?,
        val addedAt: Long,
        val usb: Boolean,
        /** Quand tu l'as regardé (0 = pas encore). */
        val watchedAt: Long = 0,
        /** « Garder » : jamais effacé tout seul. */
        val keep: Boolean = false,
        /** Lancé tout seul par la cloche (nouvel épisode VF). */
        val auto: Boolean = false
    ) {
        val file: File get() = File(path)
        val partFile: File get() = File("$path.part")
        val posterFile: File get() = File(File(path).parentFile, ".img/${id}_poster.jpg")
        val backdropFile: File get() = File(File(path).parentFile, ".img/${id}_fond.jpg")
        val label: String get() = if (season != null && episode != null) "$contentName · S${season}E$episode" else contentName
    }

    /** Ce qui bouge pendant l'enregistrement (pas sauvegardé). */
    data class Live(val bytes: Long, val total: Long, val bytesPerSec: Long, val note: String?)

    private val _items = MutableStateFlow<List<Rec>>(emptyList())
    val items: StateFlow<List<Rec>> = _items
    private val _live = MutableStateFlow<Map<String, Live>>(emptyMap())
    val live: StateFlow<Map<String, Live>> = _live

    @Volatile private var loaded = false

    // ---------- Où ? ----------

    /** La tube (« sif ») n'enregistre pas ; la Shield du bas (Pro) oui. */
    fun enabledHere(): Boolean = isEnabledDevice(Build.DEVICE)

    fun isEnabledDevice(device: String?): Boolean = !device.orEmpty().trim().equals("sif", ignoreCase = true)

    data class Place(val dir: File, val usb: Boolean, val free: Long, val totalSpace: Long, val maxFile: Long) {
        val margin: Long get() = if (usb) MARGIN_USB else MARGIN_INTERNAL
        val usable: Long get() = (free - margin).coerceAtLeast(0)
        val label: String get() = if (usb) "Clé USB" else "Mémoire de la Shield"
    }

    fun place(context: Context): Place? = runCatching {
        val dirs = context.getExternalFilesDirs(null).toList()
        val usbDir = dirs.drop(1).firstOrNull { d ->
            d != null && runCatching { Environment.getExternalStorageState(d) == Environment.MEDIA_MOUNTED }.getOrDefault(false)
        }
        val base = usbDir ?: dirs.firstOrNull() ?: context.filesDir
        val dir = File(base, "666TVF").apply { mkdirs() }
        val st = StatFs(dir.absolutePath)
        val mounts = runCatching { File("/proc/mounts").readText() }.getOrDefault("")
        Place(
            dir = dir,
            usb = usbDir != null,
            free = st.availableBytes,
            totalSpace = st.totalBytes,
            maxFile = if (usbDir != null) maxFileFor(mounts, dir.absolutePath) else Long.MAX_VALUE
        )
    }.getOrNull()

    /**
     * Règle pure (testée) : taille max d'un fichier selon le système de fichiers de la clé.
     * Sur Android 11 la clé apparaît en « fuse » sous /storage/XXXX-XXXX ; le vrai format est
     * lisible sur /mnt/media_rw/XXXX-XXXX. vfat (FAT32) → 4 Go ; exfat, ntfs, ext4… → pas de limite.
     */
    fun maxFileFor(mounts: String, path: String): Long {
        val uuid = Regex("^/storage/([^/]+)").find(path)?.groupValues?.get(1)
        val lines = mounts.lines().mapNotNull { l ->
            val p = l.trim().split(Regex("\\s+"))
            if (p.size >= 3) p[1] to p[2].lowercase() else null
        }
        val type = (uuid?.let { u -> lines.firstOrNull { (mp, t) -> mp.endsWith("/$u") && t != "fuse" && !t.startsWith("sdcardfs") }?.second }
            ?: lines.filter { (mp, _) -> path.startsWith(mp) }.maxByOrNull { it.first.length }?.second)
            ?: return Long.MAX_VALUE
        return if (type == "vfat" || type == "fat" || type == "msdos" || type == "fat32") FAT_MAX_BYTES else Long.MAX_VALUE
    }

    sealed class Fit {
        data object Ok : Fit()
        data object Unknown : Fit()
        data object TooBigForKey : Fit()
        data object NoRoom : Fit()
    }

    /** Règle pure (testée) : le lien tient-il ? Taille inconnue → on tente, on revérifie au téléchargement. */
    fun fit(size: Long?, usable: Long, maxFile: Long, alreadyQueued: Long = 0): Fit = when {
        size == null || size <= 0 -> Fit.Unknown
        size > maxFile -> Fit.TooBigForKey
        size > usable - alreadyQueued -> Fit.NoRoom
        else -> Fit.Ok
    }

    fun fitText(fit: Fit, place: Place): String = when (fit) {
        Fit.Ok -> "✓ Tient sur ${if (place.usb) "la clé USB" else "la Shield"} (${gb(place.usable)} libres)"
        Fit.Unknown -> "Taille inconnue · ${gb(place.usable)} libres ${if (place.usb) "sur la clé" else "sur la Shield"}"
        Fit.TooBigForKey -> "Trop gros : ta clé est en FAT32, 4 Go max par fichier (formatée en exFAT, plus de limite)"
        Fit.NoRoom -> "Pas assez de place (${gb(place.usable)} libres)"
    }

    fun gb(bytes: Long): String {
        val g = bytes / (1024.0 * 1024 * 1024)
        return if (g >= 10) "${g.toLong()} Go" else String.format(java.util.Locale.FRANCE, "%.1f Go", g)
    }

    // ---------- Liste ----------

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _items.value = readIndex(File(context.filesDir, INDEX))
    }

    fun ready(): List<Rec> = _items.value.filter { it.status == Status.DONE && it.file.exists() }

    fun queuedBytes(): Long = _items.value.filter { it.status == Status.QUEUED || it.status == Status.RUNNING }
        .sumOf { (it.total - it.partFile.length()).coerceAtLeast(0) }

    /** Un réseau est branché (ne garantit pas que la box sort sur internet). */
    fun online(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    /** Internet répond vraiment (petite requête, 4 s max). À appeler hors du fil principal. */
    fun reachable(context: Context): Boolean {
        if (!online(context)) return false
        val quick = client.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
        return listOf("https://www.google.com/generate_204", "https://cloudflare.com/cdn-cgi/trace").any { u ->
            runCatching { quick.newCall(Request.Builder().url(u).head().build()).execute().use { it.code in 200..399 } }.getOrDefault(false)
        }
    }

    data class Request666(
        val title: String,
        val contentName: String,
        val contentId: String?,
        val contentType: String?,
        val videoId: String?,
        val season: Int?,
        val episode: Int?,
        val episodeTitle: String?,
        val year: String?,
        val quality: String,
        val url: String,
        val headers: Map<String, String>,
        val filename: String?,
        val size: Long?,
        val poster: String?,
        val backdrop: String?,
        val logo: String?
    )

    /** Ajoute à la file et démarre. Renvoie un message à afficher. */
    fun enqueue(context: Context, r: Request666, auto: Boolean = false): String {
        init(context)
        val place = place(context) ?: return "Pas de stockage disponible"
        val id = idFor(r.contentId, r.videoId, r.season, r.episode)
        _items.value.firstOrNull { it.id == id && it.status != Status.FAILED }?.let {
            return if (it.status == Status.DONE) "Déjà enregistré" else "Déjà en cours d'enregistrement"
        }
        when (fit(r.size, place.usable, place.maxFile, queuedBytes())) {
            Fit.TooBigForKey -> return "Trop gros pour la clé (4 Go max par fichier)"
            Fit.NoRoom -> return "Pas assez de place (${gb(place.usable)} libres)"
            else -> Unit
        }
        val name = safeName(r.contentName, r.season, r.episode, r.year) + "." + extensionFor(r.filename, r.url)
        val rec = Rec(
            id = id, title = r.title, contentName = r.contentName, contentId = r.contentId, contentType = r.contentType,
            videoId = r.videoId, season = r.season, episode = r.episode, episodeTitle = r.episodeTitle, year = r.year,
            quality = r.quality, url = r.url, headers = r.headers, path = File(place.dir, name).absolutePath,
            posterUrl = r.poster, backdropUrl = r.backdrop, logoUrl = r.logo, total = r.size ?: -1,
            status = Status.QUEUED, error = null, addedAt = System.currentTimeMillis(), usb = place.usb, auto = auto
        )
        update(context) { list -> list.filterNot { it.id == id } + rec }
        Record666Service.start(context)
        return "⬇ Enregistrement lancé · menu Enregistrements"
    }

    fun delete(context: Context, id: String) {
        val rec = _items.value.firstOrNull { it.id == id } ?: return
        cancelled.add(id)
        runCatching { rec.file.delete(); rec.partFile.delete(); rec.posterFile.delete(); rec.backdropFile.delete() }
        update(context) { list -> list.filterNot { it.id == id } }
        _live.value = _live.value - id
    }

    fun retry(context: Context, id: String) {
        update(context) { list -> list.map { if (it.id == id) it.copy(status = Status.QUEUED, error = null) else it } }
        Record666Service.start(context)
    }

    const val AUTO_DELETE_AFTER_MS = 3L * 24 * 3600 * 1000

    /** Règle pure (testée) : vu depuis plus de 3 jours, pas gardé → effacé tout seul. */
    fun shouldAutoDelete(r: Rec, now: Long): Boolean =
        r.status == Status.DONE && !r.keep && r.watchedAt > 0 && now - r.watchedAt >= AUTO_DELETE_AFTER_MS

    fun markWatched(context: Context, id: String, at: Long) =
        update(context) { list -> list.map { if (it.id == id && it.watchedAt == 0L) it.copy(watchedAt = at) else it } }

    fun setKeep(context: Context, id: String, keep: Boolean) =
        update(context) { list -> list.map { if (it.id == id) it.copy(keep = keep) else it } }

    fun idFor(contentId: String?, videoId: String?, season: Int?, episode: Int?): String {
        val base = (contentId ?: videoId ?: "x").replace(Regex("[^A-Za-z0-9]"), "")
        return if (season != null && episode != null) "${base}_s${season}e$episode" else base
    }

    fun safeName(name: String, season: Int?, episode: Int?, year: String?): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "video" }
        return when {
            season != null && episode != null -> clean + " " + String.format(java.util.Locale.ROOT, "S%02dE%02d", season, episode)
            !year.isNullOrBlank() -> "$clean ($year)"
            else -> clean
        }
    }

    fun extensionFor(filename: String?, url: String): String {
        val candidates = listOfNotNull(filename, url.substringBefore('?'))
        for (c in candidates) {
            val ext = c.substringAfterLast('.', "").lowercase()
            if (ext in setOf("mkv", "mp4", "avi", "m4v", "ts", "webm", "mov")) return ext
        }
        return "mkv"
    }

    /** Règle pure (testée) : que faire d'une réponse HTTP. */
    enum class HttpVerdict { RESUME, RESTART, EXPIRED, RETRY, COMPLETE }

    fun verdict(code: Int, requestedFrom: Long): HttpVerdict = when {
        code == 206 -> HttpVerdict.RESUME
        code == 200 -> if (requestedFrom > 0) HttpVerdict.RESTART else HttpVerdict.RESUME
        code == 416 -> HttpVerdict.COMPLETE
        code == 401 || code == 403 || code == 404 || code == 410 -> HttpVerdict.EXPIRED
        else -> HttpVerdict.RETRY
    }

    // ---------- Moteur ----------

    private val cancelled = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Fait tourner la file jusqu'au bout. Renvoie quand il n'y a plus rien à faire. */
    suspend fun runQueue(context: Context) {
        init(context)
        // Un enregistrement coupé net (Shield éteinte) repart.
        update(context) { list -> list.map { if (it.status == Status.RUNNING) it.copy(status = Status.QUEUED) else it } }
        while (true) {
            val next = _items.value.firstOrNull { it.status == Status.QUEUED } ?: break
            cancelled.remove(next.id)
            setStatus(context, next.id, Status.RUNNING, null)
            val (status, error) = runCatching { download(context, next) }.getOrElse { e ->
                Log.w(TAG, "échec ${next.label}", e)
                Status.FAILED to (e.message ?: "erreur")
            }
            if (cancelled.remove(next.id)) continue
            setStatus(context, next.id, status, error)
            _live.value = _live.value - next.id
            if (status == Status.DONE) {
                Announce666.log(context, "⬇ Enregistré : ${next.label}", "enregistrement")
            }
        }
    }

    private suspend fun download(context: Context, rec: Rec): Pair<Status, String?> {
        rec.file.parentFile?.mkdirs()
        fetchImages(rec)
        var attempts = 0
        var total = rec.total
        while (true) {
            if (cancelled.contains(rec.id)) return Status.FAILED to "annulé"
            // Le film passe avant : pause pendant la lecture.
            if (Announce666.inPlayer()) {
                note(rec.id, rec.partFile.length(), total, "⏸ En pause pendant le film")
                delay(5_000)
                continue
            }
            if (!online(context)) {
                note(rec.id, rec.partFile.length(), total, "En attente d'internet…")
                delay(15_000)
                continue
            }
            val from = rec.partFile.length()
            val req = Request.Builder().url(rec.url).apply {
                rec.headers.forEach { (k, v) -> if (!k.equals("Range", true)) header(k, v) }
                if (rec.headers.keys.none { it.equals("User-Agent", true) }) header("User-Agent", "Mozilla/5.0 666TVF")
                if (from > 0) header("Range", "bytes=$from-")
            }.build()
            val outcome: Pair<Status, String?>? = try {
                client.newCall(req).execute().use { resp ->
                    when (verdict(resp.code, from)) {
                        HttpVerdict.EXPIRED -> Status.FAILED to "Lien expiré : relance depuis la liste des liens"
                        HttpVerdict.COMPLETE -> finish(rec)
                        HttpVerdict.RETRY -> { attempts++; null }
                        HttpVerdict.RESTART, HttpVerdict.RESUME -> {
                            val restart = verdict(resp.code, from) == HttpVerdict.RESTART
                            if (restart) rec.partFile.delete()
                            val body = resp.body ?: return@use (Status.FAILED to "réponse vide")
                            val len = body.contentLength()
                            val start = if (restart) 0L else from
                            if (len > 0) total = start + len
                            if (total > 0) {
                                val place = place(context)
                                if (place != null) {
                                    if (total > place.maxFile) return@use (Status.FAILED to "Trop gros pour la clé (4 Go max par fichier)")
                                    if (total - start > place.free - place.margin + 0) return@use (Status.FAILED to "Pas assez de place sur ${place.label.lowercase()}")
                                }
                                if (rec.total != total) update(context) { l -> l.map { if (it.id == rec.id) it.copy(total = total) else it } }
                            }
                            val paused = copyStream(rec, body.byteStream(), start, total)
                            if (paused) null else finish(rec)
                        }
                    }
                }
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                when {
                    msg.contains("EFBIG") || msg.contains("too large", true) -> Status.FAILED to "Trop gros pour la clé (4 Go max par fichier)"
                    msg.contains("ENOSPC") || msg.contains("No space", true) -> Status.FAILED to "Plus de place sur le stockage"
                    rec.file.parentFile?.exists() != true -> Status.FAILED to "Clé USB débranchée"
                    !online(context) -> null
                    else -> { attempts++; null }
                }
            }
            if (outcome != null) return outcome
            if (attempts > 40) return Status.FAILED to "Le lien ne répond plus : relance depuis la liste des liens"
            if (attempts > 0) delay((attempts * 5_000L).coerceAtMost(60_000))
        }
    }

    /** Copie le flux ; renvoie true si on s'est arrêté pour laisser passer un film. */
    private fun copyStream(rec: Rec, input: java.io.InputStream, start: Long, total: Long): Boolean {
        var done = start
        var lastTick = System.currentTimeMillis()
        var lastBytes = done
        var speed = 0L
        FileOutputStream(rec.partFile, start > 0).use { out ->
            val buf = ByteArray(256 * 1024)
            input.use { inp ->
                while (true) {
                    if (cancelled.contains(rec.id)) return true
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - lastTick >= 1_000) {
                        speed = (done - lastBytes) * 1000 / (now - lastTick)
                        lastTick = now; lastBytes = done
                        note(rec.id, done, total, null, speed)
                        if (Announce666.inPlayer()) return true
                    }
                }
            }
        }
        if (total > 0 && done < total) throw IOException("coupé à $done/$total")
        return false
    }

    private fun finish(rec: Rec): Pair<Status, String?> {
        if (!rec.partFile.exists() && rec.file.exists()) return Status.DONE to null
        return if (rec.partFile.renameTo(rec.file)) Status.DONE to null else Status.FAILED to "Impossible de finir le fichier"
    }

    private fun fetchImages(rec: Rec) {
        listOf(rec.posterUrl to rec.posterFile, rec.backdropUrl to rec.backdropFile).forEach { (url, f) ->
            if (url.isNullOrBlank() || f.exists() || !url.startsWith("http")) return@forEach
            runCatching {
                f.parentFile?.mkdirs()
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.byteStream()?.use { i -> FileOutputStream(f).use { o -> i.copyTo(o) } }
                }
            }
        }
    }

    private fun note(id: String, bytes: Long, total: Long, note: String?, speed: Long = 0) {
        _live.value = _live.value + (id to Live(bytes, total, speed, note))
    }

    private fun setStatus(context: Context, id: String, s: Status, error: String?) =
        update(context) { list -> list.map { if (it.id == id) it.copy(status = s, error = error) else it } }

    @Synchronized
    private fun update(context: Context, f: (List<Rec>) -> List<Rec>) {
        val next = f(_items.value)
        _items.value = next
        runCatching { File(context.filesDir, INDEX).writeText(toJson(next).toString()) }
    }

    // ---------- JSON ----------

    fun toJson(list: List<Rec>): JSONArray = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id); put("title", r.title); put("contentName", r.contentName)
                put("contentId", r.contentId); put("contentType", r.contentType); put("videoId", r.videoId)
                r.season?.let { put("season", it) }; r.episode?.let { put("episode", it) }
                put("episodeTitle", r.episodeTitle); put("year", r.year); put("quality", r.quality)
                put("url", r.url); put("headers", JSONObject(r.headers)); put("path", r.path)
                put("posterUrl", r.posterUrl); put("backdropUrl", r.backdropUrl); put("logoUrl", r.logoUrl)
                put("total", r.total); put("status", r.status.name); put("error", r.error)
                put("addedAt", r.addedAt); put("usb", r.usb)
                put("watchedAt", r.watchedAt); put("keep", r.keep); put("auto", r.auto)
            })
        }
    }

    fun fromJson(arr: JSONArray): List<Rec> = (0 until arr.length()).mapNotNull { i ->
        runCatching {
            val o = arr.getJSONObject(i)
            fun s(k: String): String? = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
            val h = o.optJSONObject("headers")
            Rec(
                id = o.getString("id"), title = s("title") ?: "", contentName = s("contentName") ?: "",
                contentId = s("contentId"), contentType = s("contentType"), videoId = s("videoId"),
                season = if (o.has("season")) o.getInt("season") else null,
                episode = if (o.has("episode")) o.getInt("episode") else null,
                episodeTitle = s("episodeTitle"), year = s("year"), quality = s("quality") ?: "",
                url = o.getString("url"),
                headers = h?.keys()?.asSequence()?.associateWith { h.getString(it) } ?: emptyMap(),
                path = o.getString("path"), posterUrl = s("posterUrl"), backdropUrl = s("backdropUrl"), logoUrl = s("logoUrl"),
                total = o.optLong("total", -1), status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.FAILED),
                error = s("error"), addedAt = o.optLong("addedAt"), usb = o.optBoolean("usb"),
                watchedAt = o.optLong("watchedAt"), keep = o.optBoolean("keep"), auto = o.optBoolean("auto")
            )
        }.getOrNull()
    }

    private fun readIndex(f: File): List<Rec> = runCatching { fromJson(JSONArray(f.readText())) }.getOrDefault(emptyList())

    /** Qualité courte lue dans le nom du lien (pour l'affichage). */
    fun qualityOf(text: String): String {
        val t = text.lowercase()
        val res = when {
            "2160" in t || "4k" in t || "uhd" in t -> "4K"
            "1080" in t -> "1080p"
            "720" in t -> "720p"
            else -> ""
        }
        val vf = when {
            "vfq" in t -> "VFQ"
            "vff" in t || "truefrench" in t -> "VFF"
            "vf" in t || "french" in t || "multi" in t || "🇫🇷" in text -> "VF"
            else -> ""
        }
        return listOf(res, vf).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

class Record666Service : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, "666TVF enregistrements", NotificationManager.IMPORTANCE_LOW))
        }
        val n = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
            else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("666TVF")
            .setContentText("Enregistrement en cours")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive != true) {
            job = scope.launch {
                runCatching { Record666.runQueue(applicationContext) }
                if (isActive) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "666tv_rec"
        private const val ID = 6661

        fun start(context: Context) {
            val i = Intent(context, Record666Service::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
            }.onFailure { Log.w("Record666", "service non démarré", it) }
        }
    }
}
