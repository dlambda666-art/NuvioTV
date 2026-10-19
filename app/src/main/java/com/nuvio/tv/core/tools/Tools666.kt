package com.nuvio.tv.core.tools

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val PREFS = "666tv_outils"

/**
 * 666TV : mode léger. Sur une petite Shield (tube, 32 bits / peu de mémoire), on coupe les
 * animations lourdes : GIF des tuiles, effet poussière. « Auto » le décide tout seul.
 */
object Light666 {
    enum class Mode { AUTO, ON, OFF }

    var mode by mutableStateOf(Mode.AUTO)
        private set
    var lowEndDevice by mutableStateOf(false)
        private set

    val active: Boolean get() = mode == Mode.ON || (mode == Mode.AUTO && lowEndDevice)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = runCatching { Mode.valueOf(p.getString("light_mode", Mode.AUTO.name)!!) }.getOrDefault(Mode.AUTO)
        lowEndDevice = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            Build.SUPPORTED_64_BIT_ABIS.isEmpty() || am.isLowRamDevice || mi.totalMem < 2_600L * 1024 * 1024
        }.getOrDefault(false)
    }

    fun setMode(context: Context, value: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("light_mode", value.name).apply()
        mode = value
    }
}

/** 666TV : heure et météo en haut de l'accueil. */
object Clock666 {
    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("clock", true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("clock", value).apply()
        enabled = value
    }

    data class Weather(val tempC: Int, val icon: String)

    private var cached: Weather? = null
    private var cachedAt = 0L

    /** Météo du coin (position approximative d'après la connexion, rien n'est demandé). */
    fun weather(): Weather? {
        if (cached != null && System.currentTimeMillis() - cachedAt < 45 * 60_000L) return cached
        return runCatching {
            val geo = Http666.get("https://ipwho.is/?fields=latitude,longitude") ?: return null
            val lat = Regex("\"latitude\"\\s*:\\s*(-?[0-9.]+)").find(geo)?.groupValues?.get(1) ?: return null
            val lon = Regex("\"longitude\"\\s*:\\s*(-?[0-9.]+)").find(geo)?.groupValues?.get(1) ?: return null
            val w = Http666.get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
            ) ?: return null
            val cur = w.substringAfter("\"current\"", "")
            val t = Regex("\"temperature_2m\"\\s*:\\s*(-?[0-9.]+)").find(cur)?.groupValues?.get(1)?.toDouble() ?: return null
            val code = Regex("\"weather_code\"\\s*:\\s*([0-9]+)").find(cur)?.groupValues?.get(1)?.toInt() ?: 0
            Weather(Math.round(t).toInt(), iconFor(code))
        }.getOrNull()?.also { cached = it; cachedAt = System.currentTimeMillis() }
    }

    private fun iconFor(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⛈"
        else -> "☁"
    }
}

internal object Http666 {
    /** Code HTTP (ou -1 si injoignable). */
    fun status(url: String, timeoutMs: Int = 8000): Int = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "666TV")
        try { c.responseCode } finally { c.disconnect() }
    }.getOrDefault(-1)

    fun get(url: String, timeoutMs: Int = 8000): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "666TV")
        try {
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }.getOrNull()
}

/**
 * 666TV : sauvegarde complète (réglages, plugins et leurs configs, Mes applis, tombes…)
 * dans Téléchargements/666tv-sauvegarde.zip. Faite toute seule une fois par semaine.
 */
object Backup666 {
    const val FILE_NAME = "666tv-sauvegarde.zip"
    private const val WEEK_MS = 7L * 24 * 3600 * 1000

    fun lastBackupAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_backup", 0L)

    fun autoBackupIfDue(context: Context) {
        if (System.currentTimeMillis() - lastBackupAt(context) < WEEK_MS) return
        runCatching { backup(context) }
    }

    private fun roots(context: Context): List<Pair<String, File>> {
        val data = context.filesDir.parentFile ?: context.filesDir
        return listOf(
            "datastore" to File(context.filesDir, "datastore"),
            "shared_prefs" to File(data, "shared_prefs"),
            "nobackup" to context.noBackupFilesDir
        )
    }

    /** Crée la sauvegarde. Renvoie le nombre de fichiers sauvegardés. */
    fun backup(context: Context): Int {
        val bytes = ByteArrayOutputStream()
        var count = 0
        ZipOutputStream(bytes).use { zip ->
            roots(context).forEach { (tag, dir) ->
                if (!dir.isDirectory) return@forEach
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    if (tag == "nobackup" && !f.name.startsWith("plugin_settings_666")) return@forEach
                    if (f.name.endsWith(".tmp") || f.length() > 8L * 1024 * 1024) return@forEach
                    zip.putNextEntry(ZipEntry(tag + "/" + f.relativeTo(dir).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        val data = bytes.toByteArray()
        // Copie 1 : dossier de l'appli (rapide à relire).
        runCatching {
            context.getExternalFilesDir(null)?.let { File(it, FILE_NAME).writeBytes(data) }
        }
        // Copie 2 : Téléchargements (survit à un « vider les données »).
        runCatching { writeToDownloads(context, data) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_backup", System.currentTimeMillis()).apply()
        return count
    }

    private fun writeToDownloads(context: Context, data: ByteArray) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        findOwnDownload(context)?.let { runCatching { resolver.delete(it, null, null) } }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(data) }
    }

    private fun findOwnDownload(context: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(FILE_NAME),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                if (c.moveToFirst()) android.content.ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)
                ) else null
            }
        }.getOrNull()
    }

    /** Ouvre la dernière sauvegarde trouvée sans rien demander (null si aucune). */
    fun openLatest(context: Context): InputStream? {
        findOwnDownload(context)?.let { uri ->
            runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.let { return it }
        }
        val local = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }
        if (local != null && local.isFile) return local.inputStream()
        return null
    }

    /** Remet les fichiers en place puis redémarre 666TV. Renvoie false si la sauvegarde est illisible. */
    fun restore(context: Context, input: InputStream): Boolean {
        val rootByTag = roots(context).toMap()
        val entries = mutableListOf<Pair<File, ByteArray>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val tag = e.name.substringBefore('/')
                val rel = e.name.substringAfter('/', "")
                val root = rootByTag[tag] ?: continue
                if (rel.isBlank() || rel.contains("..")) continue
                entries += File(root, rel) to zip.readBytes()
            }
        }
        if (entries.isEmpty()) return false
        entries.forEach { (f, b) -> f.parentFile?.mkdirs(); f.writeBytes(b) }
        restart(context)
        return true
    }

    private fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) runCatching {
            val pi = android.app.PendingIntent.getActivity(
                context, 666, launch,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_CANCEL_CURRENT
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 800, pi)
        }
        Runtime.getRuntime().exit(0)
    }
}
