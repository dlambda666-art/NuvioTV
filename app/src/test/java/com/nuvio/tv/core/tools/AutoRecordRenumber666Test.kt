package com.nuvio.tv.core.tools

import com.nuvio.tv.core.calendar.CalendarEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** 12/11 piège : NXT annoncé avec la numérotation TMDB (Cinemeta en panne) ne s'enregistrait jamais. */
class AutoRecordRenumber666Test {
    private fun ep(s: Int, e: Int, at: String) = CalendarEpisode("tt1601141", "WWE NXT", null, s, e, null, Instant.parse(at), null)
    private fun pend(s: Int, e: Int, air: Long) = AutoRecord666.Pending("tt1601141", "WWE NXT", null, s, e, null, 1, 0, air)
    private val now = Instant.parse("2026-11-12T06:00:00Z").toEpochMilli()

    @Test fun `numero TMDB remplace par le numero du calendrier, par le jour`() {
        val cal = listOf(ep(2026, 44, "2026-11-04T00:00:00Z"), ep(2026, 45, "2026-11-11T00:00:00Z"))
        val p = AutoRecord666.renumbered(pend(20, 45, Instant.parse("2026-11-11T01:00:00Z").toEpochMilli()), cal, now)!!
        assertEquals(2026 to 45, p.season to p.episode)
    }

    @Test fun `ancienne attente sans jour - le dernier episode sorti`() {
        val cal = listOf(ep(2026, 44, "2026-11-04T00:00:00Z"), ep(2026, 45, "2026-11-11T00:00:00Z"))
        assertEquals(45, AutoRecord666.renumbered(pend(20, 45, 0L), cal, now)!!.episode)
    }

    @Test fun `bon numero - rien ne change`() {
        val cal = listOf(ep(2026, 45, "2026-11-11T00:00:00Z"))
        assertNull(AutoRecord666.renumbered(pend(2026, 45, 0L), cal, now))
    }
}
