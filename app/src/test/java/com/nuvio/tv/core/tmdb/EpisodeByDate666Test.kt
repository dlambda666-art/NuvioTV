package com.nuvio.tv.core.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 666TVF (06/11) : cas piège NXT — le calendrier dit S20E49 (PLE comptés), les liens suivent S20E44. */
class EpisodeByDate666Test {
    private val d = LocalDate.of(2026, 11, 4)
    private val season = (1..44).map { n -> n to LocalDate.of(2026, 1, 7).plusWeeks((n - 1).toLong()) }

    @Test fun `l'episode de la semaine est retrouve par sa date`() {
        assertEquals(44, pickEpisodeByDate666(season, d))
        assertEquals(44, pickEpisodeByDate666(season, d.plusDays(1)))   // décalage de fuseau
        assertEquals(44, pickEpisodeByDate666(season, d.minusDays(1)))
    }

    @Test fun `rien d'invente - pas d'episode ce jour-la, ou deux possibles`() {
        assertNull(pickEpisodeByDate666(season, d.plusDays(3)))
        assertNull(pickEpisodeByDate666(listOf(1 to d, 2 to d), d))
        assertNull(pickEpisodeByDate666(listOf(1 to null), d))
    }
}
