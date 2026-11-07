package com.nuvio.tv.core.debrid

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges : 4e dimension (07/11), liens hors cache TorBox mais prêts chez AllDebrid. */
class AllDebridFallback666Test {

    private fun s(hash: String, state: StreamDebridCacheState?, group: String? = "666|comet666|x") = Stream(
        name = "Comet666", title = "Release.$hash.MULTi.1080p", description = null, url = null, ytId = null,
        infoHash = hash, fileIdx = null, externalUrl = null,
        behaviorHints = StreamBehaviorHints(notWebReady = null, bingeGroup = group, countryWhitelist = null, proxyHeaders = null),
        addonName = "Comet666", addonLogo = null,
        debridCacheStatus = state?.let { StreamDebridCacheStatus("torbox", "Torbox", it) }
    )

    @Test fun `quatrieme dimension - hors cache TorBox mais pret chez AllDebrid devient lisible`() {
        val out = AllDebridFallback666.relabel(listOf(s("aaa", StreamDebridCacheState.NOT_CACHED)), true, setOf("aaa"))
        assertEquals(1, out.size)
        assertEquals(StreamDebridCacheState.CACHED, out[0].debridCacheStatus?.state)
        assertTrue(AllDebridFallback666.isFallback(out[0]))
        assertFalse(AllDebridFallback666.isGuess(out[0]))
        assertEquals("⚡ AllDebrid ✓", AllDebridFallback666.label(out[0]))
    }

    @Test fun `hors cache partout - propose en point d'interrogation, jamais en lecture auto`() {
        val out = AllDebridFallback666.relabel(listOf(s("bbb", StreamDebridCacheState.NOT_CACHED)), true, emptySet())
        assertEquals(StreamDebridCacheState.UNKNOWN, out[0].debridCacheStatus?.state)
        assertTrue(AllDebridFallback666.isGuess(out[0]))
        assertEquals("⚡ AllDebrid ?", AllDebridFallback666.label(out[0]))
    }

    @Test fun `ordre - cache TorBox devant, puis coche, puis point d'interrogation`() {
        val out = AllDebridFallback666.relabel(
            listOf(s("q1", StreamDebridCacheState.NOT_CACHED), s("ok", StreamDebridCacheState.NOT_CACHED), s("tb", StreamDebridCacheState.CACHED)),
            true, setOf("ok")
        )
        assertEquals(listOf("tb", "ok", "q1"), out.map { it.infoHash })
    }

    @Test fun `sans AllDebrid - rien ne change (protection gardee)`() {
        val input = listOf(s("ccc", StreamDebridCacheState.NOT_CACHED))
        assertEquals(input, AllDebridFallback666.relabel(input, false, setOf("ccc")))
    }

    @Test fun `liens d'autres addons non touches`() {
        val out = AllDebridFallback666.relabel(listOf(s("ddd", StreamDebridCacheState.NOT_CACHED, group = "other")), true, emptySet())
        assertEquals(StreamDebridCacheState.NOT_CACHED, out[0].debridCacheStatus?.state)
        assertNull(AllDebridFallback666.label(out[0]))
    }

    @Test fun `pas de liste interminable - au plus MAX_GUESS points d'interrogation`() {
        val many = (1..20).map { s("h$it", StreamDebridCacheState.NOT_CACHED) }
        val out = AllDebridFallback666.relabel(many, true, emptySet())
        assertEquals(AllDebridFallback666.MAX_GUESS, out.count { AllDebridFallback666.isGuess(it) })
        assertEquals(20 - AllDebridFallback666.MAX_GUESS, out.count { it.debridCacheStatus?.state == StreamDebridCacheState.NOT_CACHED })
    }

    @Test fun `hash en majuscules reconnu`() {
        val out = AllDebridFallback666.relabel(listOf(s("ABCDEF", StreamDebridCacheState.NOT_CACHED)), true, setOf("abcdef"))
        assertEquals(StreamDebridCacheState.CACHED, out[0].debridCacheStatus?.state)
    }

    @Test fun `reponse AllDebrid - seuls les torrents prets comptent`() {
        val json = JSONObject(
            """{"status":"success","data":{"magnets":[
              {"hash":"AAA","statusCode":4,"status":"Ready"},
              {"hash":"bbb","statusCode":1,"status":"Downloading"}]}}"""
        )
        assertEquals(setOf("aaa"), parseReadyHashes(json))
        assertNull(parseReadyHashes(JSONObject("""{"status":"error"}""")))
        // ancien format (objet indexé)
        assertEquals(setOf("ccc"), parseReadyHashes(JSONObject("""{"status":"success","data":{"magnets":{"1":{"hash":"ccc","statusCode":4}}}}""")))
    }
}
