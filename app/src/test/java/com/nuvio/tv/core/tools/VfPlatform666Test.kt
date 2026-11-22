package com.nuvio.tv.core.tools

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas piège (22/11, Psycho Killer sur Disney+ sans VF) : une VF « plateforme » récente est à confirmer par les liens. */
class VfPlatform666Test {
    private val today = LocalDate.of(2026, 11, 22)

    @Test fun `film recent sur une plateforme - a confirmer`() {
        assertTrue(VfCheck666.platformProvisional("2026-10-30", today))
        assertTrue(VfCheck666.platformProvisional("2026-06-01", today))
    }

    @Test fun `film ancien sur une plateforme - VF sure`() {
        assertFalse(VfCheck666.platformProvisional("2024-03-10", today))
        assertFalse(VfCheck666.platformProvisional("", today))
    }

    @Test fun `seule une VF plateforme a confirmer peut etre retiree`() {
        assertTrue(VfCheck666.isProvisional("DUB|1763800000000|r|p"))
        assertFalse(VfCheck666.isProvisional("DUB|1763800000000|r"))   // French-Stream ou piste française entendue
        assertFalse(VfCheck666.isProvisional("DUB|1763800000000"))      // confirmée par les liens
        assertFalse(VfCheck666.isProvisional("NONE|1763800000000|r|p"))
        assertFalse(VfCheck666.isProvisional(null))
    }
}
