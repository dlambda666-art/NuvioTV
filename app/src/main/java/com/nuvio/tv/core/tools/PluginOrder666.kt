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

    /** Anciens / nouveaux noms affichés du plugin wastream666 (id interne inchangé : le lien collé reste). */
    private val WASCUSTOM_NAMES = setOf("wastream666", "wacustom666", "wascustom666")
    const val WASCUSTOM = "Wastream666"   // 20/11 : Wastream est de retour, son vrai nom revient partout

    /** 18/11 : nom de classement (ordre des cavaliers) : Wascustom666 / Wacustom666 → Wastream666. */
    fun canonical(name: String): String =
        if (name.trim().lowercase() in WASCUSTOM_NAMES) "Wastream666" else name.trim()

    /** 18/11 : nom affiché d'un plugin, d'après son id (wastream666 → « Wascustom666 », partout). */
    fun displayName(id: String, name: String): String =
        if (id.substringAfterLast(':').equals("wastream666", true) || name.trim().lowercase() in WASCUSTOM_NAMES) WASCUSTOM else name

    /** 18/11 : Wastream666 s'affiche « Wascustom » (id interne inchangé : le lien collé reste en place). */
    fun labelOf(name: String): String =
        if (name.trim().lowercase() in WASCUSTOM_NAMES) "Wastream" else name.removeSuffix("666")
}
