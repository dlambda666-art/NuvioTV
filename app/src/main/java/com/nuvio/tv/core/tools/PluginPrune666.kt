package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.ScraperInfo

/**
 * 666TVF : quand un plugin est retiré du dépôt (Frenchio666, 27/10), l'actualisation
 * l'enlève aussi de la Shield. Seuls les plugins de CE dépôt absents du manifest
 * partent ; les réglages des autres (clés, configs) ne bougent pas.
 */
object PluginPrune666 {
    /** Plugins de [repoId] qui ne sont plus dans le manifest. Manifest vide → rien (prudence). */
    fun obsolete(scrapers: List<ScraperInfo>, repoId: String, manifestIds: Set<String>): List<ScraperInfo> {
        if (manifestIds.isEmpty()) return emptyList()
        return scrapers.filter { it.repositoryId == repoId && it.id.removePrefix("$repoId:") !in manifestIds }
    }
}
