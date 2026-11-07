package com.nuvio.tv.core.debrid

/**
 * 666TVF (07/11) : clé TorBox prêtée à Comet666 le temps d'une recherche (moteur de recherche TorBox).
 * Gardée en mémoire seulement, jamais enregistrée dans les réglages du plugin, jamais affichée.
 */
object PluginKeys666 {
    @Volatile var torbox: String = ""
    /** 666TVF (07/11) : clé AllDebrid prêtée à Comet666 : ton compte AllDebrid devient une source. */
    @Volatile var alldebrid: String = ""

    /** Réglages vus par le plugin : ceux de la Shield + la clé TorBox pour Comet666 uniquement. */
    fun withAutoKeys(pluginShortId: String, settings: Map<String, Any>): Map<String, Any> {
        if (pluginShortId != "comet666") return settings
        var out = settings
        torbox.trim().takeIf { it.isNotBlank() }?.let { out = out + ("torbox_auto" to it) }
        alldebrid.trim().takeIf { it.isNotBlank() }?.let { out = out + ("alldebrid_auto" to it) }
        return out
    }
}
