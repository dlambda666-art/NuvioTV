package com.nuvio.tv.core.debrid

/**
 * 666TVF (07/11) : clé TorBox prêtée à Comet666 le temps d'une recherche (moteur de recherche TorBox).
 * Gardée en mémoire seulement, jamais enregistrée dans les réglages du plugin, jamais affichée.
 */
object PluginKeys666 {
    @Volatile var torbox: String = ""

    /** Réglages vus par le plugin : ceux de la Shield + la clé TorBox pour Comet666 uniquement. */
    fun withAutoKeys(pluginShortId: String, settings: Map<String, Any>): Map<String, Any> {
        val key = torbox.trim()
        if (pluginShortId != "comet666" || key.isBlank()) return settings
        return settings + ("torbox_auto" to key)
    }
}
