package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cas piège (18/11) : un plugin qui ne répond pas n'empêche pas d'afficher les autres. */
class SearchProgress666Test {
    private fun s(addon: String, url: String, title: String? = null) = Stream(
        name = addon, title = title, description = null, url = url, ytId = null, infoHash = null, fileIdx = null,
        externalUrl = null, behaviorHints = null, addonName = addon, addonLogo = null
    )

    @Test fun `chaque source a sa ligne, la panne n'efface rien`() {
        val chips = listOf(
            SourceChipItem("Lumio666", SourceChipStatus.SUCCESS),
            SourceChipItem("Comet666", SourceChipStatus.LOADING),
            SourceChipItem("Wastream666", SourceChipStatus.SUCCESS),
            SourceChipItem("StreamFusion666", SourceChipStatus.ERROR)
        )
        val streams = listOf(
            AddonStreams("Lumio666", null, (1..8).map { s("Lumio666", "https://a.test/$it") }),
            AddonStreams("Wastream666", null, listOf(
                s("Wastream666", "https://diagnostic.invalid/w", "Aucune réponse : Matrix → adresse introuvable (site fermé, déplacé ou bloqué)")
            ))
        )
        val items = SearchProgress666.items(chips, streams)
        assertEquals(
            listOf(
                "Lumio666 ✓ 8 liens",
                "Comet666 … en cours",
                "Wastream666 ✗ adresse introuvable",
                "StreamFusion666 ✗ ne répond pas"
            ),
            items.map { it.name + " " + it.text }
        )
        assertEquals(
            listOf(SearchProgress666.State.OK, SearchProgress666.State.RUNNING, SearchProgress666.State.FAILED, SearchProgress666.State.FAILED),
            items.map { it.state }
        )
    }

    @Test fun `cause HTTP gardee`() {
        assertEquals("HTTP 403", SearchProgress666.shortCause("Aucune réponse : Matrix → HTTP 403"))
        assertEquals("ne répond pas", SearchProgress666.shortCause(null))
    }

    @Test fun `plugins groupes - chaque plugin a son etat (18-11)`() {
        val g = "Les 4 cavaliers de l'apocalypse"
        val chips = listOf(SourceChipItem(g, SourceChipStatus.SUCCESS))
        val plugins = listOf(
            com.nuvio.tv.core.streams.PluginProgress666.Plugin("Lumio666", g, com.nuvio.tv.core.streams.PluginProgress666.State.OK, links = 2),
            com.nuvio.tv.core.streams.PluginProgress666.Plugin("Comet666", g, com.nuvio.tv.core.streams.PluginProgress666.State.FAILED, cause = "Thepiratebay : 1 trouvé · 1 pas de VF"),
            com.nuvio.tv.core.streams.PluginProgress666.Plugin("Wastream666", g, com.nuvio.tv.core.streams.PluginProgress666.State.RUNNING),
            com.nuvio.tv.core.streams.PluginProgress666.Plugin("StreamFusion666", g, com.nuvio.tv.core.streams.PluginProgress666.State.ASLEEP)
        )
        val items = SearchProgress666.items(chips, emptyList(), plugins)
        assertEquals(
            listOf("$g :", "Lumio666 ✓ 2", "Comet666 ✗ pas de VF", "Wastream666 …", "StreamFusion666 💤"),
            items.map { if (it.text.isEmpty()) it.name else it.name + " " + it.text }
        )
    }
}
