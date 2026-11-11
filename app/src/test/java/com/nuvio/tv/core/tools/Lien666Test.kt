package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 11/11 : Mémoire 666 et enregistrements lus d'une Shield à l'autre. */
class Lien666Test {
    @Test fun `lecture a distance - les morceaux demandes par le lecteur`() {
        assertEquals(0L to 999L, Lien666.range("bytes=0-", 1000))
        assertEquals(500L to 599L, Lien666.range("bytes=500-599", 1000))
        assertEquals(900L to 999L, Lien666.range("bytes=-100", 1000))
        assertEquals(10L to 999L, Lien666.range("bytes=10-5000", 1000))
        assertNull(Lien666.range("bytes=2000-", 1000))
        assertNull(Lien666.range(null, 1000))
    }

    @Test fun `groupes partages - pas de double compte si on echange deux fois`() {
        val a = Groups666.Entry(fr = 3, other = 0, shieldFr = 1, shieldNo = 0)
        val b = Groups666.Entry(fr = 2, other = 1, shieldFr = 0, shieldNo = 2)
        val m = Groups666.merged(a, b)
        assertEquals(Groups666.Entry(3, 1, 1, 2), m)
        assertEquals(m, Groups666.merged(m, b))
    }

    @Test fun `moteur VF - orange apres 2 jours sans nouveaute`() {
        val h = 3600_000L
        assertFalse(VfCheck666.newsStale(0L, 100 * h))
        assertFalse(VfCheck666.newsStale(10 * h, 40 * h))
        assertTrue(VfCheck666.newsStale(10 * h, 60 * h))
    }

    @Test fun `Shield muettes - cloche apres 3 jours sans echange, une fois`() {
        val d = 24 * 3600_000L
        assertFalse(Lien666.silentTooLong(0L, false, 10 * d))        // jamais échangé : pas d'alerte
        assertFalse(Lien666.silentTooLong(5 * d, false, 7 * d))
        assertTrue(Lien666.silentTooLong(5 * d, false, 9 * d))
        assertFalse(Lien666.silentTooLong(5 * d, true, 9 * d))
    }
}
