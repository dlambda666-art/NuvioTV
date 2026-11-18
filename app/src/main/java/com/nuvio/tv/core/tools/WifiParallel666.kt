package com.nuvio.tv.core.tools

import android.content.Context

/**
 * FrankenStream (18/11) : 4 connexions parallèles toutes seules en WiFi quand la mémoire tampon se vide.
 *
 * - Ton réglage manuel (Réseau personnalisé) a toujours la priorité : Frank n'y touche pas.
 * - En WiFi, si un film recharge (2 rechargements ou plus après la 1re image), Frank retient que cette
 *   Shield en a besoin : les lectures suivantes en WiFi partent avec 4 connexions.
 * - Si un serveur refuse (429 trop de demandes / 503), Frank oublie et revient à 1 connexion.
 * - En câble (Shield du bas), rien ne change.
 */
object WifiParallel666 {
    const val CONNECTIONS = 4
    private const val PREFS = "666tv_wifi_parallel"

    /** Règle pure (testée) : nombre de connexions à imposer (null = ne rien changer). */
    fun decide(manualNetwork: Boolean, onWifi: Boolean, learned: Boolean): Int? =
        if (!manualNetwork && onWifi && learned) CONNECTIONS else null

    /** Règle pure (testée) : faut-il retenir que cette Shield a besoin des connexions parallèles ? */
    fun shouldLearn(manualNetwork: Boolean, onWifi: Boolean, rebuffers: Int): Boolean =
        !manualNetwork && onWifi && rebuffers >= 2

    /** Règle pure (testée) : le serveur refuse les connexions multiples → on revient à 1. */
    fun shouldForget(httpCode: Int?): Boolean = httpCode == 429 || httpCode == 503

    fun learned(context: Context): Boolean =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("learned", false) }.getOrDefault(false)

    fun learn(context: Context) {
        if (learned(context)) return
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("learned", true).apply() }
        runCatching { Announce666.log(context, "📶 WiFi : la lecture rechargeait → 4 connexions parallèles dès le prochain film", "réseau") }
    }

    fun forget(context: Context) {
        if (!learned(context)) return
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("learned", false).apply() }
        runCatching { Announce666.log(context, "📶 Le serveur refuse plusieurs connexions → retour à 1 connexion", "réseau") }
    }

    fun onWifi(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(false)
}
