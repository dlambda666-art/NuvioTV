package com.nuvio.tv.core.wake

import android.content.Context

/**
 * FrankenStream (20/11) : deux retours sur Frank, faits par le service « Toujours revenir »
 * (Android laisse toujours passer un service d'accessibilité, pas une appli qui s'ouvre toute seule).
 *
 * 1. Après une mise à jour : le service redémarre avec la nouvelle version et rouvre Frank
 *    (sauf devant Orange TV). Avant : Frank essayait de s'ouvrir lui-même → souvent refusé par Android.
 * 2. Pendant un film : si Frank disparaît d'un coup (fermé par Android, manque de mémoire surtout sur la
 *    Shield du haut) et qu'une autre appli revient devant (YouTube regardé deux jours avant), le service
 *    rouvre Frank au lieu de laisser YouTube.
 */
object Rescue666 {
    private const val PREFS = "666tv_rescue"
    const val UPDATE_WINDOW_MS = 5 * 60_000L
    const val PLAYER_FRESH_MS = 15_000L

    // ---------- règles pures (testées) ----------

    /** Après une mise à jour : rouvrir Frank si la demande est récente et que l'appli devant n'est pas Orange TV. */
    fun shouldReturnAfterUpdate(pendingAt: Long, now: Long, foreground: String?, own: String, foregroundIsOrange: Boolean): Boolean =
        pendingAt > 0 && now - pendingAt in 0..UPDATE_WINDOW_MS && foreground != own && !foregroundIsOrange

    /**
     * Pendant un film : Frank lisait il y a moins de [PLAYER_FRESH_MS] (battement du lecteur), il n'a pas été
     * fermé normalement, et une autre appli (pas Orange TV, pas les réglages) est maintenant devant → on revient.
     */
    fun shouldRescuePlayback(lastBeatAt: Long, now: Long, foreground: String?, own: String, foregroundIsOrange: Boolean, isSettings: Boolean): Boolean =
        lastBeatAt > 0 && now - lastBeatAt in 0..PLAYER_FRESH_MS && foreground != null && foreground != own &&
            !foregroundIsOrange && !isSettings

    // ---------- mémoire (survit à la fermeture de Frank) ----------

    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markUpdatePending(context: Context, now: Long = System.currentTimeMillis()) =
        runCatching { p(context).edit().putLong("update_at", now).commit() }

    fun updatePendingAt(context: Context): Long = runCatching { p(context).getLong("update_at", 0L) }.getOrDefault(0L)

    /** Battement du lecteur (toutes les 5 s pendant un film) ; 0 = film fermé normalement. */
    fun beat(context: Context, now: Long = System.currentTimeMillis()) =
        runCatching { p(context).edit().putLong("beat", now).apply() }

    fun stopBeat(context: Context) = runCatching { p(context).edit().putLong("beat", 0L).apply() }

    fun lastBeat(context: Context): Long = runCatching { p(context).getLong("beat", 0L) }.getOrDefault(0L)

    /** Frank est revenu devant : plus rien à rattraper. */
    fun onFrankInFront(context: Context) =
        runCatching { p(context).edit().putLong("update_at", 0L).apply() }

    fun isOrange(context: Context, pkg: String?): Boolean {
        if (pkg == null) return false
        if (pkg.contains("orange", ignoreCase = true)) return true
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().contains("orange", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun isSettings(pkg: String?): Boolean =
        pkg == null || pkg == "com.android.tv.settings" || pkg == "com.android.settings" || pkg == "com.android.systemui" ||
            pkg.contains("packageinstaller") || pkg == "android"
}
