package com.nuvio.tv.core.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 11/11 piège : NXT a crié 2 fois (Cinemeta et TMDB ne numérotent pas pareil). */
class DayKey666Test {
    private fun ep(season: Int, episode: Int, at: String) = CalendarEpisode(
        seriesId = "tt1601141", seriesName = "WWE NXT", poster = null, season = season, episode = episode,
        title = null, airInstant = Instant.parse(at), addonBaseUrl = null
    )

    @Test fun `meme episode numerote autrement - reconnu par son jour`() {
        val cinemeta = ep(2026, 45, "2026-11-11T00:00:00Z")
        val tmdb = ep(20, 45, "2026-11-11T01:00:00Z")   // 20 h à New York le 10/11
        assertTrue(tmdb.dayKeys666.contains(cinemeta.dayKey666))
    }

    @Test fun `episode de la semaine suivante - pas confondu`() {
        val a = ep(2026, 45, "2026-11-04T00:00:00Z")
        val b = ep(2026, 46, "2026-11-11T00:00:00Z")
        assertFalse(b.dayKeys666.contains(a.dayKey666))
    }
}
