package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Cas piège (18/11, cri de Raw à 05:30) : le journal dit quand la VF a été trouvée et pourquoi le cri a attendu. */
class Announce666TimingTest {
    private val zone = ZoneId.of("Europe/Brussels")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun `cri en retard explique`() {
        val air = at(17, 2, 0)
        assertEquals(
            "diffusé il y a 27 h · VF trouvée à 03:10 · cri à 05:30 (attendu 140 min : film en cours ou écran éteint)",
            Announce666.timing(air, at(18, 3, 10), at(18, 5, 30), zone)
        )
    }

    @Test fun `cri immediat sans parenthese`() {
        assertEquals(
            "diffusé il y a 26 h · VF trouvée à 04:00 · cri à 04:05",
            Announce666.timing(at(17, 2, 0), at(18, 4, 0), at(18, 4, 5), zone)
        )
    }

    @Test fun `heure de decouverte inconnue`() {
        assertEquals("diffusé il y a 1 h · cri à 03:00", Announce666.timing(at(18, 2, 0), null, at(18, 3, 0), zone))
    }
}
