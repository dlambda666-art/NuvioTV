package com.nuvio.tv.core.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le cerveau VF de Frank sur la Shield (09/11) : mêmes règles que Frank. */
class VfCheck666Test {
    // Réponses réelles de la recherche French-Stream (sonde du 09/11)
    private val cannibal = """<div class='search-item' onclick="location.href='/13379-cannibal-holocaust-film-streaming-complet-vf.html'"><div class='search-poster'><img src='https://image.tmdb.org/t/p/w400/89vXjnQGdTR1DBrAjqfN5oPpmX3.jpg' alt='Cannibal Holocaust' loading='lazy'></div><div class='search-info'><div class='search-title'>Cannibal Holocaust (1980)</div></div></div>"""
    private val terrifier = """<div class='search-item' onclick="location.href='/15118725-terrifier-3.html'"><div class='search-poster'><img src='x' alt='Terrifier 3' loading='lazy'></div><div class='search-info'><div class='search-title'>Terrifier 3 (2024)</div></div></div><div class='search-item' onclick="location.href='/17193-terrifier-film-streaming-complet-vf.html'"><div class='search-poster'><img src='y' alt='Terrifier' loading='lazy'></div><div class='search-info'><div class='search-title'>Terrifier (2018)</div></div></div>"""
    private val series = """<div class='search-item search-series' onclick='showSeasons(this)' data-seasons='[{&quot;n&quot;:1,&quot;l&quot;:&quot;\/1510456-stranger-things-saison-1-streaming-complet-vf-vostfr.html&quot;,&quot;p&quot;:&quot;x&quot;}]'><div class='search-poster'><img src='x' alt='Stranger Things' loading='lazy'></div><div class='search-info'><div class='search-title'>Stranger Things - série</div></div></div>"""

    @Test fun `recherche French-Stream - titre, annee, langue de l'adresse`() {
        val c = VfCheck666.parseSearch(cannibal).single()
        assertEquals("Cannibal Holocaust", c.title); assertEquals(1980, c.year)
        assertEquals(VfCheck666.Tag.DUB, c.tag); assertFalse(c.series)
        val t = VfCheck666.parseSearch(terrifier)
        assertEquals(listOf("Terrifier 3", "Terrifier"), t.map { it.title })
        assertEquals(VfCheck666.Tag.NONE, t[0].tag)   // adresse muette : la fiche sera lue
        val s = VfCheck666.parseSearch(series).single()
        assertTrue(s.series); assertEquals("Stranger Things", s.title); assertEquals(VfCheck666.Tag.DUB_SUB, s.tag)
    }

    @Test fun `grande plateforme FR ou BE qui double - oui ; chaine de niche ou location - non`() {
        val netflix = JSONObject("""{"FR":{"flatrate":[{"provider_name":"Netflix"}]}}""")
        assertTrue(VfCheck666.onDubbingPlatform(netflix))
        val auvio = JSONObject("""{"BE":{"free":[{"provider_name":"RTBF Auvio"}]}}""")
        assertTrue(VfCheck666.onDubbingPlatform(auvio))
        val shadowz = JSONObject("""{"FR":{"flatrate":[{"provider_name":"Shadowz Amazon Channel"}]}}""")
        assertFalse(VfCheck666.onDubbingPlatform(shadowz))
        val rent = JSONObject("""{"FR":{"rent":[{"provider_name":"Apple TV"}]}}""")
        assertFalse(VfCheck666.onDubbingPlatform(rent))
        assertFalse(VfCheck666.onDubbingPlatform(JSONObject("""{"US":{"flatrate":[{"provider_name":"Netflix"}]}}""")))
    }

    @Test fun `VOSTFR seul n'est pas une VF, version cinema reperee`() {
        assertEquals(VfCheck666.Tag.SUB, VfCheck666.languageTag("Film VOSTFR"))
        assertFalse(VfCheck666.isVf(VfCheck666.merge(listOf(VfCheck666.Tag.SUB, VfCheck666.Tag.NONE))))
        assertTrue(VfCheck666.isVf(VfCheck666.merge(listOf(VfCheck666.Tag.SUB, VfCheck666.Tag.DUB))))
        assertTrue(VfCheck666.CAM_RELEASE.containsMatchIn("HDCAM"))
        assertTrue(VfCheck666.CAM_RELEASE.containsMatchIn("MD"))
        assertFalse(VfCheck666.CAM_RELEASE.containsMatchIn("HDLight 1080p"))
    }
}
