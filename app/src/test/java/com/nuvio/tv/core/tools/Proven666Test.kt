package com.nuvio.tv.core.tools

import com.nuvio.tv.core.debrid.AllDebridFallback666
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cas pièges : historique des liens qui marchent (07/11). */
class Proven666Test {
    private fun s(title: String, hash: String = title.hashCode().toString(), guess: Boolean = false) = Stream(
        name = "Comet666", title = title, description = null, url = null, ytId = null, infoHash = hash, fileIdx = null,
        externalUrl = null, behaviorHints = StreamBehaviorHints(null, "666|comet666|x", null, null),
        addonName = "Comet666", addonLogo = null,
        debridCacheStatus = if (guess) StreamDebridCacheStatus(AllDebridFallback666.PROVIDER_ID, "AllDebrid ?", StreamDebridCacheState.UNKNOWN) else null
    )

    @Test fun `equipe lue en fin de nom`() {
        assertEquals("FW", Proven666.teamOf("WWE.NXT.2026.11.04.FRENCH.1080p.WEB.H264-FW\n🇫🇷 VF"))
        assertEquals("TEAM", Proven666.teamOf("Show.S01E05.MULTi.1080p.WEB-TEAM.mkv"))
        assertNull(Proven666.teamOf("Show.S01E05.MULTi.1080p.WEB.H264"))
        assertNull(Proven666.teamOf("Show S01E05 MULTi 1080p-x265"))
        assertNull(Proven666.teamOf(null))
    }

    @Test fun `film - pas d'historique`() {
        assertNull(Proven666.seriesOf("tt1234567"))
        assertEquals("tt0185103", Proven666.seriesOf("tt0185103:2026:45"))
    }

    @Test fun `equipe qui a marche passe en tete, ordre garde sinon`() {
        val a = s("NXT.FRENCH.1080p-AAA"); val b = s("NXT.FRENCH.2160p-BBB"); val c = s("NXT.FRENCH.1080p-CCC")
        assertEquals(listOf(c, a, b), Proven666.sortProven(listOf(a, b, c), listOf("CCC")))
        assertEquals(listOf(a, b, c), Proven666.sortProven(listOf(a, b, c), emptyList()))
    }

    @Test fun `un lien AllDebrid point d'interrogation ne passe jamais devant`() {
        val ok = s("NXT.FRENCH.1080p-AAA"); val guess = s("NXT.FRENCH.1080p-CCC", guess = true)
        assertEquals(listOf(ok, guess), Proven666.sortProven(listOf(ok, guess), listOf("CCC")))
    }

    @Test fun `trois equipes max, la plus recente d'abord`() {
        var t = emptyList<String>()
        for (x in listOf("A", "B", "C", "D", "B")) t = Proven666.pushTeam(t, x)
        assertEquals(listOf("B", "D", "C"), t)
    }
}
