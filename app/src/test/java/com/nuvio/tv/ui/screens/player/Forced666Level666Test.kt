package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 666TVF : cas pièges des sous-titres forcés en français et du volume constant. */
class Forced666Level666Test {

    @Test fun `pistes forcees nommees en francais ou en anglais`() {
        listOf("Forcés", "FR forcé", "French (Forced)", "FORCÉS", "VFF Forces", "forcee", "Français - forcé [PGS]", "fre.forced")
            .forEach { assertTrue(it, Forced666.isForcedName(it)) }
    }

    @Test fun `pas de faux forces`() {
        listOf("Français", "Forcené", "Workforce SDH", "French SDH", "", "Effort")
            .forEach { assertFalse(it, Forced666.isForcedName(it)) }
        assertFalse(Forced666.isForcedName(null))
    }

    @Test fun `film trop bas remonte, film trop fort baisse, sans depasser les bornes`() {
        assertEquals(9.0, Level666AudioProcessor.targetGainDb(-30.0), 1e-9)
        assertEquals(-6.0, Level666AudioProcessor.targetGainDb(-15.0), 1e-9)
        assertEquals(0.0, Level666AudioProcessor.targetGainDb(Level666AudioProcessor.TARGET_DB), 1e-9)
        assertEquals(Level666AudioProcessor.MAX_BOOST_DB, Level666AudioProcessor.targetGainDb(-60.0), 1e-9)
        assertEquals(Level666AudioProcessor.MAX_CUT_DB, Level666AudioProcessor.targetGainDb(0.0), 1e-9)
    }

    @Test fun `garde-fou - jamais de saturation, rien ne bouge sous le seuil`() {
        assertEquals(0.5, Level666AudioProcessor.softLimit(0.5), 1e-12)
        assertEquals(-0.5, Level666AudioProcessor.softLimit(-0.5), 1e-12)
        for (x in listOf(0.9, 1.0, 2.0, 10.0, 1000.0)) {
            val y = Level666AudioProcessor.softLimit(x)
            assertTrue("$x -> $y", y < 1.0 && y > Level666AudioProcessor.KNEE)
            assertEquals(-y, Level666AudioProcessor.softLimit(-x), 1e-12)
        }
        assertTrue(Level666AudioProcessor.softLimit(2.0) > Level666AudioProcessor.softLimit(1.0))
    }
}
