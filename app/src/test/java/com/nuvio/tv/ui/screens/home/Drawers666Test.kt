package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La morgue (07/11) : murs de 8 tiroirs, étiquettes SxxEyy. */
class Drawers666Test {
    @Test fun `8 tiroirs par mur`() {
        assertEquals(1, Drawers666.walls(0)); assertEquals(1, Drawers666.walls(8))
        assertEquals(2, Drawers666.walls(9)); assertEquals(3, Drawers666.walls(17))
    }

    @Test fun `etiquette SxxEyy, rien pour un film`() {
        assertEquals("S03E06", Drawers666.code(3, 6)); assertEquals("S28E45", Drawers666.code(28, 45))
        assertEquals("", Drawers666.code(null, null)); assertEquals("", Drawers666.code(0, 1))
    }

    @Test fun `les portes ne se chevauchent pas et restent dans l'ecran`() {
        val cols = Drawers666.COLS; val rows = Drawers666.ROWS
        assertEquals(4, cols.size); assertEquals(2, rows.size)
        (cols + rows).forEach { (a, b) -> assertTrue(a in 0f..1f && b in 0f..1f && a < b) }
        cols.zipWithNext().forEach { (l, r) -> assertTrue(l.second < r.first) }
        rows.zipWithNext().forEach { (t, b) -> assertTrue(t.second < b.first) }
    }
}
