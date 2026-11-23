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

    @Test fun `piege mise a jour - Frank se rouvre, mais jamais devant Orange TV`() {
        // 16/11 : après la mise à jour, David forçait l'arrêt et retombait sur Orange TV
        assertTrue(WakeLaunch666.shouldRelaunchAfterUpdate(fromBanner = true, wasFront = false))   // installée depuis la bannière
        assertTrue(WakeLaunch666.shouldRelaunchAfterUpdate(fromBanner = false, wasFront = true))   // poussée par l'autre Shield, Frank à l'écran
        assertFalse(WakeLaunch666.shouldRelaunchAfterUpdate(fromBanner = false, wasFront = false)) // Orange TV devant : on ne passe pas devant
    }

    // 23/11 : à chaque allumage de la TV → cinéma, portes, son ; jamais par-dessus un film ; un seul cinéma
    @Test fun `allumage TV rejoue le cinema sauf pendant un film`() {
        org.junit.Assert.assertTrue(WakeLaunch666.replayCinemaOnResume(screenWentOff = true, inPlayer = false))
        org.junit.Assert.assertFalse(WakeLaunch666.replayCinemaOnResume(screenWentOff = true, inPlayer = true))
        org.junit.Assert.assertFalse(WakeLaunch666.replayCinemaOnResume(screenWentOff = false, inPlayer = false))
    }

    @Test fun `deux reveils coup sur coup = un seul cinema`() {
        org.junit.Assert.assertTrue(WakeLaunch666.wakeAgain(0L, 1_000L))
        org.junit.Assert.assertFalse(WakeLaunch666.wakeAgain(10_000L, 12_000L))
        org.junit.Assert.assertTrue(WakeLaunch666.wakeAgain(10_000L, 16_000L))
    }
}
