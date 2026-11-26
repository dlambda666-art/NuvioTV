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

    // 26/11 : StreamFusion (YGG) pour le cri, au compte-gouttes
    @org.junit.Test fun `streamfusion une question rare seulement`() {
        val h = 3600_000L
        org.junit.Assert.assertTrue(Background666.rareOk(0L, 0L, 10 * h))
        org.junit.Assert.assertFalse(Background666.rareOk(10 * h - 60_000L, 0L, 10 * h))      // 1 min après une autre
        org.junit.Assert.assertFalse(Background666.rareOk(0L, 10 * h - 2 * h, 10 * h))         // même épisode il y a 2 h
        org.junit.Assert.assertTrue(Background666.rareOk(10 * h - 11 * 60_000L, 10 * h - 5 * h, 10 * h))
        org.junit.Assert.assertTrue(Background666.takeRare("x:streamfusion666", "raw-47", 100 * h))
        org.junit.Assert.assertFalse(Background666.takeRare("x:streamfusion666", "nxt-12", 100 * h + 60_000L))
        // jamais en rafale : toujours exclu des vérifications ordinaires
        assertFalse(Background666.allowed("repo:streamfusion666"))
    }
}
