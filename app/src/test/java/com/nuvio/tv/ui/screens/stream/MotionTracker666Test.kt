package com.nuvio.tv.ui.screens.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 24/11 : le détecteur de mouvement (Alien) pendant la recherche des liens. */
class MotionTracker666Test {
    @Test fun `le bip accelere quand les cavaliers repondent`() {
        assertEquals(1_400L, MotionTracker666.beepIntervalMs(0, 4))
        assertTrue(MotionTracker666.beepIntervalMs(2, 4) < MotionTracker666.beepIntervalMs(1, 4))
        assertEquals(350L, MotionTracker666.beepIntervalMs(4, 4))
        assertEquals(1_400L, MotionTracker666.beepIntervalMs(0, 0))
    }

    @Test fun `la distance se rapproche`() {
        assertEquals(20, MotionTracker666.distanceMeters(0, 4))
        assertEquals(2, MotionTracker666.distanceMeters(4, 4))
        assertTrue(MotionTracker666.distanceMeters(2, 4) in 3..19)
    }
}
