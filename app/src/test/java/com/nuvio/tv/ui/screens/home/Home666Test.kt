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
}
