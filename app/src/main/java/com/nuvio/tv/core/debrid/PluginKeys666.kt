package com.nuvio.tv.core.debrid

/**
 * 666TVF (07/11) : clé TorBox prêtée à Comet666 le temps d'une recherche (moteur de recherche TorBox).
 * Gardée en mémoire seulement, jamais enregistrée dans les réglages du plugin, jamais affichée.
 */
object PluginKeys666 {
    @Volatile var torbox: String = ""
    /** 666TVF (07/11) : clé AllDebrid prêtée à Comet666 : ton compte AllDebrid devient une source. */
    @Volatile var alldebrid: String = ""

    /**
     * 26/11 (NXT S20E47 : 77 réponses YggReborn, toutes « autre épisode ») : Comet666 prenait la date de
     * diffusion chez TMDB, dont la numérotation de NXT (PLE comptés) n'est pas celle de Frank → mauvaise date.
     * Frank lui prête la date qu'il connaît, par « saison:épisode » demandé (aaaa-mm-jj).
     */
    private val airDates = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun putAirDate(season: Int?, episode: Int?, date: String?) {
        if (season == null || episode == null) return
        val d = date?.take(10)?.takeIf { Regex("""\d{4}-\d\d-\d\d""").matches(it) } ?: return
        if (airDates.size > 60) airDates.clear()
        airDates["$season:$episode"] = d
    }

    /** Règle pure (testée) : le JSON prêté à Comet666. */
    fun airDatesJson(): String = org.json.JSONObject(airDates as Map<*, *>).toString()

    /** Réglages vus par le plugin : ceux de la Shield + la clé TorBox pour Comet666 uniquement. */
    fun withAutoKeys(pluginShortId: String, settings: Map<String, Any>): Map<String, Any> {
        if (!pluginShortId.endsWith("666")) return settings
        var out = settings
        // 666TVF (09/11) : groupes de release appris sur la Shield (avant : Frank). Pas de clé là-dedans.
        val (fr, other) = runCatching { com.nuvio.tv.core.tools.Groups666.lists() }.getOrDefault(emptyList<String>() to emptyList())
        if (fr.isNotEmpty()) out = out + ("groups_fr_auto" to fr.joinToString(","))
        if (other.isNotEmpty()) out = out + ("groups_other_auto" to other.joinToString(","))
        if (pluginShortId != "comet666") return out
        torbox.trim().takeIf { it.isNotBlank() }?.let { out = out + ("torbox_auto" to it) }
        alldebrid.trim().takeIf { it.isNotBlank() }?.let { out = out + ("alldebrid_auto" to it) }
        if (airDates.isNotEmpty()) out = out + ("air_dates_auto" to airDatesJson())
        return out
    }
}
