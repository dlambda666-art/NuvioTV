package com.nuvio.tv.core.wake

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * FrankenStream (16/11) : « toujours revenir à FrankenStream ».
 *
 * Un service d'accessibilité (activé une seule fois dans les réglages de la Shield) regarde quelle appli
 * passe au premier plan. Quand c'est l'accueil Nvidia / Google TV (bouton Accueil, fin d'Orange TV,
 * démarrage), il rouvre FrankenStream aussitôt. Il ne fait rien pendant Orange TV, Kodi, un film… :
 * seulement quand l'accueil d'Android apparaît.
 *
 * Porte de sortie : si l'accueil Nvidia réapparaît dans les 10 s qui suivent un retour (deux appuis sur
 * Accueil), on le laisse — pour atteindre les réglages de la Shield.
 */
object Return666 {
    const val EXTRA_RETURN = "666tv.return"
    private const val TAG = "Return666"

    /** Les accueils connus (Android TV, Google TV, Nvidia). Les autres sont trouvés par Android. */
    val KNOWN_HOMES = setOf(
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher"
    )

    /** Ce qui n'est jamais un accueil, même si ça se déclare comme tel (réglages, écran de veille…). */
    private val NEVER = setOf("com.android.systemui", "com.android.tv.settings", "android")

    /**
     * Règle pure (testée) : faut-il rouvrir FrankenStream ?
     * - l'appli au premier plan est un accueil (pas FrankenStream lui-même) ;
     * - l'écran est allumé ;
     * - on n'a pas déjà ramené FrankenStream il y a moins de 10 s (deux appuis sur Accueil = on reste).
     */
    fun shouldReturn(foreground: String, homes: Set<String>, own: String, screenOn: Boolean, sinceLastReturnMs: Long): Boolean =
        screenOn && foreground != own && foreground !in NEVER && foreground in homes &&
            (sinceLastReturnMs < 0 || sinceLastReturnMs >= 10_000L)

    /** Tous les accueils installés (sauf FrankenStream), plus ceux connus. */
    fun homes(context: Context): Set<String> = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(home, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrDefault(emptySet()).minus(context.packageName) + KNOWN_HOMES

    fun component(context: Context): String = context.packageName + "/" + Return666Service::class.java.name

    /** Le service est-il activé dans les réglages d'accessibilité de la Shield ? */
    fun enabled(context: Context): Boolean = runCatching {
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        isListed(list, context.packageName, Return666Service::class.java.name)
    }.getOrDefault(false)

    /** Règle pure (testée) : le service figure-t-il dans la liste d'Android (formes longue ou courte) ? */
    fun isListed(list: String, pkg: String, cls: String): Boolean {
        val short = cls.removePrefix(pkg)
        return list.split(':').map { it.trim() }.any { it.equals("$pkg/$cls", true) || it.equals("$pkg/$short", true) }
    }

    /** Règle pure (testée) : la nouvelle liste d'Android avec notre service ajouté (sans doublon). */
    fun withService(list: String?, component: String): String {
        val items = list.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() && !it.equals(component, true) }
        return (items + component).joinToString(":")
    }

    /** Ouvre les réglages d'accessibilité de la Shield (pour l'activer à la main). */
    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "réglages d'accessibilité introuvables", it) }
    }

    /**
     * Active le service tout seul, par le débogage réseau de cette Shield (la même autorisation que
     * pour les mises à jour entre Shield : « Autoriser » une fois sur la TV). Renvoie un texte pour l'écran.
     */
    fun enableByAdb(context: Context): String = runCatching {
        val dir = java.io.File(context.filesDir, "adb666").apply { mkdirs() }
        val priv = java.io.File(dir, "adbkey"); val pub = java.io.File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) dadb.AdbKeyPair.generate(priv, pub)
        dadb.Dadb.create("127.0.0.1", 5555, dadb.AdbKeyPair.read(priv, pub)).use { d ->
            val now = d.shell("settings get secure enabled_accessibility_services").output.trim().takeIf { it != "null" }
            val next = withService(now, component(context))
            d.shell("settings put secure enabled_accessibility_services '$next'")
            d.shell("settings put secure accessibility_enabled 1")
        }
        if (enabled(context)) "Activé ✓" else "Envoyé : vérifie dans quelques secondes"
    }.getOrElse { e ->
        Log.w(TAG, "activation par le débogage impossible", e)
        "Le débogage réseau n'est pas activé sur cette Shield (ou la demande n'a pas été acceptée) : active-le à la main"
    }

    /** Ouvre FrankenStream (depuis le service : Android l'autorise toujours à un service d'accessibilité). */
    fun bringBack(context: Context) {
        val launch = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        launch.putExtra(EXTRA_RETURN, true)
        runCatching { context.startActivity(launch) }.onFailure { Log.w(TAG, "retour refusé", it) }
    }
}

class Return666Service : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var homes: Set<String> = emptySet()
    private var homesAt = 0L
    private var lastReturnAt = -1L

    override fun onServiceConnected() {
        super.onServiceConnected()
        homes = Return666.homes(this)
        homesAt = SystemClock.elapsedRealtime()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - homesAt > 3_600_000L) { homes = Return666.homes(this); homesAt = now }
        val screenOn = runCatching { (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive }.getOrDefault(true)
        val since = if (lastReturnAt < 0) -1L else now - lastReturnAt
        if (!Return666.shouldReturn(pkg, homes, packageName, screenOn, since)) return
        lastReturnAt = now
        // petit délai : laisse l'accueil finir d'apparaître (sinon il repasse devant)
        handler.postDelayed({ Return666.bringBack(this) }, 350L)
    }

    override fun onInterrupt() = Unit
}
