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
        // 23/11 : où Frank était bloqué / a planté (les lignes du code, jamais de lien ni de clé) → Rapport pour Claude
        val where = when (info.reason) {
            ApplicationExitInfo.REASON_ANR -> runCatching {
                info.traceInputStream?.bufferedReader()?.use { mainThreadFrames(it.readText()) }
            }.getOrNull()
            else -> prefs.getString("crash_pending", null)
        }
        prefs.edit().remove("crash_pending").apply()
        if (!where.isNullOrBlank()) {
            val list = prefs.getString("traces", "").orEmpty().split("\n\n").filter { it.isNotBlank() }.takeLast(4)
            val entry = "%02d/%02d %02d:%02d · %s\n%s".format(
                java.time.Instant.ofEpochMilli(info.timestamp).atZone(java.time.ZoneId.systemDefault()).dayOfMonth,
                java.time.Instant.ofEpochMilli(info.timestamp).atZone(java.time.ZoneId.systemDefault()).monthValue,
                t.hour, t.minute, why, where
            )
            prefs.edit().putString("traces", (list + entry).joinToString("\n\n")).apply()
        }
    }

    /** Règle pure (testée) : les 14 premières lignes « at … » du fil principal d'un rapport de blocage Android. */
    fun mainThreadFrames(dump: String, max: Int = 14): String? {
        val start = dump.indexOf("\"main\"")
        if (start < 0) return null
        return dump.substring(start).lineSequence().drop(1)
            .map { it.trim() }
            .takeWhile { it.isNotEmpty() }
            .filter { it.startsWith("at ") || it.startsWith("- ") }
            .take(max)
            .joinToString("\n")
            .ifBlank { null }
    }

    /** Règle pure (testée) : résumé d'un plantage (type + 12 premières lignes de la pile). */
    fun crashFrames(e: Throwable, max: Int = 12): String =
        (listOf("${e.javaClass.name}: ${e.message.orEmpty().take(120)}") +
            e.stackTrace.take(max).map { "at $it" } +
            (e.cause?.let { c -> listOf("cause: ${c.javaClass.name}: ${c.message.orEmpty().take(120)}") + c.stackTrace.take(6).map { "at $it" } } ?: emptyList())
        ).joinToString("\n")

    /** 23/11 : garde le résumé du plantage pour le journal du prochain démarrage (puis laisse Android faire). */
    fun installCrashCatcher(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("crash_pending", "fil « ${thread.name} »\n" + crashFrames(e)).commit()
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Les derniers blocages / plantages (pour le Rapport pour Claude). */
    fun traces(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("traces", "").orEmpty()
}
