package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Son666Test {
    @Test fun `seulement les 10 premieres minutes d'un film`() {
        assertFalse(Son666.inWindow(0L, 5_000L))                       // aucun film lancé
        assertTrue(Son666.inWindow(1_000L, 1_000L + 9 * 60_000L))
        assertFalse(Son666.inWindow(1_000L, 1_000L + 11 * 60_000L))
    }

    @Test fun `ligne lisible et journal limite`() {
        assertEquals("26/11 22:50:03 · +12 s · COUPURE", Son666.line("26/11 22:50:03", 12_400L, "COUPURE"))
        assertEquals(listOf("b", "c"), Son666.keepLast(listOf("a", "b", "c"), 2))
        assertEquals("AC-3 (Dolby)", Son666.encodingName(5))
        assertEquals("E-AC-3 (Dolby+)", Son666.encodingName(6))
    }
}
