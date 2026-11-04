package com.nuvio.tv.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** 666TVF (04/11) : cas pièges du clavier — curseur, effacement au milieu, rangées en boucle. */
class KeyText666Test {
    @Test fun `corriger une faute au milieu du titre puis continuer a la fin`() {
        var (q, c) = "resodent evil" to 13
        repeat(9) { c = KeyText666.left(c) }                 // curseur après « reso »
        KeyText666.backspace(q, c).let { q = it.first; c = it.second }   // efface le « o » fautif
        KeyText666.insert(q, c, "i").let { q = it.first; c = it.second }
        assertEquals("resident evil", q)
        repeat(50) { c = KeyText666.right(c, q) }            // ▶ tenu : on revient au bout
        assertEquals(q.length, c)
        KeyText666.insert(q, c, " 2").let { q = it.first; c = it.second }
        assertEquals("resident evil 2", q)
    }

    @Test fun `effacer tout en tenant, sans jamais deborder`() {
        var (q, c) = "dune" to 4
        repeat(10) { KeyText666.backspace(q, c).let { q = it.first; c = it.second } }
        assertEquals("" to 0, q to c)
        assertEquals(0, KeyText666.left(0))
    }

    @Test fun `rangee en boucle - Q gauche donne M, M droite donne Q`() {
        assertEquals(9, KeyText666.wrapLeft(0, 10))
        assertEquals(0, KeyText666.wrapRight(9, 10))
        assertEquals(4, KeyText666.wrapLeft(5, 10))
    }
}
