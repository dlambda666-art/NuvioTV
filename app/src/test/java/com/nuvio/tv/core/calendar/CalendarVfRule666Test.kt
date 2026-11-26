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

    // 26/11 : la raison de chaque rejet (Rapport « pourquoi pas de cri »)
    @Test fun `raison du rejet pour le rapport`() {
        val raw = setOf(LocalDate.of(2026, 11, 24))
        org.junit.Assert.assertEquals("720p ou moins", CalendarVfRule666.rejectReason("WWE.RAW.2026.11.24.FRENCH.720p", null, 34, 47, raw))
        org.junit.Assert.assertEquals("autre date", CalendarVfRule666.rejectReason("WWE.RAW.2026.11.17.FRENCH.1080p", null, 34, 47, raw))
        org.junit.Assert.assertEquals("VF pas sûre", CalendarVfRule666.rejectReason("WWE.RAW.2026.11.24.VOSTFR.1080p", null, 34, 47, raw))
        org.junit.Assert.assertNull(CalendarVfRule666.rejectReason("WWE.RAW.2026.11.24.FRENCH.1080p", null, 34, 47, raw))
        val line = VfWhy666.scraperLine("Comet666", 4, mapOf("720p ou moins" to 3, "autre date" to 1), "rien de valable", "WWE.RAW.2026.11.24.FRENCH.720p")
        assertTrue(line.startsWith("Comet666 : 4 trouvé(s) · écartés : 3 720p ou moins, 1 autre date → rien de valable"))
        assertFalse(com.nuvio.tv.core.tools.Rapport666.looksSecret(com.nuvio.tv.core.tools.Rapport666.clean(line)))
    }

    // 26/11 : le cri dit NXT S20E47 (TMDB), la fiche Cinemeta appelle le NXT du 25/11 S20E51 → on lance S20E51
    @Test fun `lancer depuis le calendrier retrouve l'episode de la fiche du meme jour`() {
        fun at(s: String) = java.time.Instant.parse(s)
        val fiche = listOf(
            Triple("tt1:20:47", 20 to 47, at("2026-10-29T00:00:00Z")),
            Triple("tt1:20:50", 20 to 50, at("2026-11-19T01:00:00Z")),
            Triple("tt1:20:51", 20 to 51, at("2026-11-26T01:00:00Z"))
        )
        val got = pickSameDay666(fiche, 20, at("2026-11-26T01:00:00Z"))
        org.junit.Assert.assertEquals("tt1:20:51", got?.first)
        org.junit.Assert.assertNull(pickSameDay666(fiche, 20, at("2026-12-10T01:00:00Z")))
    }
}
