package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (18/11) : jamais de son pendant un film ; l'intervalle est respecté. */
class AbattoirSound666Test {
    private fun play(
        mode: Int = 1, hasFile: Boolean = true, onScreen: Boolean = true, front: Boolean = true,
        screenOn: Boolean = true, inPlayer: Boolean = false, playing: Boolean = false, since: Long = -1L
    ) = AbattoirSound666.shouldPlay(mode, hasFile, onScreen, front, screenOn, inPlayer, playing, since)

    @Test fun `coupe par defaut`() = assertFalse(play(mode = 0))

    @Test fun `jamais pendant un film, Orange TV, ecran eteint, ailleurs que l'Abattoir`() {
        assertFalse(play(inPlayer = true))
        assertFalse(play(front = false))
        assertFalse(play(screenOn = false))
        assertFalse(play(onScreen = false))
        assertFalse(play(hasFile = false))
    }

    @Test fun `intervalle respecte`() {
        assertTrue(play(mode = 1, since = -1))
        assertFalse(play(mode = 1, since = 59_000))
        assertTrue(play(mode = 1, since = 60_000))
        assertFalse(play(mode = 5, since = 4 * 60_000L))
        assertTrue(play(mode = 5, since = 5 * 60_000L))
        assertFalse(play(mode = 1, since = 120_000, playing = true))
    }

    @Test fun `boucle douce enchaine des que le son est fini`() {
        assertTrue(play(mode = -1, since = 8_000))
        assertFalse(play(mode = -1, since = 3_000, playing = true))
    }

    @Test fun `fondus`() {
        assertEquals(0f, AbattoirSound666.fadeGain(0, 7_700, 20), 0.001f)
        assertEquals(0.2f, AbattoirSound666.fadeGain(4_000, 7_700, 20), 0.001f)
        assertEquals(0f, AbattoirSound666.fadeGain(7_700, 7_700, 20), 0.001f)
    }
}
