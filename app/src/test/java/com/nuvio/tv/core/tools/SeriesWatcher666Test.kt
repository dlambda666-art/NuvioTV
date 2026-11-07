package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Cas pièges : le guetteur séries (07/11). */
class SeriesWatcher666Test {
    private val today = LocalDate.of(2026, 11, 7)

    @Test fun `episode 1 de la nouvelle saison en VF sure 1080p crie`() {
        assertTrue(VfWatcher666.isSeriesCryRelease("Yellowjackets.S04E01.FRENCH.1080p.WEB.H264-TEAM", 4))
        assertTrue(VfWatcher666.isSeriesCryRelease("Show.S02.MULTi.VFF.2160p.WEB.H265-TEAM", 2))
        assertTrue(VfWatcher666.isSeriesCryRelease("Show.S02E01-08.TRUEFRENCH.1080p.WEB-TEAM", 2))
    }

    @Test fun `jamais la VF d'une vieille saison ni d'un autre episode`() {
        assertFalse(VfWatcher666.isSeriesCryRelease("Yellowjackets.S01E01.FRENCH.1080p.WEB-TEAM", 4))
        assertFalse(VfWatcher666.isSeriesCryRelease("Yellowjackets.S04E03.FRENCH.1080p.WEB-TEAM", 4))
        assertFalse(VfWatcher666.isSeriesCryRelease("Show.S01-S03.FRENCH.1080p.WEB-TEAM", 3))
        assertFalse(VfWatcher666.isSeriesCryRelease("Show.FRENCH.1080p.WEB-TEAM", 3)) // saison inconnue
    }

    @Test fun `memes regles que les films - pas de 720p, pas de VF douteuse`() {
        assertFalse(VfWatcher666.isSeriesCryRelease("Show.S04E01.FRENCH.720p.WEB-TEAM", 4))
        assertFalse(VfWatcher666.isSeriesCryRelease("Show.S04E01.VOSTFR.1080p.WEB-TEAM", 4))
        assertFalse(VfWatcher666.isSeriesCryRelease("Show.S04E01.MULTi.1080p.WEB-Mixio", 4))
    }

    @Test fun `saison qui demarre - lue depuis TMDB`() {
        val raw = """{"name":"Yellowjackets","poster_path":"/p.jpg","external_ids":{"imdb_id":"tt11041332"},
            "last_episode_to_air":{"season_number":4,"episode_number":2},
            "seasons":[{"season_number":3,"air_date":"2025-02-14"},{"season_number":4,"air_date":"2026-10-20"}]}"""
        val c = VfWatcher666.parseSeason(raw, today)!!
        assertEquals("tt11041332", c.imdbId)
        assertEquals(4, c.season)
        assertEquals(LocalDate.of(2026, 10, 20), c.seasonStart)
    }

    @Test fun `saison commencee il y a longtemps ou sans id IMDb - ignoree`() {
        val old = """{"name":"X","external_ids":{"imdb_id":"tt1"},"last_episode_to_air":{"season_number":2},
            "seasons":[{"season_number":2,"air_date":"2026-06-01"}]}"""
        assertNull(VfWatcher666.parseSeason(old, today))
        val noImdb = """{"name":"X","external_ids":{},"last_episode_to_air":{"season_number":2},
            "seasons":[{"season_number":2,"air_date":"2026-11-01"}]}"""
        assertNull(VfWatcher666.parseSeason(noImdb, today))
    }

    @Test fun `premier passage - seule une saison de la semaine crie`() {
        assertTrue(VfWatcher666.shouldCry(true, false, today.minusDays(3), today))
        assertFalse(VfWatcher666.shouldCry(true, false, today.minusDays(30), today))
        assertTrue(VfWatcher666.shouldCry(false, false, today.minusDays(30), today))
    }
}
