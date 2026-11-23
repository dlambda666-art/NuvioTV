package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * FrankenStream (18/11) : son d'ambiance de l'Abattoir.
 *
 * Frank ne contient aucun son : il lit un fichier choisi sur la Shield (Download, disque de 4 To).
 * Le fichier reste sur la Shield, jamais envoyé ni publié. Coupé par défaut.
 * 23/11 : il ne joue plus qu'une fois, ~20 s, quand les portes de l'Abattoir s'ouvrent après le cinéma.
 * Silence pendant les films, Orange TV, l'écran de veille et la veille de la Shield.
 */
object AbattoirSound666 {
    private const val PREFS = "666tv_abattoir_sound"
    /**
     * 23/11 (David) : plus de répétition. 0 = coupé, 1 = une seule fois, à l'ouverture des portes
     * de l'Abattoir après le cinéma (lancement, mise à jour, allumage de la TV).
     */
    val MODES = listOf(0, 1)

    /** Durée jouée à l'ouverture des portes (le fichier est repris en boucle s'il est plus court). */
    const val PLAY_MS = 20_000L
    const val FADE_IN_MS = 400L
    const val FADE_OUT_MS = 3_000L

    /** Règle pure (testée) : un ancien réglage (toutes les 10 s, 20 s, 1 min, boucle…) devient « aux portes ». */
    fun migrate(seconds: Int): Int = if (seconds == 0) 0 else 1
    val VOLUMES = listOf(10, 20, 30, 40, 60)
    
    data class Settings(val mode: Int, val uri: String?, val name: String?, val volume: Int)

    fun settings(context: Context): Settings = runCatching {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 1.0.154 rangeait des minutes sous « mode » : repris en secondes
        val every = if (p.contains("every_s")) p.getInt("every_s", 0) else p.getInt("mode", 0).let { if (it > 0) it * 60 else it }
        Settings(migrate(every), p.getString("uri", null), p.getString("name", null), p.getInt("volume", 20))
    }.getOrDefault(Settings(0, null, null, 20))

    private fun edit(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply() }

    fun nextMode(context: Context): Int {
        val cur = settings(context).mode
        val next = MODES[(MODES.indexOf(cur).coerceAtLeast(0) + 1) % MODES.size]
        edit(context) { putInt("every_s", next) }
        return next
    }

    fun nextVolume(context: Context): Int {
        val cur = settings(context).volume
        val next = VOLUMES[(VOLUMES.indexOf(cur).coerceAtLeast(0) + 1) % VOLUMES.size]
        edit(context) { putInt("volume", next) }
        return next
    }

    fun setFile(context: Context, uri: String, name: String) = edit(context) { putString("uri", uri); putString("name", name) }

    fun modeLabel(mode: Int): String = if (mode == 0) "coupé" else "à l'ouverture des portes"

    /**
     * Règle pure (testée) : les portes de l'Abattoir s'ouvrent (fin du cinéma) : faut-il jouer ?
     * Jamais si coupé, sans fichier, pendant un film, Frank pas au premier plan ou déjà en train de jouer.
     */
    fun shouldPlayAtDoors(mode: Int, hasFile: Boolean, frankInFront: Boolean, inPlayer: Boolean, playing: Boolean): Boolean =
        mode != 0 && hasFile && frankInFront && !inPlayer && !playing

    /** Règle pure (testée) : volume (0..1) à l'instant t des 20 s : montée courte, fondu de fin de 3 s. */
    fun fadeGain(tMs: Long, volumePct: Int, totalMs: Long = PLAY_MS): Float {
        val v = volumePct.coerceIn(0, 100) / 100f
        val k = when {
            tMs < 0 || tMs >= totalMs -> 0f
            tMs < FADE_IN_MS -> tMs.toFloat() / FADE_IN_MS
            tMs > totalMs - FADE_OUT_MS -> (totalMs - tMs).toFloat() / FADE_OUT_MS
            else -> 1f
        }
        return v * k.coerceIn(0f, 1f)
    }

    /** Appelé par le cinéma quand les portes commencent à s'ouvrir. */
    fun onDoorsOpening(context: Context) {
        val s = settings(context)
        val inPlayer = com.nuvio.tv.core.tools.Announce666.inPlayer()
        val front = com.nuvio.tv.core.wake.WakeLaunch666.resumed
        if (shouldPlayAtDoors(s.mode, s.uri != null, front, inPlayer, isPlaying)) play(context, s.uri!!, s.volume)
    }

    // ---------- Lecture ----------
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var lastStartAt = -1L
    val isPlaying: Boolean get() = player != null

    fun sinceLastStart(now: Long = System.currentTimeMillis()): Long = if (lastStartAt < 0) -1L else now - lastStartAt

    fun play(context: Context, uri: String, volumePct: Int) {
        stop()
        val app = context.applicationContext
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(app, Uri.parse(uri))
            mp.isLooping = true            // un fichier plus court que 20 s repart du début
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepare()
            mp.setVolume(0f, 0f)
            mp.start()
            player = mp
            val start = System.currentTimeMillis()
            lastStartAt = start
            val tick = object : Runnable {
                override fun run() {
                    val p = player ?: return
                    val t = System.currentTimeMillis() - start
                    // un film qui démarre, Orange TV, la veille : on coupe tout de suite
                    if (t >= PLAY_MS || com.nuvio.tv.core.tools.Announce666.inPlayer() || !com.nuvio.tv.core.wake.WakeLaunch666.resumed) {
                        stop(); return
                    }
                    val g = fadeGain(t, volumePct)
                    runCatching { p.setVolume(g, g) }
                    handler.postDelayed(this, 50L)
                }
            }
            handler.post(tick)
        }.onFailure { stop() }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    private val AUDIO_EXT = Regex("""\.(mp3|m4a|aac|ogg|oga|opus|wav|flac|wma|amr)$""", RegexOption.IGNORE_CASE)

    /** Règle pure (testée) : ce nom de fichier est-il un son ? */
    fun isAudioName(name: String): Boolean = AUDIO_EXT.containsMatchIn(name.trim())

    /**
     * 18/11 (piège Shield du haut) : un fichier reçu par « Send Files to TV » n'est pas toujours rangé
     * dans la médiathèque d'Android → invisible dans « Choisir ». On regarde donc aussi directement les
     * dossiers Download (et les sous-dossiers), sur la Shield et sur les disques branchés.
     */
    private fun directAudioFiles(context: Context): List<Pair<String, String>> {
        val roots = LinkedHashSet<java.io.File>()
        runCatching { roots += android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS) }
        runCatching { roots += java.io.File(android.os.Environment.getExternalStorageDirectory(), "Download") }
        runCatching { roots += java.io.File(android.os.Environment.getExternalStorageDirectory(), "Music") }
        runCatching {
            java.io.File("/storage").listFiles()?.forEach { v ->
                if (v.name != "self" && v.name != "emulated") { roots += java.io.File(v, "Download"); roots += v }
            }
        }
        val out = ArrayList<Pair<String, String>>()
        fun walk(dir: java.io.File, depth: Int) {
            if (depth > 2 || out.size >= 40) return
            val list = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (f in list) {
                if (f.isDirectory) { if (depth < 2 && !f.name.startsWith(".") && f.name != "Android") walk(f, depth + 1) }
                else if (isAudioName(f.name)) {
                    out += android.net.Uri.fromFile(f).toString() to f.name
                    // et on le range dans la médiathèque pour la prochaine fois
                    runCatching { android.media.MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null) }
                }
            }
        }
        roots.forEach { r -> if (r.isDirectory) walk(r, if (r.parentFile?.path == "/storage") 1 else 0) }
        return out
    }

    /** Liste des fichiers audio de la Shield (Download, disque USB) : médiathèque d'Android + dossiers Download. */
    fun audioFiles(context: Context): List<Pair<String, String>> =
        (mediaStoreAudioFiles(context) + directAudioFiles(context)).distinctBy { it.second.lowercase() }.take(40)

    private fun mediaStoreAudioFiles(context: Context): List<Pair<String, String>> = runCatching {
        val out = ArrayList<Pair<String, String>>()
        val volumes = runCatching { android.provider.MediaStore.getExternalVolumeNames(context) }.getOrDefault(setOf("external"))
        for (vol in volumes) {
            val base = android.provider.MediaStore.Audio.Media.getContentUri(vol)
            context.contentResolver.query(
                base,
                arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, android.provider.MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 40) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    out += android.content.ContentUris.withAppendedId(base, id).toString() to name
                }
            }
        }
        out
    }.getOrDefault(emptyList())
}

