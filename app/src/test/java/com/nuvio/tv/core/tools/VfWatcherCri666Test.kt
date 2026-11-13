package com.nuvio.tv.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 666TVF (03/11) : cas pièges du cri du guetteur — VF sûre, 1080p ou 4K seulement. */
class VfWatcherCri666Test {

    @Test fun `vraie VF en 1080p, HDLight ou 4K - ca crie`() {
        listOf(
            "Resident.Evil.2026.TRUEFRENCH.1080p.WEB.H264",
            "Beware Boiuna 2026 FRENCH VFF 1080p HDLight x264",
            "Apocalypse.Z.2026.MULTi.VFF.2160p.WEB.DV.HDR",
            "Ghost in the Cell (2026) VFQ 1080p BluRay",
            "Forbidden Fruits 2026 MULTi VF2 4K"
        ).forEach { assertTrue(it, VfWatcher666.isCryRelease(it)) }
    }

    @Test fun `pas de cri - VOSTFR, MULTi incertain, VF douteuse, 720p, CAM`() {
        listOf(
            "Resident.Evil.2026.VOSTFR.1080p.WEB",
            "Resident.Evil.2026.MULTi.1080p.WEB",
            "Resident Evil 2026 VF ? 1080p",
            "Resident.Evil.2026.TRUEFRENCH.720p.WEB",
            "Resident.Evil.2026.TRUEFRENCH.HDCAM",
            "Resident.Evil.2026.TRUEFRENCH.TS.1080p",
            "Resident.Evil.2026.TRUEFRENCH.WEB",
            ""
        ).forEach { assertFalse(it, VfWatcher666.isCryRelease(it)) }
        assertFalse(VfWatcher666.isCryRelease(null))
    }

    @Test fun `un film ne crie qu'une fois, et pas pour les vieux au premier passage`() {
        val today = LocalDate.of(2026, 11, 3)
        // passage normal : tout film nouveau en VF crie
        assertTrue(VfWatcher666.shouldCry(false, false, null, today))
        assertTrue(VfWatcher666.shouldCry(false, false, today.minusDays(60), today))
        // jamais deux fois
        assertFalse(VfWatcher666.shouldCry(false, true, today, today))
        // premier passage : seulement une sortie de la semaine
        assertTrue(VfWatcher666.shouldCry(true, false, today.minusDays(3), today))
        assertFalse(VfWatcher666.shouldCry(true, false, today.minusDays(30), today))
        assertFalse(VfWatcher666.shouldCry(true, false, null, today))
        assertFalse(VfWatcher666.shouldCry(true, false, today.plusDays(2), today))
    }

    @Test fun `PLE de la WWE reconnus, pas les docus ni les jeux`() {
        listOf("WWE Survivor Series: WarGames 2026", "Survivor Series: WarGames", "WWE Crown Jewel 2026",
            "Royal Rumble 2026", "NXT Halloween Havoc 2026", "WWE Saturday Night's Main Event", "WrestleMania 42")
            .forEach { assertTrue(it, VfWatcher666.isPleTitle(it)) }
        listOf("WWE Legends: Biography", "WWE Untold: The Streak", "The Best of WWE 2026", "WWE 2K26", "Évolution", "Backlash")
            .forEach { assertFalse(it, VfWatcher666.isPleTitle(it)) }
    }

    @Test fun `PLE verifie seulement les 4 jours apres sa sortie`() {
        val today = LocalDate.of(2026, 11, 30)
        assertTrue(VfWatcher666.inPleWindow(today, today))
        assertTrue(VfWatcher666.inPleWindow(today.minusDays(4), today))
        assertFalse(VfWatcher666.inPleWindow(today.minusDays(5), today))
        assertFalse(VfWatcher666.inPleWindow(today.plusDays(1), today))
        assertFalse(VfWatcher666.inPleWindow(null, today))
    }

    @Test fun `L Odyssee - HDLight VF sans 1080p ecrit crie, capture cine jamais`() {
        assertTrue(VfWatcher666.isCryRelease("L'Odyssée 2026 FRENCH HDLight"))
        assertTrue(VfWatcher666.isCryRelease("The.Odyssey.2026.TRUEFRENCH.HDLight.x264-TEAM"))
        assertFalse(VfWatcher666.isCryRelease("The.Odyssey.2026.TRUEFRENCH.HDLight.720p.x264"))
        assertFalse(VfWatcher666.isCryRelease("The.Odyssey.2026.FRENCH.MD.1080p.HDTS.x264"))
        assertFalse(VfWatcher666.isCryRelease("The.Odyssey.2026.FRENCH.1080p.TC.x264"))
        assertFalse(VfWatcher666.isCryRelease("L'Odyssée 2026 FRENCH HDCAM"))
    }

    @Test fun `piege Clayface - film etranger encore au cinema, pas de cri`() {
        val today = LocalDate.of(2026, 11, 13)
        fun d(r: String, ty: Int, s: String) = VfWatcher666.RelDate(r, ty, LocalDate.parse(s))
        // Clayface : salles FR/BE 21/10, USA 23/10 ; VOD USA 08/12, VOD FR 24/02/2027 (French-Stream : VF en CAM)
        val clayface = listOf(d("FR", 3, "2026-10-21"), d("BE", 3, "2026-10-21"), d("CA", 3, "2026-10-23"),
            d("US", 1, "2026-10-13"), d("US", 3, "2026-10-23"), d("US", 4, "2026-12-08"), d("FR", 5, "2027-02-24"))
        assertTrue(VfWatcher666.isCinemaOnly("en", clayface, today))
        // le 8 décembre, la VOD américaine est sortie : une vraie VFQ / VFF peut crier
        assertFalse(VfWatcher666.isCinemaOnly("en", clayface, LocalDate.of(2026, 12, 8)))
        // L'Odyssée : VOD USA déjà sortie, le HDLight VF crie avant la VOD française
        assertFalse(VfWatcher666.isCinemaOnly("en", listOf(d("FR", 3, "2026-07-16"), d("US", 4, "2026-10-20"), d("FR", 4, "2026-11-17")), today))
        // film français : ses propres règles (pas touché ici)
        assertFalse(VfWatcher666.isCinemaOnly("fr", listOf(d("FR", 3, "2026-10-21")), today))
        // aucune date connue : on ne bloque pas
        assertFalse(VfWatcher666.isCinemaOnly("en", emptyList(), today))
    }
}
