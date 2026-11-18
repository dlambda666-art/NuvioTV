package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * FrankenStream (18/11) : son d'ambiance de l'Abattoir.
 *
 * Frank ne contient aucun son : il lit un fichier choisi sur la Shield (Download, disque de 4 To).
 * Le fichier reste sur la Shield, jamais envoyé ni publié. Coupé par défaut.
 * Seulement quand l'Abattoir est à l'écran et que Frank est au premier plan : silence pendant les films,
 * Orange TV, l'écran de veille et la veille de la Shield.
 */
object AbattoirSound666 {
    private const val PREFS = "666tv_abattoir_sound"
    /** 0 = coupé, -1 = boucle douce, sinon une fois toutes les N minutes. */
    val MODES = listOf(0, 1, 2, 5, 10, -1)
    val VOLUMES = listOf(10, 20, 30, 40, 60)
    const val FADE_MS = 1_500L

    data class Settings(val mode: Int, val uri: String?, val name: String?, val volume: Int)

    fun settings(context: Context): Settings = runCatching {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Settings(p.getInt("mode", 0), p.getString("uri", null), p.getString("name", null), p.getInt("volume", 20))
    }.getOrDefault(Settings(0, null, null, 20))

    private fun edit(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply() }

    fun nextMode(context: Context): Int {
        val cur = settings(context).mode
        val next = MODES[(MODES.indexOf(cur).coerceAtLeast(0) + 1) % MODES.size]
        edit(context) { putInt("mode", next) }
        return next
    }

    fun nextVolume(context: Context): Int {
        val cur = settings(context).volume
        val next = VOLUMES[(VOLUMES.indexOf(cur).coerceAtLeast(0) + 1) % VOLUMES.size]
        edit(context) { putInt("volume", next) }
        return next
    }

    fun setFile(context: Context, uri: String, name: String) = edit(context) { putString("uri", uri); putString("name", name) }

    fun modeLabel(mode: Int): String = when (mode) {
        0 -> "coupé"
        -1 -> "en boucle douce"
        1 -> "toutes les minutes"
        else -> "toutes les $mode min"
    }

    /**
     * Règle pure (testée) : faut-il jouer maintenant ?
     * Jamais si coupé, sans fichier, Abattoir pas à l'écran, Frank pas au premier plan (Orange TV, écran
     * de veille), écran éteint ou film en cours. En boucle : dès que le son précédent est fini. Sinon :
     * l'intervalle depuis le début du dernier son.
     */
    fun shouldPlay(
        mode: Int, hasFile: Boolean, abattoirOnScreen: Boolean, frankInFront: Boolean,
        screenOn: Boolean, inPlayer: Boolean, playing: Boolean, sinceLastStartMs: Long
    ): Boolean {
        if (mode == 0 || !hasFile || !abattoirOnScreen || !frankInFront || !screenOn || inPlayer || playing) return false
        if (sinceLastStartMs < 0) return true
        return if (mode == -1) true else sinceLastStartMs >= mode * 60_000L
    }

    /** Règle pure (testée) : volume du fondu (0..1) à l'instant t d'un son de durée d. */
    fun fadeGain(tMs: Long, durationMs: Long, volumePct: Int): Float {
        val v = volumePct.coerceIn(0, 100) / 100f
        if (durationMs <= 0) return v
        val fade = minOf(FADE_MS, durationMs / 3)
        val k = when {
            tMs < fade -> tMs.toFloat() / fade
            tMs > durationMs - fade -> (durationMs - tMs).toFloat() / fade
            else -> 1f
        }
        return (v * k.coerceIn(0f, 1f))
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
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepare()
            mp.setVolume(0f, 0f)
            mp.start()
            player = mp
            lastStartAt = System.currentTimeMillis()
            val duration = mp.duration.toLong()
            val tick = object : Runnable {
                override fun run() {
                    val p = player ?: return
                    val t = runCatching { p.currentPosition.toLong() }.getOrDefault(0L)
                    val g = fadeGain(t, duration, volumePct)
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

    /** Liste des fichiers audio de la Shield (Download, disque USB), via la médiathèque d'Android. */
    fun audioFiles(context: Context): List<Pair<String, String>> = runCatching {
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

/** À poser dans l'Abattoir : joue le son selon le réglage, tant que l'Abattoir est à l'écran. */
@Composable
fun AbattoirSound666Effect(abattoirOnScreen: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(abattoirOnScreen) {
        if (!abattoirOnScreen) { AbattoirSound666.stop(); return@LaunchedEffect }
        while (true) {
            val s = AbattoirSound666.settings(context)
            val front = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            val screenOn = runCatching {
                (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
            }.getOrDefault(true)
            val inPlayer = com.nuvio.tv.core.tools.Announce666.inPlayer()
            if (!front || !screenOn || inPlayer || s.mode == 0) AbattoirSound666.stop()
            else if (AbattoirSound666.shouldPlay(
                    s.mode, s.uri != null, true, front, screenOn, inPlayer, AbattoirSound666.isPlaying, AbattoirSound666.sinceLastStart()
                )) AbattoirSound666.play(context, s.uri!!, s.volume)
            delay(1_000L)
        }
    }
    DisposableEffect(Unit) { onDispose { AbattoirSound666.stop() } }
}
