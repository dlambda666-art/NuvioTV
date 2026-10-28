package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceFailoverRulesTest {
    private fun stream(addon: String, url: String?, infoHash: String? = null, title: String = "Film.2026.MULTi.VFF.2160p.mkv") = Stream(
        name = "🗣️ 🇫🇷",
        title = title,
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

    @Test
    fun `plugin order keeps known plugins and fills the missing ones`() {
        assertEquals(
            listOf("Lumio666", "Comet666", "StreamFusion666", "Wastream666"),
            com.nuvio.tv.core.tools.PluginOrder666.normalize(listOf("lumio666", "Frenchio666", "Comet666"))
        )
    }

    // Cas piège 28/10 : image violette (Dolby Vision sans HDR) et vieux WWE Raw
    @Test
    fun `next link never picks Dolby Vision without HDR`() {
        val dv = stream("Comet666", "https://a.test/dv", title = "Film.2026.MULTi.VFF.2160p.WEB.DV.H265.mkv")
        val hdr = stream("Comet666", "https://a.test/hdr", title = "Film.2026.MULTi.VFF.2160p.WEB.HDR10.H265.mkv")
        assertEquals(hdr, SourceFailoverRules.selectNext(listOf(dv, hdr), emptySet()))
    }

    @Test
    fun `next link never picks a Raw dated another day`() {
        com.nuvio.tv.core.player.StreamAutoPlaySelector.airDateHint666 = Triple(2026, 43, "2026-10-26")
        try {
            val old = stream("Comet666", "https://a.test/old", title = "WWE.Monday.Night.Raw.2026.10.19.FRENCH.1080p.mkv")
            val good = stream("Comet666", "https://a.test/good", title = "WWE.Monday.Night.Raw.2026.10.26.FRENCH.1080p.mkv")
            assertEquals(good, SourceFailoverRules.selectNext(listOf(old, good), emptySet(), 2026, 43))
        } finally {
            com.nuvio.tv.core.player.StreamAutoPlaySelector.airDateHint666 = null
        }
    }

    @Test
    fun `no announcement while the player is open or just closed`() {
        val a = com.nuvio.tv.core.tools.Announce666
        org.junit.Assert.assertTrue(a.isBusy(1, 0L, 10_000L))
        org.junit.Assert.assertTrue(a.isBusy(0, 10_000L, 10_000L + a.PLAYER_GRACE_MS - 1))
        org.junit.Assert.assertFalse(a.isBusy(0, 10_000L, 10_000L + a.PLAYER_GRACE_MS + 1))
        org.junit.Assert.assertFalse(a.isBusy(0, 0L, 10_000L))
    }

    @Test
    fun `a plugin removed from the repo is pruned, others untouched`() {
        fun sc(id: String, repo: String) = com.nuvio.tv.domain.model.ScraperInfo(
            id = "$repo:$id", name = id, description = "", version = "1", filename = "$id.js",
            supportedTypes = listOf("movie"), enabled = true, manifestEnabled = true, logo = null,
            contentLanguage = emptyList(), repositoryId = repo, formats = null
        )
        val all = listOf(sc("comet666", "r1"), sc("frenchio666", "r1"), sc("frenchio666", "r2"))
        val gone = com.nuvio.tv.core.tools.PluginPrune666.obsolete(all, "r1", setOf("comet666", "lumio666"))
        assertEquals(listOf("r1:frenchio666"), gone.map { it.id })
        // manifest vide (dépôt en panne) : on ne supprime rien
        assertEquals(emptyList<String>(), com.nuvio.tv.core.tools.PluginPrune666.obsolete(all, "r1", emptySet()).map { it.id })
    }
}
