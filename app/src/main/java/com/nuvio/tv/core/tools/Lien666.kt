package com.nuvio.tv.core.tools

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 666TVF (11/11) : la « Mémoire 666 ».
 *  - ce que chaque Shield apprend (groupes VF, fichiers menteurs, titres VF vérifiés) est partagé avec
 *    l'autre, sur le réseau de la maison seulement, toutes les 6 h ;
 *  - une copie part chaque nuit sur la clé USB d'en bas (repart si la Shield est réinitialisée) ;
 *  - la Shield du haut voit et lit les enregistrements d'en bas.
 * Jamais de clé, de jeton debrid ni d'adresse de config : l'adresse d'origine des enregistrements
 * est retirée avant de partir.
 */
object Memoire666 {
    const val FILE = "666tv_memoire.json"

    fun export(context: Context): JSONObject = JSONObject()
        .put("v", 1)
        .put("from", Build.DEVICE)
        .put("at", System.currentTimeMillis())
        .put("groups", Groups666.exportJson())
        .put("nofrench", JSONArray(NoFrench666.exportList(context)))
        .put("vf", VfCheck666.exportVf())

    data class Merge(val groups: Int, val liars: Int, val vf: Int) {
        val text: String get() = "$groups groupe(s), $liars fichier(s) menteur(s), $vf titre(s) VF"
    }

    fun import(context: Context, json: JSONObject): Merge {
        val g = json.optJSONObject("groups")?.let { Groups666.importJson(it) } ?: 0
        val arr = json.optJSONArray("nofrench")
        val l = if (arr == null) 0 else NoFrench666.importList(context, (0 until arr.length()).map { arr.optString(it) })
        val v = json.optJSONObject("vf")?.let { VfCheck666.importVf(it) } ?: 0
        return Merge(g, l, v)
    }

    /** Copie sur la clé USB (Shield du bas). Null si pas de clé. */
    fun saveToUsb(context: Context): File? = runCatching {
        val place = Record666.place(context)?.takeIf { it.usb } ?: return null
        File(place.dir, FILE).also { it.writeText(export(context).toString()) }
    }.getOrNull()

    fun usbCopy(context: Context): File? = runCatching {
        Record666.place(context)?.takeIf { it.usb }?.let { File(it.dir, FILE) }?.takeIf { it.exists() }
    }.getOrNull()

    fun restoreFromUsb(context: Context): Merge? = runCatching {
        val f = usbCopy(context) ?: return null
        import(context, JSONObject(f.readText()))
    }.getOrNull()
}

object Lien666 {
    private const val TAG = "Lien666"
    const val PORT = 8666
    const val SERVICE_TYPE = "_six66lien._tcp."
    private const val PREFS = "666tv_lien"

    data class Peer(val name: String, val host: String, val port: Int) {
        val base: String get() = "http://${if (host.contains(':')) "[$host]" else host}:$port"
    }

    /** Un film est en cours sur cette Shield (on n'y installe rien à ce moment-là). */
    @Volatile var playing: Boolean = false

    @Volatile var peer: Peer? = null
        private set
    @Volatile private var started = false
    private var server: Server? = null
    private var appCtx: Context? = null
    private var nsdManager: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appCtx = app
        // dernière adresse connue (si la découverte tarde)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("peer", null)?.split('|')?.takeIf { it.size == 3 }?.let {
            peer = Peer(it[0], it[1], it[2].toIntOrNull() ?: PORT)
        }
        runCatching { server = Server(app).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) } }
            .onFailure { Log.w(TAG, "serveur : ${it.message}") }
        runCatching { nsd(app) }.onFailure { Log.w(TAG, "nsd : ${it.message}") }
    }

    /**
     * Modem relancé, wifi coupé : les adresses changent et l'annonce sur le réseau se perd.
     * On réannonce et on recherche l'autre Shield, puis on attend un peu qu'elle réponde.
     */
    private suspend fun rediscover() {
        val app = appCtx ?: return
        val nsd = nsdManager
        if (nsd != null) {
            regListener?.let { runCatching { nsd.unregisterService(it) } }
            discListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        }
        runCatching { nsd(app) }
        kotlinx.coroutines.delay(8_000)
    }

    @Suppress("DEPRECATION")
    private fun nsd(app: Context) {
        val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = nsd
        var own: String? = null
        val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { own = info.serviceName }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        regListener = reg
        nsd.registerService(NsdServiceInfo().apply {
            serviceName = "666TV-" + Build.DEVICE
            serviceType = SERVICE_TYPE
            port = PORT
        }, NsdManager.PROTOCOL_DNS_SD, reg)
        val disc = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == own || info.serviceName == "666TV-" + Build.DEVICE) return
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val p = Peer(info.serviceName, host, info.port)
                            peer = p
                            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("peer", "${p.name}|${p.host}|${p.port}").apply()
                        }
                    })
                }
            }
        }
        discListener = disc
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, disc)
    }

    private fun get(url: String, timeoutMs: Int = 8_000): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = timeoutMs; readTimeout = timeoutMs
            try { if (responseCode == 200) inputStream.bufferedReader().readText() else null } finally { disconnect() }
        }
    }.getOrNull()

    /** Demande à l'autre Shield ; si elle ne répond plus (modem relancé…), on la recherche et on réessaie. */
    private suspend fun fetch(path: String): String? {
        peer?.let { p -> get(p.base + path)?.let { return it } }
        rediscover()
        peer?.let { p -> get(p.base + path)?.let { return it } }
        // 16/11 : Shield du haut passée en WiFi → la recherche automatique (mDNS) ne passe plus entre le
        // WiFi et le câble. On la cherche alors adresse par adresse sur le réseau de la maison.
        scanSubnet() ?: return null
        return peer?.let { p -> get(p.base + path) }
    }

    @Volatile private var lastScanAt = 0L

    /**
     * Règle pure (testée) : les adresses à essayer sur le réseau de la maison (même /24 que cette Shield),
     * la dernière adresse connue de l'autre Shield en premier, sans la nôtre.
     */
    fun subnetCandidates(ownIp: String, preferred: String?): List<String> {
        val parts = ownIp.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        val all = (1..254).map { "$prefix.$it" }.filter { it != ownIp }
        val first = preferred?.takeIf { it in all }
        return if (first != null) listOf(first) + (all - first) else all
    }

    /** 22/11 : l'adresse de cette Shield sur le réseau de la maison (pour le QR code du rapport). */
    fun ownIp(context: Context): String? = ownIpv4(context.applicationContext)

    private fun ownIpv4(app: Context): String? = runCatching {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()

    /** Cherche l'autre Shield FrankenStream sur le réseau (une fois toutes les 2 min au plus). */
    private suspend fun scanSubnet(): Peer? = withContext(Dispatchers.IO) {
        val app = appCtx ?: return@withContext null
        val now = System.currentTimeMillis()
        if (now - lastScanAt < 120_000L) return@withContext null
        lastScanAt = now
        val own = ownIpv4(app) ?: return@withContext null
        val candidates = subnetCandidates(own, peer?.host)
        for (chunk in candidates.chunked(32)) {
            val found = kotlinx.coroutines.coroutineScope {
                chunk.map { ip ->
                    async {
                        val raw = get("http://$ip:$PORT/version", timeoutMs = 800) ?: return@async null
                        runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.has("code") }?.let { j ->
                            Peer("666TV-" + j.optString("device").ifBlank { "autre" }, ip, PORT)
                        }
                    }
                }.awaitAll().firstOrNull { it != null }
            }
            if (found != null) {
                peer = found
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("peer", "${found.name}|${found.host}|${found.port}").apply()
                Log.i(TAG, "autre Shield retrouvée par balayage : ${found.host}")
                return@withContext found
            }
        }
        null
    }

    /** Échange avec l'autre Shield. Null si elle ne répond pas. */
    suspend fun syncNow(context: Context): Memoire666.Merge? = withContext(Dispatchers.IO) {
        val raw = fetch("/memoire") ?: return@withContext null
        val m = runCatching { Memoire666.import(context, JSONObject(raw)) }.getOrNull() ?: return@withContext null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("sync_at", System.currentTimeMillis()).putString("sync_text", m.text).putBoolean("silent_alerted", false).apply()
        m
    }

    fun lastSync(context: Context): Pair<Long, String?> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .let { it.getLong("sync_at", 0L) to it.getString("sync_text", null) }

    /** Règle pure (testée) : plus d'échange depuis 3 jours alors qu'il y en a déjà eu → la cloche, une fois. */
    fun silentTooLong(lastSyncAt: Long, alerted: Boolean, now: Long): Boolean =
        lastSyncAt > 0 && !alerted && now - lastSyncAt > 3 * 24 * 3600_000L

    /** Vrai une seule fois par silence ; réarmé au prochain échange réussi. */
    fun takeSilentAlert(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!silentTooLong(p.getLong("sync_at", 0L), p.getBoolean("silent_alerted", false), now)) return false
        p.edit().putBoolean("silent_alerted", true).apply()
        return true
    }

    data class PeerVersion(val code: Long, val name: String, val playing: Boolean)

    suspend fun peerVersion(): PeerVersion? = withContext(Dispatchers.IO) {
        val raw = fetch("/version") ?: return@withContext null
        runCatching { JSONObject(raw).let { PeerVersion(it.getLong("code"), it.optString("name"), it.optBoolean("playing")) } }.getOrNull()
    }

    /** Les enregistrements prêts de l'autre Shield. */
    suspend fun remoteRecs(): List<Record666.Rec> = withContext(Dispatchers.IO) {
        val raw = fetch("/recs") ?: return@withContext emptyList()
        runCatching { Record666.fromJson(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    /** 16/11 : le journal des coupures de l'autre Shield (heures seulement). Null si elle ne répond pas. */
    suspend fun remoteNetLog(): List<Net666.Entry>? = withContext(Dispatchers.IO) {
        val raw = fetch("/netlog") ?: return@withContext null
        Net666.fromJson(raw)
    }

    /**
     * 18/11 (réparation, Shield du haut) : récupère le son de l'Abattoir choisi sur l'autre Shield
     * (seulement ce fichier-là, rien d'autre) et le range dans le dossier privé de Frank ici.
     * Renvoie le nom du fichier, ou null si l'autre Shield ne répond pas / n'a pas de son choisi.
     */
    suspend fun fetchAbattoirSound(context: Context): String? = withContext(Dispatchers.IO) {
        val info = fetch("/abattoir-son/info")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@withContext null
        val name = safeSoundName(info.optString("name")) ?: return@withContext null
        val base = peer?.base ?: return@withContext null
        val dir = File(context.filesDir, "abattoir666").apply { mkdirs() }
        val out = File(dir, name)
        val ok = runCatching {
            (URL("$base/abattoir-son").openConnection() as HttpURLConnection).run {
                connectTimeout = 8_000; readTimeout = 20_000
                try {
                    if (responseCode != 200) return@run false
                    val tmp = File(dir, "$name.part")
                    inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    if (tmp.length() <= 0L || tmp.length() > MAX_SOUND_BYTES) { tmp.delete(); return@run false }
                    dir.listFiles()?.filter { it.name != tmp.name }?.forEach { it.delete() }
                    tmp.renameTo(out)
                } finally { disconnect() }
            }
        }.getOrDefault(false)
        if (ok) name else null
    }

    const val MAX_SOUND_BYTES = 20L * 1024 * 1024

    /** Règle pure (testée) : nom de fichier son acceptable (pas de dossier, extension audio). */
    fun safeSoundName(raw: String?): String? {
        val n = raw?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        if (n.isEmpty() || n.startsWith(".") || n.length > 120) return null
        return n.takeIf { com.nuvio.tv.ui.screens.home.AbattoirSound666.isAudioName(it) }
    }

    fun recUrl(id: String): String? = peer?.let { it.base + "/rec/" + java.net.URLEncoder.encode(id, "UTF-8") }

    /** Règle pure (testée) : en-tête Range « bytes=a-b » → (début, fin incluse), ou null. */
    fun range(header: String?, size: Long): Pair<Long, Long>? {
        val m = Regex("""bytes=(\d*)-(\d*)""").find(header ?: return null) ?: return null
        val a = m.groupValues[1]; val b = m.groupValues[2]
        val (start, end) = when {
            a.isEmpty() && b.isNotEmpty() -> (size - b.toLong()).coerceAtLeast(0) to size - 1
            a.isNotEmpty() -> a.toLong() to (b.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1))
            else -> return null
        }
        return if (start in 0..end && end < size) start to end else null
    }

    private class Server(private val app: Context) : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response = runCatching {
            if (session.method != Method.GET) return@runCatching notFound()
            val uri = session.uri
            when {
                // 12/11 : quelle version tourne ici (et un film est-il en cours ?) — pour Maj666
                uri == "/version" -> newFixedLengthResponse(Response.Status.OK, "application/json",
                    JSONObject().put("code", Maj666.myCode(app)).put("name", Maj666.myName(app)).put("playing", playing).put("device", Build.DEVICE).toString())
                // 16/11 : journal des coupures réseau (heures et durées seulement)
                uri == "/netlog" -> newFixedLengthResponse(Response.Status.OK, "application/json", Net666.export(app))
                // 22/11 : le « Rapport pour Claude » (texte filtré : ni clé, ni token, ni lien de config)
                uri == Rapport666.PATH -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", Rapport666.page(Rapport666.build(app)))
                uri == "/memoire" -> newFixedLengthResponse(Response.Status.OK, "application/json", Memoire666.export(app).toString())
                uri == "/recs" -> {
                    // l'adresse d'origine (lien debrid) et ses en-têtes ne quittent jamais la Shield
                    val list = if (Record666.enabledHere()) Record666.ready().map { it.copy(url = "", headers = emptyMap()) } else emptyList()
                    newFixedLengthResponse(Response.Status.OK, "application/json", Record666.toJson(list).toString())
                }
                // 18/11 : le son de l'Abattoir choisi ici (seulement lui), pour l'autre Shield
                uri == "/abattoir-son/info" -> {
                    val s = com.nuvio.tv.ui.screens.home.AbattoirSound666.settings(app)
                    if (s.uri == null || s.name == null) notFound()
                    else newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().put("name", s.name).toString())
                }
                uri == "/abattoir-son" -> {
                    val s = com.nuvio.tv.ui.screens.home.AbattoirSound666.settings(app)
                    val u = s.uri ?: return@runCatching notFound()
                    val bytes = app.contentResolver.openInputStream(android.net.Uri.parse(u))?.use { it.readBytes() }
                        ?: return@runCatching notFound()
                    if (bytes.size > MAX_SOUND_BYTES) return@runCatching notFound()
                    newFixedLengthResponse(Response.Status.OK, "audio/mpeg", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
                }
                uri.startsWith("/rec/") -> {
                    val id = java.net.URLDecoder.decode(uri.removePrefix("/rec/"), "UTF-8")
                    val rec = Record666.ready().firstOrNull { it.id == id } ?: return@runCatching notFound()
                    file(rec.file, session.headers["range"])
                }
                else -> notFound()
            }
        }.getOrElse { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "erreur") }

        private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")

        private fun file(f: File, rangeHeader: String?): Response {
            val size = f.length()
            val mime = if (f.name.endsWith(".mp4", true)) "video/mp4" else "video/x-matroska"
            val r = range(rangeHeader, size)
            return if (r == null) {
                newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(f), size).apply { addHeader("Accept-Ranges", "bytes") }
            } else {
                val (start, end) = r
                val input = FileInputStream(f).apply { skip(start) }
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, input, end - start + 1).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes $start-$end/$size")
                }
            }
        }
    }
}
