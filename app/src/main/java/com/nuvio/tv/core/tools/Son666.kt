package com.nuvio.tv.core.tools

import android.content.Context

/**
 * 26/11 (David, Shield du bas avec la barre de son) : « pertes de son au début des films, dialogues
 * sourds 1 ou 2 s, deux-trois fois, puis ça passe ». Frank note ce que fait le son pendant les
 * 10 premières minutes de chaque film : coupures du flux son (le décodeur n'a pas suivi), changements
 * de format, sortie son recréée (la barre se recale à chaque fois), erreurs. Lu dans le Rapport.
 */
object Son666 {
    private const val PREFS = "666tv_son"
    const val WINDOW_MS = 10 * 60_000L
    private const val MAX = 40

    @Volatile private var filmStart = 0L
    @Volatile private var filmLabel: String = ""

    /** Un nouveau film / épisode démarre. */
    fun start(context: Context, label: String?) {
        filmStart = System.currentTimeMillis()
        filmLabel = label.orEmpty().take(60)
        add(context, "▶ ${filmLabel.ifBlank { "lecture" }}")
    }

    /** Règle pure (testée) : on note seulement pendant les 10 premières minutes d'un film. */
    fun inWindow(startAt: Long, now: Long): Boolean = startAt > 0 && now - startAt in 0..WINDOW_MS

    /** Règle pure (testée) : une ligne du journal (heure · temps depuis le début · quoi). */
    fun line(clock: String, sinceStartMs: Long, what: String): String =
        "$clock · +${sinceStartMs / 1000} s · $what"

    fun note(context: Context, what: String) {
        val now = System.currentTimeMillis()
        if (!inWindow(filmStart, now)) return
        add(context, what, now)
    }

    private fun add(context: Context, what: String, now: Long = System.currentTimeMillis()) {
        runCatching {
            val z = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
            val clock = "%02d/%02d %02d:%02d:%02d".format(z.dayOfMonth, z.monthValue, z.hour, z.minute, z.second)
            val l = line(clock, (now - filmStart).coerceAtLeast(0), what)
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val list = p.getString("log", "").orEmpty().lines().filter { it.isNotBlank() } + l
            p.edit().putString("log", keepLast(list, MAX).joinToString("\n")).apply()
        }
    }

    /** Règle pure (testée). */
    fun keepLast(lines: List<String>, max: Int): List<String> = if (lines.size <= max) lines else lines.takeLast(max)

    fun entries(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "").orEmpty().lines().filter { it.isNotBlank() }

    /** Nom lisible d'un encodage de sortie (AudioFormat.ENCODING_*). */
    fun encodingName(encoding: Int): String = when (encoding) {
        2 -> "PCM 16 bits"
        3 -> "PCM 8 bits"
        4 -> "PCM float"
        5 -> "AC-3 (Dolby)"
        6 -> "E-AC-3 (Dolby+)"
        7 -> "DTS"
        8 -> "DTS-HD"
        14 -> "TrueHD"
        18 -> "E-AC-3 JOC (Atmos)"
        21 -> "PCM 24 bits"
        22 -> "PCM 32 bits"
        else -> "encodage $encoding"
    }
}
