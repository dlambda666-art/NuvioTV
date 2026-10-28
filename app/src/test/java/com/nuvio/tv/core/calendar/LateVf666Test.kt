package com.nuvio.tv.core.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérification des galas : seulement les émissions dont la VF arrive vite, 18 h → 72 h après. */
class LateVf666Test {
    private val h = 3_600_000L

    @Test
    fun `fast series are learned from quick VF`() {
        assertTrue(isFastVf666(0, 20 * h))
        assertFalse(isFastVf666(0, 5 * 24 * h))
    }

    @Test
    fun `late alert window is 18 to 72 hours after airing`() {
        assertFalse(isLateVf666(0, 10 * h))
        assertTrue(isLateVf666(0, 20 * h))
        assertFalse(isLateVf666(0, 80 * h))
    }
}
