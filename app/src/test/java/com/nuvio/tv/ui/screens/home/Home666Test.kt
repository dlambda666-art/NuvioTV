package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (22/11) : Abattoir dégagé, « Reprendre en VF » dans la morgue, corps pendu dans le coin. */
class Home666Test {
    @Test fun `abattoir degage - plus de description ni de bouton en haut a gauche`() {
        assertTrue(Home666.ABATTOIR_DEGAGE)
    }

    @Test fun `le bouton de la morgue dit Reprendre en VF ou Lecture VF`() {
        assertEquals("▶ Reprendre en VF", Home666.drawerPlayLabel(0.4f))
        assertEquals("▶ Lecture VF", Home666.drawerPlayLabel(0f))
    }

    @Test fun `le corps pendu reste dans le coin haut gauche, au-dessus de la tuile de la morgue`() {
        val corps = Abattoir666.HANGS.last()
        assertTrue(corps.x < 640f)            // tiers gauche de l'image 1920
        assertTrue(corps.y + 500f < 540f)     // l'image fait 500 px de haut : finit avant la moitié de l'écran
        assertTrue(corps.amp <= 2f)           // il est lourd : il bouge à peine
    }

    @Test fun `l ampoule eclaire le corps, grésille a peine, jamais eteinte`() {
        var min = 1f
        var t = 0.0
        while (t < 14.0) { min = minOf(min, Abattoir666.bulbLevel(t)); t += 0.01 }
        assertTrue(min >= 0.3f)
        assertEquals(1f, Abattoir666.bulbLevel(1.0))
        assertTrue(Abattoir666.BULB_X < 640f && Abattoir666.BULB_Y < 200f)
    }

    @Test fun `le corps pendu est sec - aucune goutte ne part de lui ni ne lui tombe dessus`() {
        // le corps : environ x 245..415, y 60..494 dans l'image 1920 x 1080 ;
        // les gouttes sont dans l'espace 1280 en miroir : x écran = (1280 - x) × 1,5, y écran = y × 1,5
        Abattoir666.SOURCES.forEach { s ->
            val x = (1280f - s.x) * 1.5f
            val y0 = s.y0 * 1.5f
            val y1 = s.y1 * 1.5f
            val surLui = x in 245f..415f && y0 < 494f && y1 > 60f
            assertTrue("goutte en ${s.x},${s.y0}", !surLui)
        }
    }

    // 23/11 : bouton du programme en cours sous le corps pendu
    @Test fun `bouton sous le corps, centre sur lui, jamais dans les rangees`() {
        val spot = Home666.bodyButtonSpot(960f, 540f, 540f * 0.52f)
        org.junit.Assert.assertEquals(165f, spot.centerX, 0.5f)          // 330 / 2
        assertTrue(spot.top >= 487f / 2f - 0.5f || spot.top <= 540f - 540f * 0.52f - 28f)
        assertTrue(spot.top + 28f <= 540f - 540f * 0.52f)                 // au-dessus de « Continuer à regarder »
        // écran plus étroit (Crop calé à droite) : le corps se décale, le bouton aussi
        val narrow = Home666.bodyButtonSpot(800f, 540f, 280f)
        assertTrue(narrow.centerX < 165f)
    }

    @Test fun `titre du programme dans le bouton`() {
        org.junit.Assert.assertEquals("Godzilla Minus One", Home666.bodyButtonLabel("Godzilla Minus One"))
        org.junit.Assert.assertEquals("Reprendre en VF", Home666.bodyButtonLabel(null))
        org.junit.Assert.assertEquals("Reprendre en VF", Home666.bodyButtonLabel("  "))
        assertTrue(Home666.bodyButtonLabel("x".repeat(60)).length <= 34)
    }
}
