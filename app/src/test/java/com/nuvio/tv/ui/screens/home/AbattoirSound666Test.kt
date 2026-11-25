package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (18/11, revus le 23/11) : une seule fois aux portes, jamais pendant un film, ~20 s avec fondu. */
class AbattoirSound666Test {
    private fun doors(
        mode: Int = 1, hasFile: Boolean = true, front: Boolean = true, inPlayer: Boolean = false, playing: Boolean = false
    ) = AbattoirSound666.shouldPlayAtDoors(mode, hasFile, front, inPlayer, playing)

    @Test fun `aux portes seulement si allume, avec un fichier, Frank devant, pas de film`() {
        assertTrue(doors())
        assertFalse(doors(mode = 0))
        assertFalse(doors(hasFile = false))
        assertFalse(doors(front = false))
        assertFalse(doors(inPlayer = true))
        assertFalse(doors(playing = true))
    }

    @Test fun `plus de repetition (23-11) - anciens reglages repris en marche`() {
        assertEquals(listOf(0, 1), AbattoirSound666.MODES)
        assertEquals(1, AbattoirSound666.migrate(10))
        assertEquals(1, AbattoirSound666.migrate(20))
        assertEquals(1, AbattoirSound666.migrate(60))
        assertEquals(1, AbattoirSound666.migrate(-1))
        assertEquals(0, AbattoirSound666.migrate(0))
        assertEquals("coupé", AbattoirSound666.modeLabel(0))
        assertEquals("à l'ouverture des portes", AbattoirSound666.modeLabel(1))
    }

    @Test fun `plus long que 10 s, fondu de fin`() {
        assertTrue(AbattoirSound666.PLAY_MS > 10_000L)
        assertEquals(0f, AbattoirSound666.fadeGain(0, 40), 0.001f)
        assertEquals(0.4f, AbattoirSound666.fadeGain(10_000, 40), 0.001f)
        assertEquals(0.2f, AbattoirSound666.fadeGain(AbattoirSound666.PLAY_MS - 1_500, 40), 0.001f)
        assertEquals(0f, AbattoirSound666.fadeGain(AbattoirSound666.PLAY_MS, 40), 0.001f)
    }

    @Test fun `fichier recu par Send Files to TV reconnu (piege Shield du haut)`() {
        assertTrue(AbattoirSound666.isAudioName("Abattoir_long.mp3"))
        assertTrue(AbattoirSound666.isAudioName("abattoir.M4A"))
        assertFalse(AbattoirSound666.isAudioName("film.mkv"))
        assertFalse(AbattoirSound666.isAudioName("mp3.txt"))
    }

    @Test fun `a chaque ouverture des portes, sauf doublon immediat (23-11)`() {
        assertTrue(AbattoirSound666.shouldPlayAtDoors(1, true, true, false, false, sinceLastDoorsMs = -1))
        assertFalse(AbattoirSound666.shouldPlayAtDoors(1, true, true, false, false, sinceLastDoorsMs = 5_000))
        assertTrue(AbattoirSound666.shouldPlayAtDoors(1, true, true, false, false, sinceLastDoorsMs = 25_000))
        assertTrue(AbattoirSound666.shouldPlayAtDoors(1, true, true, false, false, sinceLastDoorsMs = AbattoirSound666.DOORS_GAP_MS))
    }

    @Test fun `le fichier une seule fois, plus jamais en boucle (23-11, 3x le son)`() {
        assertEquals(7_700L, AbattoirSound666.playLength(7_700L))
        assertEquals(AbattoirSound666.PLAY_MS, AbattoirSound666.playLength(60_000L))
        // fondu de fin raccourci pour un fichier court : on l'entend jusqu'au bout
        assertEquals(0.4f, AbattoirSound666.fadeGain(4_000, 40, 7_700L), 0.001f)
        assertEquals(0f, AbattoirSound666.fadeGain(7_700, 40, 7_700L), 0.001f)
    }

    @Test fun `bobine - jamais coupee net, fondu croise avec Machine Head (23-11)`() {
        val b = com.nuvio.tv.ui.components.Bobine666
        assertEquals(0.7f, b.fadeLevel(0.7f, 0, 6_000), 0.001f)
        assertEquals(0.35f, b.fadeLevel(0.7f, 3_000, 6_000), 0.001f)     // 24/11 : encore à moitié au milieu (fondu croisé)
        assertTrue(b.fadeLevel(0.7f, 1_000, 6_000) > 0.6f)              // reste présente au début (avant : presque partie)
        assertTrue(b.fadeLevel(0.7f, 5_800, 6_000) > 0f)                // pas d'arrêt sec avant la fin
        assertEquals(0f, b.fadeLevel(0.7f, 6_000, 6_000), 0.001f)
        // 25/11 (David) : jamais la fin de la bobine sur Machine Head : elle est éteinte avant que les portes soient ouvertes
        assertTrue(b.CROSSFADE_MS < b.DOORS_MS)
        // Machine Head monte en douceur : à peine là au début, plein volume après 1,5 s
        assertTrue(AbattoirSound666.fadeGain(100, 40) < 0.02f)
        assertEquals(0.4f, AbattoirSound666.fadeGain(2_000, 40), 0.001f)
    }
}
