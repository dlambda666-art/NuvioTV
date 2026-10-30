package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import com.nuvio.tv.ui.components.HeldOk666
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 666TVF : cas pièges de l'enregistrement auto, du « Vu » et de l'appui long (30/10). */
class AutoRecord666Test {
    private val GB = 1024L * 1024 * 1024

    private fun s(name: String, size: Long?, addon: String = "Comet666", url: String? = "https://x/f.mkv", cache: StreamDebridCacheState? = null) = Stream(
        name = name, title = null, description = null, url = url, ytId = null, infoHash = null, fileIdx = null,
        externalUrl = null,
        behaviorHints = StreamBehaviorHints(notWebReady = null, bingeGroup = null, countryWhitelist = null, proxyHeaders = null, videoSize = size, filename = name),
        addonName = addon, addonLogo = null,
        debridCacheStatus = cache?.let { StreamDebridCacheStatus(providerId = "torbox", providerName = "TorBox", state = it) }
    )

    @Test fun `auto - 1080p VF qui tient, ni 4K ni 720p ni trop gros`() {
        val list = listOf(
            s("Monstre.S04E05.MULTi.2160p.WEB 🇫🇷", 9 * GB),
            s("Monstre.S04E05.MULTi.720p.WEB 🇫🇷", 1 * GB),
            s("Monstre.S04E05.MULTi.1080p.BluRay 🇫🇷", 6 * GB),
            s("Monstre.S04E05.MULTi.1080p.WEB.x265 🇫🇷", 2 * GB)
        )
        val picked = AutoRecord666.pickAll(list, 4, 5, Record666.FAT_MAX_BYTES)
        assertEquals(listOf("Monstre.S04E05.MULTi.1080p.WEB.x265 🇫🇷"), picked.map { it.name })
    }

    @Test fun `auto - jamais un autre episode ni un VF incertain ni une taille inconnue`() {
        val list = listOf(
            s("Monstre.S04E04.MULTi.1080p 🇫🇷", 2 * GB),
            s("Monstre.S04E05.1080p VF ?", 2 * GB),
            s("Monstre.S04E05.MULTi.1080p 🇫🇷", null),
            s("Monstre.S04E05.MULTi.1080p.WEB 🇫🇷", 2 * GB, cache = StreamDebridCacheState.NOT_CACHED)
        )
        assertTrue(AutoRecord666.pickAll(list, 4, 5, 20 * GB).isEmpty())
    }

    @Test fun `auto - l'ordre des cavaliers est garde, l'episode ecrit passe devant`() {
        val list = listOf(
            s("Monstre MULTi 1080p (pack saison)", 2 * GB, addon = "Lumio666"),
            s("Monstre.S04E05.MULTi.1080p 🇫🇷", 3 * GB, addon = "Comet666"),
            s("Monstre.S04E05.MULTi.1080p.x265 🇫🇷", 2 * GB, addon = "StreamFusion666")
        )
        assertEquals(listOf("Comet666", "StreamFusion666", "Lumio666"), AutoRecord666.pickAll(list, 4, 5, 20 * GB).map { it.addonName })
    }

    @Test fun `vu - efface 3 jours apres, sauf garde, jamais avant d'etre vu`() {
        val day = 24L * 3600 * 1000
        val base = Record666.Rec(
            id = "tt1", title = "t", contentName = "t", contentId = "tt1", contentType = "movie", videoId = null,
            season = null, episode = null, episodeTitle = null, year = null, quality = "", url = "https://x",
            headers = emptyMap(), path = "/x.mkv", posterUrl = null, backdropUrl = null, logoUrl = null,
            total = 1, status = Record666.Status.DONE, error = null, addedAt = 0, usb = true
        )
        val now = 10 * day
        assertFalse(Record666.shouldAutoDelete(base, now))
        assertFalse(Record666.shouldAutoDelete(base.copy(watchedAt = now - 2 * day), now))
        assertTrue(Record666.shouldAutoDelete(base.copy(watchedAt = now - 3 * day), now))
        assertFalse(Record666.shouldAutoDelete(base.copy(watchedAt = now - 9 * day, keep = true), now))
        assertFalse(Record666.shouldAutoDelete(base.copy(watchedAt = now - 9 * day, status = Record666.Status.RUNNING), now))
    }

    @Test fun `appui long - la fenetre ignore le OK encore enfonce puis son relachement`() {
        val ok = android.view.KeyEvent.KEYCODE_DPAD_CENTER
        val st = BooleanArray(1)
        assertTrue(HeldOk666.swallow(st, ok, isDown = true, repeatCount = 5, sinceOpenMs = 50))    // toujours enfoncé
        assertTrue(HeldOk666.swallow(st, ok, isDown = true, repeatCount = 9, sinceOpenMs = 900))   // très long appui
        assertTrue(HeldOk666.swallow(st, ok, isDown = false, repeatCount = 0, sinceOpenMs = 1000)) // relâché : avalé
        assertFalse(HeldOk666.swallow(st, ok, isDown = true, repeatCount = 0, sinceOpenMs = 1500)) // nouvel appui : OK
        assertFalse(HeldOk666.swallow(st, ok, isDown = false, repeatCount = 0, sinceOpenMs = 1600))
    }

    @Test fun `appui long - un nouvel appui franc est accepte meme sans relachement vu`() {
        val st = BooleanArray(1)
        assertFalse(HeldOk666.swallow(st, android.view.KeyEvent.KEYCODE_ENTER, isDown = true, repeatCount = 0, sinceOpenMs = 800))
        // les flèches ne sont jamais avalées
        assertFalse(HeldOk666.swallow(BooleanArray(1), android.view.KeyEvent.KEYCODE_DPAD_DOWN, true, 3, 10))
    }
}
