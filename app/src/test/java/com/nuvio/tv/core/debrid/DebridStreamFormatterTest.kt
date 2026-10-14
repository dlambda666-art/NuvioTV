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

        assertEquals("🗣️ 🇫🇷", formatted.name)
        assertTrue(formatted.description.orEmpty(), formatted.description.orEmpty().startsWith("🎬"))
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

    @Test
    fun `666TV default formatter shows the talking head, the flag and the title only`() {
        val formatter = DebridStreamFormatter(DebridStreamTemplateEngine())
        val stream = Stream(
            name = "Frenchio666 1080p",
            title = "The.Last.of.Us.S01E01.MULTi.VFF.1080p.WEB.H264-GRP\n\uD83C\uDDEB\uD83C\uDDF7 VFF\n4.1 Go · Frenchio",
            description = null,
            url = "https://example.test/play/2",
            ytId = null,
            infoHash = null,
            fileIdx = null,
            externalUrl = null,
            behaviorHints = StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = "666|frenchio666|1080p",
                countryWhitelist = null,
                proxyHeaders = null
            ),
            addonName = "Frenchio666",
            addonLogo = null,
            quality = "1080p"
        )

        val formatted = formatter.format(stream, DebridSettings())

        assertEquals("🗣️ 🇫🇷", formatted.name)
        assertEquals("🎬 The Last Of Us · S01E01", formatted.description)
    }

    @Test
    fun `old Nuvio template saved on a Shield is replaced by the 666TV one`() {
        val legacyName = "{stream.resolution::=2160p[\"4K \"||\"\"]}{stream.resolution::=1440p[\"QHD \"||\"\"]}{stream.resolution::=1080p[\"FHD \"||\"\"]}{stream.resolution::=720p[\"HD \"||\"\"]}{stream.resolution::exists[\"\"||\"Direct \"]}{service.shortName::exists[\"{service.shortName} \"||\"Debrid \"]}Instant"
        assertEquals(DebridStreamFormatterDefaults.NAME_TEMPLATE, DebridStreamFormatterDefaults.upgradeName(legacyName))
        assertEquals(DebridStreamFormatterDefaults.NAME_TEMPLATE, DebridStreamFormatterDefaults.upgradeName(null))
        assertEquals("{addon.name}", DebridStreamFormatterDefaults.upgradeName("{addon.name}"))
        val previousEmojiName = "{stream.resolution::=2160p[\"🎞️ 4K\"||\"\"]}{stream.resolution::=1440p[\"🎞️ 1440p\"||\"\"]}{stream.resolution::=1080p[\"🎞️ 1080p\"||\"\"]}{stream.resolution::=720p[\"🎞️ 720p\"||\"\"]} {addon.name}"
        assertEquals(DebridStreamFormatterDefaults.NAME_TEMPLATE, DebridStreamFormatterDefaults.upgradeName(previousEmojiName))
    }
}
