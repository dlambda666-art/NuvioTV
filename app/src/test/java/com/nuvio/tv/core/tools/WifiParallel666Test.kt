package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (18/11, Shield du haut en WiFi qui rechargeait sur un fichier de 4 Go). */
class WifiParallel666Test {
    @Test fun `reglage manuel toujours prioritaire`() {
        assertNull(WifiParallel666.decide(manualNetwork = true, onWifi = true, learned = true))
        assertFalse(WifiParallel666.shouldLearn(manualNetwork = true, onWifi = true, rebuffers = 5))
    }

    @Test fun `cable - rien ne change`() {
        assertNull(WifiParallel666.decide(manualNetwork = false, onWifi = false, learned = true))
        assertFalse(WifiParallel666.shouldLearn(manualNetwork = false, onWifi = false, rebuffers = 5))
    }

    @Test fun `wifi qui recharge - 4 connexions au film suivant`() {
        assertFalse(WifiParallel666.shouldLearn(false, true, 1))
        assertTrue(WifiParallel666.shouldLearn(false, true, 2))
        assertEquals(4, WifiParallel666.decide(manualNetwork = false, onWifi = true, learned = true))
        assertNull(WifiParallel666.decide(manualNetwork = false, onWifi = true, learned = false))
    }

    @Test fun `serveur qui refuse - retour a 1`() {
        assertTrue(WifiParallel666.shouldForget(429))
        assertTrue(WifiParallel666.shouldForget(503))
        assertFalse(WifiParallel666.shouldForget(404))
        assertFalse(WifiParallel666.shouldForget(null))
    }
}
