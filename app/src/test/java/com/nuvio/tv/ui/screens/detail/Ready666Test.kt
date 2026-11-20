package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.core.player.StreamAutoPlaySelector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cas pièges (18/11) : « ⚡ Prêt » seulement pour un lien en cache de CET épisode. */
class Ready666Test {
    private fun s(name: String, cached: Boolean) = Stream(
        name = name, title = null, description = null, url = null, ytId = null,
        infoHash = "a".repeat(40), fileIdx = null, externalUrl = null, behaviorHints = null,
        addonName = "Comet666", addonLogo = null,
        debridCacheStatus = StreamDebridCacheStatus(
            providerId = "torbox", providerName = "TorBox",
            state = if (cached) StreamDebridCacheState.CACHED else StreamDebridCacheState.NOT_CACHED
        )
    )

    @Test fun `film en cache - pret`() =
        assertEquals(Ready666.State.READY, Ready666.decide(true, listOf(s("Matrix.1999.FRENCH.1080p", true)), null, null))

    @Test fun `rien en cache - recherche puis rien`() {
        assertEquals(Ready666.State.SEARCHING, Ready666.decide(true, listOf(s("Matrix.1999.FRENCH.1080p", false)), null, null))
        assertEquals(Ready666.State.NONE, Ready666.decide(false, emptyList(), null, null))
    }

    @Test fun `autre episode en cache - jamais pret`() =
        assertEquals(Ready666.State.SEARCHING, Ready666.decide(true, listOf(s("Dexter.S01E04.FRENCH", true)), 1, 5))

    @Test fun `vieux Raw en cache - jamais pret`() {
        StreamAutoPlaySelector.airDateHint666 = Triple(2026, 46, "2026-11-16")
        try {
            assertEquals(Ready666.State.NONE, Ready666.decide(false, listOf(s("WWE.Raw.2026.11.09.FRENCH", true)), 2026, 46))
            assertEquals(Ready666.State.READY, Ready666.decide(false, listOf(s("WWE.Raw.2026.11.16.FRENCH", true)), 2026, 46))
        } finally {
            StreamAutoPlaySelector.airDateHint666 = null
        }
    }

    @Test fun `lien direct d'un cavalier - pret (20-11)`() {
        val lumio = Stream(
            name = "Lumio666", title = "Matrix.1999.MULTi.1080p", description = null, url = "https://torbox.test/dl/1", ytId = null,
            infoHash = null, fileIdx = null, externalUrl = null,
            behaviorHints = com.nuvio.tv.domain.model.StreamBehaviorHints(notWebReady = null, bingeGroup = "666|lumio666|1080p", countryWhitelist = null, proxyHeaders = null),
            addonName = "Les 4 cavaliers de l'apocalypse", addonLogo = null
        )
        assertEquals(Ready666.State.READY, Ready666.decide(true, listOf(lumio), null, null))
        val diag = lumio.copy(url = "https://diagnostic.invalid/x")
        assertEquals(Ready666.State.SEARCHING, Ready666.decide(true, listOf(diag), null, null))
    }
}
