package com.nuvio.tv.updater.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 24/11 : MOTHER (Alien) tape l'annonce de mise à jour. */
class Mother666Test {
    @Test fun `ligne de MOTHER`() {
        assertEquals("MISE À JOUR 2.0.8 DISPONIBLE", Mother666.line("v2.0.8"))
        assertEquals("MISE À JOUR 2.0.8 • 41 MO DISPONIBLE", Mother666.line("v2.0.8 • 41 Mo"))
    }

    @Test fun `frappe lettre par lettre puis complete`() {
        assertEquals(0, Mother666.typedCount(28, 0))
        assertEquals(10, Mother666.typedCount(28, 350))
        assertEquals(28, Mother666.typedCount(28, 60_000))
    }
}
