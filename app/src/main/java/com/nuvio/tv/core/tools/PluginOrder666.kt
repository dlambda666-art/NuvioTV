package com.nuvio.tv.core.tools

import android.content.Context

/**
 * 666TVF : ordre des cavaliers (plugins de flux), choisi dans Outils 666.
 * Sert partout : ordre de la liste des flux, lien lancé par « Lecture VF »,
 * lien suivant quand un lien échoue, et vérification VF du calendrier.
 */
object PluginOrder666 {
    private const val PREFS = "666tv_plugin_order"

    /** Ordre par défaut : Comet666 d'abord (rapide, trackers C411/Tr4ker), puis les autres. */
    val DEFAULT = listOf("Comet666", "StreamFusion666", "Lumio666", "Wastream666")

    @Volatile
    var order: List<String> = DEFAULT
        private set

    fun init(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("order", null)
        order = normalize(raw?.split("|").orEmpty())
    }

    fun save(context: Context, names: List<String>) {
        val clean = normalize(names)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("order", clean.joinToString("|")).apply()
        order = clean
    }

    /** Ordre choisi (plugins connus seulement), puis ceux qui manquent, dans l'ordre par défaut. */
    fun normalize(names: List<String>): List<String> {
        val known = names.mapNotNull { n -> DEFAULT.firstOrNull { it.equals(n.trim(), ignoreCase = true) } }.distinct()
        return known + DEFAULT.filter { it !in known }
    }

    fun labelOf(name: String): String = name.removeSuffix("666")
}
