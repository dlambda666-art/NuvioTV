package com.nuvio.tv.core.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Cas pièges (07/11) : « VF dispo » faux pour SmackDown et AHS S13. */
class CalendarVfRule666Test {
    private val smackdown = setOf(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7))

    @Test fun `SmackDown - le lien de la semaine passee ne compte pas`() {
        assertFalse(CalendarVfRule666.counts("WWE.SmackDown.2026.10.31.FRENCH.1080p.WEB.h264", "1080p", 28, 45, smackdown))
        assertFalse(CalendarVfRule666.counts("WWE SmackDown 31/10/2026 VF 1080p", null, 28, 45, smackdown))
    }

    @Test fun `SmackDown - le bon jour compte`() {
        assertTrue(CalendarVfRule666.counts("WWE.SmackDown.2026.11.07.FRENCH.1080p.WEB.h264", "1080p", 28, 45, smackdown))
        assertTrue(CalendarVfRule666.counts("WWE.SmackDown.2026.11.06.FRENCH.1080p", null, 28, 45, smackdown))
    }

    @Test fun `AHS - autre episode ou VOSTFR ne compte pas`() {
        val any = emptySet<LocalDate>()
        assertFalse(CalendarVfRule666.counts("American.Horror.Story.S13E12.FRENCH.1080p.WEB", "1080p", 13, 13, any))
        assertFalse(CalendarVfRule666.counts("American.Horror.Story.S13E13.VOSTFR.1080p.WEB", "1080p", 13, 13, any))
        assertFalse(CalendarVfRule666.counts("American.Horror.Story.S13E13.MULTi.1080p 🇫🇷 MULTi (VF ?)", "1080p", 13, 13, any))
        assertTrue(CalendarVfRule666.counts("American.Horror.Story.S13E13.FRENCH.1080p.WEB", "1080p", 13, 13, any))
    }

    @Test fun `jamais de 720p`() {
        assertFalse(CalendarVfRule666.counts("Show.S01E02.FRENCH.720p.WEB", null, 1, 2, emptySet()))
        assertFalse(CalendarVfRule666.counts("Lumio VF", "720p", 1, 2, emptySet()))
        assertTrue(CalendarVfRule666.counts("Lumio VF", "1080p", 1, 2, emptySet()))
    }

    @Test fun `pack de saison ou titre sans numero - garde`() {
        assertTrue(CalendarVfRule666.counts("Show.S01.FRENCH.1080p.WEB", null, 1, 2, emptySet()))
        assertTrue(CalendarVfRule666.counts("Show.S01E01-E08.FRENCH.1080p.WEB", null, 1, 1, emptySet()))
    }

    @Test fun `lien pret sans numero ni date - ne prouve rien (SmackDown Lumio)`() {
        assertFalse(CalendarVfRule666.namesEpisode("Lumio 1080p VF", 28, 45, smackdown))
        assertFalse(CalendarVfRule666.namesEpisode("WWE SmackDown VF 1080p", 28, 45, smackdown))
        assertFalse(CalendarVfRule666.namesEpisode("WWE.SmackDown.2026.10.31.FRENCH", 28, 45, smackdown))
    }

    @Test fun `lien pret qui nomme l'episode - prouve`() {
        assertTrue(CalendarVfRule666.namesEpisode("WWE.SmackDown.2026.11.07.FRENCH.1080p", 28, 45, smackdown))
        assertTrue(CalendarVfRule666.namesEpisode("American Horror Story S13E13 VF 1080p", 13, 13, emptySet()))
        assertFalse(CalendarVfRule666.namesEpisode("American Horror Story S13E12 VF 1080p", 13, 13, emptySet()))
    }
}
