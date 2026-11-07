package com.nuvio.tv.core.tools

import com.nuvio.tv.core.debrid.AllDebridFallback666
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import com.nuvio.tv.ui.screens.player.releaseNameForVerdict666
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (07/11) : AHS S13E01 « MULTi (VF ?) » sans piste française ; hibernation des cavaliers. */
class Hibernate666NoFrenchTest {
    private val mixio = "a".repeat(40)
    private val good = "b".repeat(40)

    private fun s(hash: String, state: StreamDebridCacheState?) = Stream(
        name = "Comet666", title = "Rel", description = null, url = null, ytId = null, infoHash = hash, fileIdx = null,
        externalUrl = null, behaviorHints = StreamBehaviorHints(null, "666|comet666|x", null, null),
        addonName = "Comet666", addonLogo = null,
        debridCacheStatus = state?.let { StreamDebridCacheStatus("torbox", "Torbox", it) }
    )

    @Test fun `AHS - Frank recoit le vrai nom de release, pas la ligne drapeau`() {
        val rel = "American.Horror.Story.S13E01-06..MULTi.1080p.WEB-DL.H264-Mixio"
        assertEquals(rel, releaseNameForVerdict666(rel, "🗣️ 🇫🇷\n🐎 Comet666", "Comet666 1080p"))
        // pas de fichier connu : on cherche une ligne qui ressemble à une release
        assertEquals(rel, releaseNameForVerdict666(null, "🗣️ 🇫🇷\n$rel", "Comet666"))
        assertEquals("", releaseNameForVerdict666(null, "🗣️ 🇫🇷\n🐎 Comet666", "Comet666 1080p"))
    }

    @Test fun `fichier menteur garde 180 jours puis oublie`() {
        val today = 1000L
        val kept = NoFrench666.pruned(listOf("$mixio|1000", "$good|${today - 200}", "$mixio|990"), today)
        assertEquals(listOf("$mixio|1000"), kept)
    }

    @Test fun `hibernation - le 1er cavalier suffit s'il a un lien en cache`() {
        assertFalse(Hibernate666.shouldWake(listOf(s(good, StreamDebridCacheState.CACHED)), emptySet(), emptySet()))
    }

    @Test fun `hibernation - reveil si rien de lisible`() {
        assertTrue(Hibernate666.shouldWake(emptyList(), emptySet(), emptySet()))
        assertTrue(Hibernate666.shouldWake(listOf(s(good, StreamDebridCacheState.NOT_CACHED)), emptySet(), emptySet()))
    }

    @Test fun `hibernation - pret chez AllDebrid compte comme lisible`() {
        assertFalse(Hibernate666.shouldWake(listOf(s(good, StreamDebridCacheState.NOT_CACHED)), setOf(good), emptySet()))
    }

    @Test fun `AHS - un seul lien en cache mais menteur, on reveille les autres`() {
        assertTrue(Hibernate666.shouldWake(listOf(s(mixio, StreamDebridCacheState.CACHED)), emptySet(), setOf(mixio)))
    }

    @Test fun `lien AllDebrid point d'interrogation ne suffit pas`() {
        val guess = AllDebridFallback666.relabel(listOf(s(good, StreamDebridCacheState.NOT_CACHED)), true, emptySet())
        assertTrue(Hibernate666.shouldWake(guess, emptySet(), emptySet()))
    }
}
