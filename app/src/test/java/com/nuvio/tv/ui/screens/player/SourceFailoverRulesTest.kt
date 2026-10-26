package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceFailoverRulesTest {
    private fun stream(addon: String, url: String?, infoHash: String? = null) = Stream(
        name = "🗣️ 🇫🇷",
        title = "Film.2026.MULTi.VFF.2160p.mkv",
        description = null,
        url = url,
        ytId = null,
        infoHash = infoHash,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = addon,
        addonLogo = null
    )

    @Test
    fun `next ready link is the first untried one in list order`() {
        val sf = stream("StreamFusion666", "https://a.test/1")
        val fr = stream("Frenchio666", "https://b.test/2")
        val lu = stream("Lumio666", "https://c.test/3")
        val next = SourceFailoverRules.selectNext(listOf(sf, fr, lu), tried = setOf("https://a.test/1"))
        assertEquals("Frenchio666", next?.addonName)
    }

    @Test
    fun `torrents without a ready link are never picked`() {
        val torrent = stream("Comet666", null, infoHash = "abc")
        assertNull(SourceFailoverRules.selectNext(listOf(torrent), tried = emptySet()))
    }

    @Test
    fun `nothing left when every link was tried`() {
        val sf = stream("StreamFusion666", "https://a.test/1")
        assertNull(SourceFailoverRules.selectNext(listOf(sf), tried = setOf("https://a.test/1")))
    }

    // Cas piège (Below S1E2, 26/10) : les liens Frenchio refusés en HTTP 403 →
    // les autres liens Frenchio sont sautés, on passe directement à Lumio.
    @Test
    fun `a refused source is skipped entirely`() {
        val fr1 = stream("Frenchio666", "https://b.test/1")
        val fr2 = stream("Frenchio666", "https://b.test/2")
        val lu = stream("Lumio666", "https://c.test/3")
        val tried = setOf("https://b.test/1", SourceFailoverRules.sourceKey("Frenchio666")!!)
        assertEquals("Lumio666", SourceFailoverRules.selectNext(listOf(fr1, fr2, lu), tried)?.addonName)
    }

    @Test
    fun `server refusals are recognised`() {
        org.junit.Assert.assertTrue(SourceFailoverRules.isServerRefusal("HTTP 403 Forbidden (ERROR_CODE_IO_BAD_HTTP_STATUS)"))
        org.junit.Assert.assertFalse(SourceFailoverRules.isServerRefusal(NO_FRENCH_AUDIO_666))
        org.junit.Assert.assertFalse(SourceFailoverRules.isServerRefusal("Le lien ne démarre pas"))
    }
}
