package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas piège 28/10 (Affection) : seule une VF sûre est signalée à Frank. */
class FrankVf666Test {
    @Test
    fun `only sure VF releases are reported`() {
        assertTrue(FrankVf666.isSureVfRelease("Affection.2025.TRUEFRENCH.1080p.WEB.H264"))
        assertTrue(FrankVf666.isSureVfRelease("Affection 2025 VFF 2160p"))
        assertFalse(FrankVf666.isSureVfRelease("Affection.2025.MULTi.1080p (VF ?)"))
        assertFalse(FrankVf666.isSureVfRelease("Affection.2025.VOSTFR.1080p"))
        assertFalse(FrankVf666.isSureVfRelease("Affection.2025.1080p.WEBRip"))
    }

    @Test
    fun `frank id is the imdb id only`() {
        assertEquals("tt33249097", FrankVf666.frankId("tt33249097"))
        assertEquals("tt0185103", FrankVf666.frankId("tt0185103:2026:43"))
        assertNull(FrankVf666.frankId("tmdb:1339210"))
    }
}
