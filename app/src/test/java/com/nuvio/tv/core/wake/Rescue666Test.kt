package com.nuvio.tv.core.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (20/11) : retour sur Frank après une mise à jour ; film qui démarre puis YouTube revient. */
class Rescue666Test {
    private val own = "com.nuvio.tv"
    private val now = 1_000_000_000L

    @Test fun `apres une mise a jour - Frank revient, jamais devant Orange TV`() {
        assertTrue(Rescue666.shouldReturnAfterUpdate(now - 20_000, now, "com.google.android.tvlauncher", own, false))
        assertTrue(Rescue666.shouldReturnAfterUpdate(now - 20_000, now, "com.liskovsoft.smartyoutubetv", own, false))
        assertFalse(Rescue666.shouldReturnAfterUpdate(now - 20_000, now, "be.orange.tv", own, true))
        assertFalse(Rescue666.shouldReturnAfterUpdate(now - 20_000, now, own, own, false))
        assertFalse(Rescue666.shouldReturnAfterUpdate(0, now, "com.google.android.tvlauncher", own, false))
        assertFalse(Rescue666.shouldReturnAfterUpdate(now - 10 * 60_000L, now, "com.google.android.tvlauncher", own, false))
    }

    @Test fun `film qui demarre puis YouTube revient - Frank revient`() {
        assertTrue(Rescue666.shouldRescuePlayback(now - 4_000, now, "com.liskovsoft.smartyoutubetv", own, false, false))
        // film fermé normalement, ou depuis longtemps
        assertFalse(Rescue666.shouldRescuePlayback(0, now, "com.liskovsoft.smartyoutubetv", own, false, false))
        assertFalse(Rescue666.shouldRescuePlayback(now - 60_000, now, "com.liskovsoft.smartyoutubetv", own, false, false))
        // jamais devant Orange TV ni les réglages
        assertFalse(Rescue666.shouldRescuePlayback(now - 4_000, now, "be.orange.tv", own, true, false))
        assertFalse(Rescue666.shouldRescuePlayback(now - 4_000, now, "com.android.tv.settings", own, false, true))
    }

    @Test fun `raison de fermeture lisible`() {
        assertEquals("fermé par Android : manque de mémoire", ExitReason666.describe(android.app.ApplicationExitInfo.REASON_LOW_MEMORY))
        assertNull(ExitReason666.describe(android.app.ApplicationExitInfo.REASON_USER_REQUESTED))
    }
}
