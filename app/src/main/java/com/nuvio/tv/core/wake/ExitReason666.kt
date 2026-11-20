package com.nuvio.tv.core.wake

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * FrankenStream (20/11) : au démarrage, Frank note dans le journal des annonces pourquoi il s'est fermé
 * la dernière fois, quand ce n'était pas normal (manque de mémoire, plantage, blocage). Sert à comprendre
 * les « film qui démarre puis YouTube revient » (surtout sur la Shield du haut).
 */
object ExitReason666 {
    private const val PREFS = "666tv_exit"

    /** Règle pure (testée) : texte du journal pour une raison de fermeture (null = fermeture normale). */
    fun describe(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "fermé par Android : manque de mémoire"
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> "plantage"
        ApplicationExitInfo.REASON_ANR -> "bloqué (ne répondait plus)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "fermé par Android : trop de ressources"
        ApplicationExitInfo.REASON_SIGNALED -> "fermé par Android (signal)"
        else -> null
    }

    fun logLastExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (info.timestamp <= prefs.getLong("seen", 0L)) return
        prefs.edit().putLong("seen", info.timestamp).apply()
        val why = describe(info.reason) ?: return
        val t = java.time.Instant.ofEpochMilli(info.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val inPlayer = Rescue666.lastBeat(context).let { it > 0 && info.timestamp - it in 0..Rescue666.PLAYER_FRESH_MS }
        com.nuvio.tv.core.tools.Announce666.log(
            context,
            "🧟 Frank s'est fermé à %02d:%02d : %s%s".format(t.hour, t.minute, why, if (inPlayer) " (pendant un film)" else ""),
            Build.MODEL ?: "Shield"
        )
    }
}
