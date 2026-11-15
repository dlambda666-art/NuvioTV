package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Stinger666Test {
    @Test fun `piege Marvel - scene apres le generique reperee par les mots-cles TMDB`() {
        assertEquals(Stinger666.Kind.AFTER, Stinger666.kindFromKeywords(listOf(9715, 179431)))
        assertEquals(Stinger666.Kind.DURING, Stinger666.kindFromKeywords(listOf(179430)))
        assertEquals(Stinger666.Kind.BOTH, Stinger666.kindFromKeywords(listOf(179430, 179431)))
        assertEquals(Stinger666.Kind.AFTER, Stinger666.kindFromKeywords(emptyList(), listOf("aftercreditsstinger")))
        assertNull(Stinger666.kindFromKeywords(listOf(9715)))
    }

    @Test fun `prevenir au debut du generique, sinon un peu avant la fin`() {
        val d = 120L * 60_000L
        assertEquals(110L * 60_000L, Stinger666.warnAtMs(d, 110L * 60_000L))
        assertEquals(d - (d * 0.08).toLong(), Stinger666.warnAtMs(d, null))
        assertEquals(60L * 60_000L - 4 * 60_000L, Stinger666.warnAtMs(60L * 60_000L, null))
        assertNull(Stinger666.warnAtMs(0L, null))
    }
}
