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
        assertNull(Horreur666.predatorLaser(5_000))
        assertNotNull(Horreur666.predatorLaser(12_500))
        assertNull(Horreur666.predatorLaser(12_000 + 6_000))
        assertNotNull(Horreur666.predatorLaser(12_000 + 40_000 + 1_000))
        assertEquals(1f, Horreur666.laserSpread(0f), 0.001f)
        assertEquals(0f, Horreur666.laserSpread(0.5f), 0.001f)
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
        assertEquals(0f, Horreur666.eggStir(20.0 + 30.0), 0.001f)
        assertEquals(1f, Horreur666.eggStir(20.0 + 75.0 + 2.0), 0.001f)
    }
}
