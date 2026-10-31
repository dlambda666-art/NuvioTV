package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 666TVF : le fond abattoir (30/10). */
class Abattoir666Test {
    @Test fun `le neon est allume presque tout le temps, et clignote`() {
        var off = 0
        val n = 9000
        for (i in 0 until n) if (Abattoir666.neonOff(i / 1000.0) > 0f) off++
        assertTrue("éteint ${off * 100 / n} % du temps", off in 1..(n / 10))
        assertEquals(0f, Abattoir666.neonOff(1.0))
        assertEquals(1f, Abattoir666.neonOff(6.1))
        assertEquals(0f, Abattoir666.neonOff(6.23)) // le flash du starter
        assertEquals(1f, Abattoir666.neonOff(9.0 + 6.1), 0f)
    }

    @Test fun `chaque goutte se forme, tombe, puis eclabousse au sol - jamais sous le sol`() {
        Abattoir666.SOURCES.indices.forEach { i ->
            var formed = false; var fell = false; var splashed = false
            var t = 0.0
            while (t < 12.0) {
                when (val d = Abattoir666.state(i, t)) {
                    is Abattoir666.Drop.Forming -> { formed = true; assertTrue(d.len >= 0f && d.r > 0f) }
                    is Abattoir666.Drop.Falling -> { fell = true; assertTrue(d.y < Abattoir666.SOURCES[i].y1 + 11) }
                    is Abattoir666.Drop.Splash -> { splashed = true; assertTrue(d.k in 0f..1f) }
                }
                t += 0.01
            }
            assertTrue("goutte $i", formed && fell && splashed)
        }
    }

    @Test fun `les gouttes visqueuses filent plus long avant de lacher`() {
        val visc = Abattoir666.SOURCES.indices.filter { Abattoir666.isViscous(it) }
        val normal = Abattoir666.SOURCES.indices.filterNot { Abattoir666.isViscous(it) }
        fun maxLen(i: Int): Float {
            var m = 0f; var t = 0.0
            while (t < 12.0) { (Abattoir666.state(i, t) as? Abattoir666.Drop.Forming)?.let { m = maxOf(m, it.len) }; t += 0.01 }
            return m
        }
        assertTrue(visc.isNotEmpty())
        assertTrue(visc.minOf { maxLen(it) } > normal.maxOf { maxLen(it) })
    }
}

class Abattoir666NeonTest {
    @Test fun `porte - le neon gresille puis meurt et reste eteint`() {
        var saw0 = false; var saw1 = false
        for (ms in 0L until Abattoir666.DOOR_DIE_MS step 10) {
            val l = Abattoir666.neonLevel(1.0, -1, ms); if (l == 0f) saw0 = true; if (l == 1f) saw1 = true
        }
        assertTrue(saw0 && saw1)
        assertEquals(1f, Abattoir666.neonLevel(1.0, -1, 2_000))
        assertEquals(1f, Abattoir666.neonLevel(1.0, 50, 2_000)) // la porte passe avant la cloche
    }

    @Test fun `cloche - coupe une seconde puis revient au clignotement normal`() {
        assertEquals(1f, Abattoir666.neonLevel(1.0, 50, -1))
        assertEquals(0f, Abattoir666.neonLevel(1.0, 150, -1))
        assertEquals(1f, Abattoir666.neonLevel(1.0, 800, -1))
        assertEquals(0f, Abattoir666.neonLevel(1.0, 1_500, -1))
    }

    @Test fun `bzzt - une grosse coupure sur trois seulement`() {
        assertTrue(Abattoir666.isBigCutStart(5.98, 6.01))
        assertTrue(!Abattoir666.isBigCutStart(6.01, 6.03))
        val cycles = (0 until 9).count { c -> Abattoir666.bigCutBuzzes(c * 9.0 + 6.1) }
        assertEquals(3, cycles)
    }
}
