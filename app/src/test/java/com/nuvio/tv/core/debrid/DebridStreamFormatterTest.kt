package com.nuvio.tv.core.debrid

import com.nuvio.tv.domain.model.DebridSettings
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebridStreamFormatterTest {
    @Test
    fun `formats cached local debrid torrents with tv default formatter`() {
        val formatter = DebridStreamFormatter(DebridStreamTemplateEngine())
        val stream = Stream(
            name = "Original torrent",
            title = "movie",
            description = null,
            url = null,
            ytId = null,
            infoHash = "abc123",
            fileIdx = null,
            externalUrl = null,
            behaviorHints = StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = null,
                countryWhitelist = null,
                proxyHeaders = null,
                filename = "Movie.2026.1080p.mkv"
            ),
            addonName = "Addon",
            addonLogo = null,
            debridCacheStatus = StreamDebridCacheStatus(
                providerId = DebridProviders.TORBOX_ID,
                providerName = "Torbox",
                state = StreamDebridCacheState.CACHED,
                cachedName = "Movie.2026.1080p.mkv",
                cachedSize = 1_500_000_000L
            )
        )

        val formatted = formatter.format(stream, DebridSettings())

        assertEquals("FHD TB Instant", formatted.name)
        assertTrue(formatted.description.orEmpty().contains("Ready (TB)"))
        assertTrue(formatted.description.orEmpty().contains("Movie.2026.1080p.mkv"))
    }

    @Test
    fun `formats 666TV plugin direct links from the release name`() {
        val formatter = DebridStreamFormatter(DebridStreamTemplateEngine())
        val stream = Stream(
            name = "Lumio666 2160p",
            title = "Dune.Part.Two.2024.MULTi.TRUEFRENCH.2160p.WEB.H265-GRP.mkv\n\uD83C\uDDEB\uD83C\uDDF7 VFF · DV\n18.2 Go · Lumio",
            description = null,
            url = "https://example.test/play/1",
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = "666|lumio666|2160p",
                countryWhitelist = null,
                proxyHeaders = null
            ),
            addonName = "Lumio666",
            addonLogo = null,
            quality = "2160p"
        )
        val settings = DebridSettings().copy(
            streamNameTemplate = "{addon.name} {stream.resolution}",
            streamDescriptionTemplate = "{stream.title} ({stream.year}) {stream.languageEmojis::join(' ')}"
        )

        val formatted = formatter.format(stream, settings)

        assertEquals("Lumio666 4K", formatted.name?.replace("2160p", "4K"))
        assertEquals("Dune Part Two (2024) \uD83C\uDDEB\uD83C\uDDF7 VFF", formatted.description)
    }
}
