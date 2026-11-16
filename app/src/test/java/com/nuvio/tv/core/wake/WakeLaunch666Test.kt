package com.nuvio.tv.core.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 16/11 : après une coupure de courant, la Shield restait sur l'accueil Nvidia ; on repart de l'Abattoir. */
class WakeLaunch666Test {
    @Test fun `piege coupure de courant - on reessaie d'ouvrir pendant la premiere minute`() {
        assertEquals(listOf(5_000L, 15_000L, 30_000L, 60_000L), WakeLaunch666.BOOT_RETRIES_MS)
        assertTrue(WakeLaunch666.shouldRetryBoot(resumedOnce = false, resumedNow = false))
    }

    @Test fun `deja affiche - on ne relance plus (on ne tire pas Orange TV de sous les pieds)`() {
        assertFalse(WakeLaunch666.shouldRetryBoot(resumedOnce = true, resumedNow = false))
        assertFalse(WakeLaunch666.shouldRetryBoot(resumedOnce = false, resumedNow = true))
    }

    @Test fun `ouverture sur l'Abattoir, jamais pendant un film`() {
        assertTrue(WakeLaunch666.shouldOpenAbattoir(settingOn = true, inPlayer = false))
        assertFalse(WakeLaunch666.shouldOpenAbattoir(settingOn = true, inPlayer = true))
        assertFalse(WakeLaunch666.shouldOpenAbattoir(settingOn = false, inPlayer = false))
    }
}
