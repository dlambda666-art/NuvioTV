package com.nuvio.tv.core.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 666TVF (06/11) : cas pièges — StreamFusion saturé (HTTP 429) par les vérifications en arrière-plan. */
class Background666Test {
    @Test fun `StreamFusion jamais en arriere-plan, les autres cavaliers oui`() {
        assertFalse(Background666.allowed("repo:streamfusion666"))
        assertFalse(Background666.allowed("StreamFusion666"))
        assertTrue(Background666.allowed("repo:comet666"))
        assertTrue(Background666.allowed("lumio666"))
        assertTrue(Background666.allowed("wastream666"))
    }

    @Test fun `demandes espacees - jamais deux d'affilee sans pause`() = runBlocking {
        val t0 = System.currentTimeMillis()
        Background666.paced { }
        Background666.paced { }
        assertTrue(System.currentTimeMillis() - t0 >= Background666.GAP_MS - 50)
    }
}
