package com.nuvio.tv.core.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 16/11 : toujours revenir à FrankenStream (accueil Nvidia après Orange TV, arrêt forcé, coupure de courant). */
class Return666Test {
    private val own = "com.nuvio.tv"
    private val homes = Return666.KNOWN_HOMES

    @Test fun `piege accueil Nvidia apres Orange TV - on revient sur FrankenStream`() {
        assertTrue(Return666.shouldReturn("com.google.android.tvlauncher", homes, own, screenOn = true, sinceLastReturnMs = -1))
        assertTrue(Return666.shouldReturn("com.google.android.apps.tv.launcherx", homes, own, true, 60_000))
    }

    @Test fun `Orange TV, Kodi, un film - on ne touche a rien`() {
        listOf("com.orange.tv", "org.xbmc.kodi", "com.google.android.youtube.tv", own).forEach {
            assertFalse(it, Return666.shouldReturn(it, homes, own, true, -1))
        }
    }

    @Test fun `ecran eteint ou reglages - on ne touche a rien`() {
        assertFalse(Return666.shouldReturn("com.google.android.tvlauncher", homes, own, screenOn = false, sinceLastReturnMs = -1))
        assertFalse(Return666.shouldReturn("com.android.tv.settings", homes + "com.android.tv.settings", own, true, -1))
    }

    @Test fun `deux appuis sur Accueil en moins de 10 s - on reste sur l'accueil Nvidia`() {
        assertFalse(Return666.shouldReturn("com.google.android.tvlauncher", homes, own, true, 4_000))
        assertTrue(Return666.shouldReturn("com.google.android.tvlauncher", homes, own, true, 10_000))
    }

    @Test fun `service reconnu dans la liste d'Android, forme longue ou courte`() {
        val cls = "com.nuvio.tv.core.wake.Return666Service"
        assertTrue(Return666.isListed("a/b:com.nuvio.tv/com.nuvio.tv.core.wake.Return666Service", own, cls))
        assertTrue(Return666.isListed("com.nuvio.tv/.core.wake.Return666Service", own, cls))
        assertFalse(Return666.isListed("", own, cls))
        assertFalse(Return666.isListed("com.other/.Service", own, cls))
    }

    @Test fun `activation - ajoute sans effacer les autres services ni doublon`() {
        val c = "com.nuvio.tv/com.nuvio.tv.core.wake.Return666Service"
        assertEquals(c, Return666.withService(null, c))
        assertEquals("x/y:$c", Return666.withService("x/y", c))
        assertEquals("x/y:$c", Return666.withService("x/y:$c", c))
    }

    @Test fun `piege Orange TV se declare accueil - on ne la prend jamais pour l'accueil Nvidia`() {
        // 17/11 : impossible d'entrer dans Orange TV, Frank revenait aussitôt (Orange TV déclare un « accueil »)
        val h = Return666.homesFrom(defaultHome = "com.google.android.tvlauncher", own = own)
        assertFalse(Return666.shouldReturn("be.orange.tv", h, own, true, -1))
        assertFalse(Return666.shouldReturn("com.orange.owtv.tv", h, own, true, -1))
        assertTrue(Return666.shouldReturn("com.google.android.tvlauncher", h, own, true, -1))
        // même si Android a Orange TV comme accueil par défaut, il n'est pas retenu
        assertFalse("be.orange.tv" in Return666.homesFrom("be.orange.tv", own))
        assertFalse(own in Return666.homesFrom(own, own))
    }
}
