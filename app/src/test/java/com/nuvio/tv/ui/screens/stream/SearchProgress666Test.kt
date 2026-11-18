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
                "Wacustom666 ✗ adresse introuvable",
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
}
