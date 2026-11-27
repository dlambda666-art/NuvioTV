package com.nuvio.tv.ui.screens.home

import android.content.Context
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
    // 25/11 (David) : Machine Head part quand les portes sont ouvertes (la bobine est déjà éteinte) : montée douce 1,5 s
    const val FADE_IN_MS = 1_500L
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
    fun shouldPlayAtDoors(mode: Int, hasFile: Boolean, frankInFront: Boolean, inPlayer: Boolean, playing: Boolean, sinceLastDoorsMs: Long = -1L): Boolean =
        mode != 0 && hasFile && frankInFront && !inPlayer && !playing && (sinceLastDoorsMs < 0 || sinceLastDoorsMs >= DOORS_GAP_MS)

    /**
     * 23/11 : le « 3 fois » venait de la boucle (corrigé) ; le son joue à chaque ouverture des portes,
     * 15 s d'écart minimum seulement pour ne pas le doubler si deux cinémas s'enchaînent.
     */
    const val DOORS_GAP_MS = 15_000L   // 23/11 (réparation 3) : à CHAQUE ouverture ; 15 s évite seulement un doublon
    @Volatile private var lastDoorsAt = 0L

    /** Règle pure (testée) : volume (0..1) à l'instant t : montée très courte, fondu de fin de 3 s (fichier plus long que 20 s). */
    fun fadeGain(tMs: Long, volumePct: Int, totalMs: Long = PLAY_MS): Float {
        val v = volumePct.coerceIn(0, 100) / 100f
        val k = when {
            tMs < 0 || tMs >= totalMs -> 0f
            tMs < fadeIn(totalMs) -> (tMs.toFloat() / fadeIn(totalMs)).let { u -> u * u * (3f - 2f * u) }   // montée en douceur
            tMs > totalMs - fadeOut(totalMs) -> (totalMs - tMs).toFloat() / fadeOut(totalMs)
            else -> 1f
        }
        return v * k.coerceIn(0f, 1f)
    }

    private fun fadeIn(totalMs: Long): Long = minOf(FADE_IN_MS, totalMs / 3).coerceAtLeast(1L)

    private fun fadeOut(totalMs: Long): Long = minOf(FADE_OUT_MS, totalMs / 4).coerceAtLeast(1L)

    /** Appelé par le cinéma quand les portes commencent à s'ouvrir. */
    fun onDoorsOpening(context: Context) {
        val s = settings(context)
        val inPlayer = com.nuvio.tv.core.tools.Announce666.inPlayer()
        val front = com.nuvio.tv.core.wake.WakeLaunch666.resumed
        val now = System.currentTimeMillis()
        val since = if (lastDoorsAt == 0L) -1L else now - lastDoorsAt
        // pendant le cinéma Frank est déjà devant (WakeLaunch666.resumed peut arriver un poil après)
        if (shouldPlayAtDoors(s.mode, s.uri != null, front || com.nuvio.tv.ui.components.Splash666.showing, inPlayer, isPlaying, since)) {
            lastDoorsAt = now
            play(context, s.uri!!, s.volume)
        }
    }

    // ---------- Lecture ----------
    // 25/11 (David, 2.0.10 : « la bobine coupe puis reprend en fondu avec Machine Head », surtout en bas avec
    // la barre de son) : MediaPlayer envoyait Machine Head (fichier long, mp3) par la sortie « offload » de la
    // Shield → la sortie HDMI se réinitialise → TOUT le son coupe une fraction de seconde (bobine comprise).
    // Maintenant Machine Head passe par ExoPlayer (jamais d'offload, même mélangeur que la bobine) : plus
    // de coupure, le fondu croisé s'entend en entier. Toujours sur le fil principal.
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var player: androidx.media3.exoplayer.ExoPlayer? = null
    @Volatile private var lastStartAt = -1L
    val isPlaying: Boolean get() = player != null

    fun sinceLastStart(now: Long = System.currentTimeMillis()): Long = if (lastStartAt < 0) -1L else now - lastStartAt

    // 23/11 (réparation) : le fichier est préparé pendant le cinéma → il part pile à l'ouverture
    @Volatile private var ready: androidx.media3.exoplayer.ExoPlayer? = null
    @Volatile private var readyUri: String? = null

    private fun build(context: Context, uri: String): androidx.media3.exoplayer.ExoPlayer =
        androidx.media3.exoplayer.ExoPlayer.Builder(context.applicationContext).build().apply {
            // pas de prise du « focus » audio : ne coupe ni ne baisse rien d'autre
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false
            )
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = false
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(uri)))
            prepare()
        }

    fun preload(context: Context) {
        val s = settings(context)
        val uri = s.uri ?: return
        if (s.mode == 0 || (ready != null && readyUri == uri)) return
        val app = context.applicationContext
        handler.post {
            runCatching {
                ready?.let { old -> runCatching { old.release() } }
                ready = build(app, uri); readyUri = uri
            }
        }
    }

    /** Règle pure (testée) : durée jouée : le fichier une seule fois, coupé à 20 s s'il est plus long. */
    fun playLength(fileMs: Long): Long = if (fileMs <= 0) PLAY_MS else minOf(fileMs, PLAY_MS)

    /** Appelé par le cinéma quand les portes commencent à s'ouvrir. */
    fun play(context: Context, uri: String, volumePct: Int) {
        val app = context.applicationContext
        handler.post {
            stop()
            runCatching {
                // 23/11 (réparation, David : « tjrs 3x le son ») : plus de boucle, le fichier une seule fois
                val p = (if (readyUri == uri) ready else null) ?: build(app, uri)
                ready = null; readyUri = null
                p.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_ENDED && player === p) stop()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (player === p) stop()
                    }
                })
                p.volume = 0f
                p.playWhenReady = true
                player = p
                // 27/11 : la montée (1,5 s) commence quand le son sort vraiment (fichier prêt), pas avant :
                // sinon, si le fichier se prépare encore, le début de la montée est perdu et on entend un retard
                var start = -1L
                lastStartAt = System.currentTimeMillis()
                val tick = object : Runnable {
                    override fun run() {
                        val cur = player ?: return
                        if (cur !== p) return
                        if (start < 0) {
                            if (p.playbackState != androidx.media3.common.Player.STATE_READY) {
                                if (com.nuvio.tv.core.tools.Announce666.inPlayer()) { stop(); return }
                                handler.postDelayed(this, 15L); return
                            }
                            start = android.os.SystemClock.uptimeMillis()
                        }
                        val t = android.os.SystemClock.uptimeMillis() - start
                        val total = playLength(p.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: -1L)
                        if (t >= total || com.nuvio.tv.core.tools.Announce666.inPlayer() || !com.nuvio.tv.core.wake.WakeLaunch666.resumed) {
                            stop(); return
                        }
                        p.volume = fadeGain(t, volumePct, total)
                        handler.postDelayed(this, 40L)
                    }
                }
                handler.post(tick)
            }.onFailure { stop() }
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    /**
     * 26/11 (David : pertes de son au début des films, en bas avec la barre de son) : un film démarre →
     * plus aucun lecteur de l'Abattoir ouvert (ni Machine Head en fin de fondu, ni celui préparé
     * d'avance pendant le cinéma) : une seule sortie son, celle du film.
     */
    fun onFilmStart() {
        // 27/11 (David : Machine Head de nouveau en retard aux portes) : on ne jette plus le lecteur préparé
        // d'avance (il ne sort aucun son tant qu'il ne joue pas) ; on arrête seulement Machine Head s'il joue.
        handler.post { if (player != null) stop() }
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

