package com.nuvio.tv.core.horreur

import com.nuvio.tv.ui.screens.livetv.BloodTest666
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (23/11) : les clins d'œil aux films d'horreur. */
class Horreur666Test {
    @Test fun `ki-ki-ki ma-ma-ma seulement sur Slasher, jamais pendant un film, pas en rafale`() {
        assertTrue(Horreur666.shouldWhisper("Slasher", inPlayer = false, sinceLastMs = -1))
        assertTrue(Horreur666.shouldWhisper("slasher", inPlayer = false, sinceLastMs = 120_000))
        assertFalse(Horreur666.shouldWhisper("Classiques", inPlayer = false, sinceLastMs = -1))
        assertFalse(Horreur666.shouldWhisper(null, inPlayer = false, sinceLastMs = -1))
        assertFalse(Horreur666.shouldWhisper("Slasher", inPlayer = true, sinceLastMs = -1))
        assertFalse(Horreur666.shouldWhisper("Slasher", inPlayer = false, sinceLastMs = 3_000))
        assertTrue(Horreur666.shouldWhisper("Slasher", inPlayer = false, sinceLastMs = 30_000))   // à chaque fois (23-11)
    }

    @Test fun `titre a l'envers puis droit (L'Exorciste)`() {
        assertEquals(180f, Horreur666.titleRotation(0), 0.01f)
        assertEquals(180f, Horreur666.titleRotation(Horreur666.UPSIDE_MS - 1), 0.01f)
        val mid = Horreur666.titleRotation(Horreur666.UPSIDE_MS + Horreur666.UPSIDE_TURN_MS / 2)
        assertTrue(mid in 0f..180f)
        assertEquals(0f, Horreur666.titleRotation(Horreur666.UPSIDE_MS + Horreur666.UPSIDE_TURN_MS), 0.01f)
        assertEquals(0f, Horreur666.titleRotation(60_000), 0.01f)
    }

    @Test fun `tempete de neige toutes les nuits (23-11, choix de David)`() {
        assertTrue((0L until 1000L).all { Horreur666.snowNight(20_000L + it) })
    }

    @Test fun `camera Evil Dead passe de temps en temps, pas en permanence`() {
        val runs = (0L until 7 * 60_000L step 1_000L).count { Horreur666.entityProgress(it) != null }
        assertEquals(12, runs)
        assertNull(Horreur666.entityProgress(0L))
        assertNotNull(Horreur666.entityProgress(3 * 60_000L + 500L))
    }

    @Test fun `silhouette un passage de nuage sur trois, partie au suivant`() {
        assertTrue(Horreur666.figureVisible(1))
        assertFalse(Horreur666.figureVisible(2))
        assertTrue(Horreur666.figureVisible(4))
        assertFalse(Horreur666.figureVisible(0))
    }

    @Test fun `test sanguin - verdicts et noms courts`() {
        assertEquals("INFECTÉ", BloodTest666.verdict(false))
        assertEquals("humain", BloodTest666.verdict(true))
        assertEquals("Wastream", BloodTest666.shortName("Plugin · Wastream666"))
        assertTrue(BloodTest666.shortName("Plugin · StreamFusionReborn").length <= 12)
    }

    @Test fun `ombre de Nosferatu un cycle de neon sur trois, partie a la coupure suivante`() {
        assertNull(Horreur666.nosferatu(3.0))
        assertNull(Horreur666.nosferatu(7.0))                 // cycle 0 : rien
        assertNotNull(Horreur666.nosferatu(6.7 + 9.0 + 0.5))  // cycle 1 : elle est là après la grosse coupure
        assertNull(Horreur666.nosferatu(6.7 + 9.0 + 8.5))     // disparue avant la coupure suivante
        assertNull(Horreur666.nosferatu(6.7 + 18.0 + 1.0))    // cycle 2 : rien
        assertNotNull(Horreur666.nosferatu(6.7 + 36.0 + 1.0)) // cycle 4 : revient
        val p = Horreur666.nosferatu(6.7 + 9.0 + 4.1)!!
        assertTrue(p in 0.45f..0.55f)
    }

    // 24/11 : un seul rire (la carte par-dessus l'autre appli, ou Frank devant après le cinéma et Machine Head)
    @Test fun `son d'annonce seulement Frank devant, hors film, apres le cinema et Machine Head`() {
        val A = com.nuvio.tv.core.tools.Announce666
        assertTrue(A.soundAllowed(frankInFront = true, inPlayer = false, cinemaShowing = false, abattoirSoundPlaying = false))
        assertFalse(A.soundAllowed(frankInFront = false, inPlayer = false, cinemaShowing = false, abattoirSoundPlaying = false))
        assertFalse(A.soundAllowed(frankInFront = true, inPlayer = true, cinemaShowing = false, abattoirSoundPlaying = false))
        assertFalse(A.soundAllowed(frankInFront = true, inPlayer = false, cinemaShowing = true, abattoirSoundPlaying = false))
        assertFalse(A.soundAllowed(frankInFront = true, inPlayer = false, cinemaShowing = false, abattoirSoundPlaying = true))
        assertTrue(A.OVERLAY_MS in 10_000L..30_000L)
    }

    @Test fun `viseur de Predator 5 s toutes les 40 s, triangle cale apres l'arrivee`() {
        // 25/11 au soir : les lasers arrivent quand la bête se pose sur Frank (35 s + 9,7 s), 10 s, toutes les 30 s
        val land = ((20.0 + 15.0 + Horreur666.CLING_AT_S) * 1000).toLong()
        assertNull(Horreur666.predatorLaser(5_000))
        assertNull(Horreur666.predatorLaser(land - 500))
        assertNotNull(Horreur666.predatorLaser(land + 500))
        assertNotNull(Horreur666.predatorLaser(land + 9_500))            // reste calé 10 s
        assertNull(Horreur666.predatorLaser(land + 10_500))
        assertNotNull(Horreur666.predatorLaser(land + 30_000 + 1_000))   // toutes les 30 s
        assertNull(Horreur666.predatorLaser(land + 20_000))
        assertEquals(1f, Horreur666.laserSpread(0f), 0.001f)
        assertEquals(0f, Horreur666.laserSpread(0.2f), 0.001f)           // calés après 1,5 s
        assertEquals(0f, Horreur666.laserSmoke(0.45f), 0.001f)           // pas de fumée avant 5 s
        assertTrue(Horreur666.laserSmoke(0.6f) > 0f)                     // puis la fumée monte
        assertEquals(1f, Horreur666.laserSmoke(0.95f), 0.001f)
    }

    @Test fun `vision thermique 7 s toutes les 5 min, froid bleu chaud rouge`() {
        assertNull(Horreur666.thermalVision(60_000))
        assertNotNull(Horreur666.thermalVision(90_000 + 1_000))
        assertNull(Horreur666.thermalVision(90_000 + 8_000))
        assertNotNull(Horreur666.thermalVision(90_000 + 5 * 60_000 + 3_000))
        assertEquals(1f, Horreur666.thermalAlpha(0.5f), 0.001f)
        val cold = Horreur666.thermalColor(0); val hot = Horreur666.thermalColor(220)
        assertTrue((cold and 0xFF) > (cold shr 16 and 0xFF))          // froid : plus bleu que rouge
        assertTrue((hot shr 16 and 0xFF) > (hot and 0xFF))            // chaud : plus rouge que bleu
    }

    @Test fun `vision thermique - la neige reste froide, les corps sont chauds`() {
        assertTrue(Horreur666.thermalLevel(250, 0) < 110)     // neige blanche = froide
        assertTrue(Horreur666.thermalLevel(40, 255) > 180)    // corps = chaud
        assertTrue(Horreur666.thermalLevel(220, 255) > Horreur666.thermalLevel(40, 255))   // visage plus chaud que le manteau
    }

    @Test fun `oeuf d'Alien s'entrouvre 6 s toutes les 75 s`() {
        assertEquals(0f, Horreur666.eggStir(10.0), 0.001f)
        assertEquals(1f, Horreur666.eggStir(20.0 + 3.0), 0.001f)
        assertEquals(0f, Horreur666.eggStir(20.0 + 10.0), 0.001f)
        assertEquals(1f, Horreur666.eggStir(20.0 + 15.0 + 2.0), 0.001f)   // 25/11 : lueur verte + fumée toutes les 15 s
    }

    @Test fun `chestburster seulement pour un film en VF le jour de sa sortie`() {
        val d = java.time.LocalDate.of(2026, 11, 24)
        assertTrue(Horreur666.releaseDay(d, d))
        assertTrue(Horreur666.releaseDay(d.minusDays(1), d))
        assertFalse(Horreur666.releaseDay(d.minusDays(5), d))
        assertFalse(Horreur666.releaseDay(null, d))
        assertTrue(Horreur666.chestburster(listOf("film:tt123"), setOf("tt123")))
        assertFalse(Horreur666.chestburster(listOf("film:tt999"), setOf("tt123")))
        assertFalse(Horreur666.chestburster(listOf("serie:tt123"), setOf("tt123")))
        assertEquals(1f, Horreur666.burstScale(2_000), 0.001f)
        assertEquals(0f, Horreur666.burstAlpha(6_000), 0.001f)
        assertEquals(1f, Horreur666.burstAlpha(1_000), 0.001f)
    }

    @Test fun `oeuf ferme qui s'ouvre doucement, la bete sort puis bondit, toutes les 30 s (25-11)`() {
        assertEquals(0f, Horreur666.eggOpen(20.0 + 3.0), 0.001f)                 // 1er passage : fermé (ça pulse seulement)
        assertNull(Horreur666.facehuggerLeap(20.0 + 8.0))
        assertEquals(0f, Horreur666.eggOpen(20.0 + 15.0 + 1.0), 0.001f)         // 2e : d'abord fermé, ça pulse vert
        val mid = Horreur666.eggOpen(20.0 + 15.0 + 4.0)
        assertTrue(mid > 0.2f && mid < 0.8f)                                       // s'ouvre doucement (pas d'un coup)
        assertEquals(1f, Horreur666.eggOpen(20.0 + 15.0 + 6.0), 0.001f)
        assertNotNull(Horreur666.facehuggerEmerge(20.0 + 15.0 + 6.0))            // la bête monte
        assertNull(Horreur666.facehuggerLeap(20.0 + 15.0 + 6.0))
        assertNotNull(Horreur666.facehuggerLeap(20.0 + 15.0 + 8.0))              // le bond (lent)
        assertNull(Horreur666.facehuggerLeap(20.0 + 15.0 + 10.0))
        assertEquals(0f, Horreur666.eggOpen(20.0 + 15.0 + 14.5), 0.001f)        // refermé avant le tour suivant
        assertNotNull(Horreur666.facehuggerLeap(20.0 + 45.0 + 8.0))              // 30 s plus tard : ça recommence
        assertTrue(Horreur666.LEAP_S >= 2.0)
        // 25/11 au soir : agrippée au visage de Frank, puis dissoute dans la fumée du laser
        val c0 = 20.0 + 15.0
        assertNull(Horreur666.facehuggerCling(c0 + 8.0))
        assertEquals(1f, Horreur666.facehuggerCling(c0 + Horreur666.CLING_AT_S + 1.0)!!, 0.001f)
        val laserAtSmoke = Horreur666.predatorLaser(((c0 + Horreur666.DISSOLVE_AT_S) * 1000).toLong())!!
        assertTrue(Horreur666.laserSmoke(laserAtSmoke) > 0f)                       // elle part quand la fumée monte
        assertNull(Horreur666.facehuggerCling(c0 + Horreur666.DISSOLVE_AT_S + Horreur666.DISSOLVE_S + 0.1))
        assertNull(Horreur666.facehuggerLeap(c0 + Horreur666.CLING_AT_S + 1.0))   // plus de bond vers nous
    }

    @Test fun `ki-ki-ki - encore plus bas`() {
        assertTrue(Horreur666.KIKI_VOLUME <= 0.35f)
    }
}
