package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cas pièges : AHS S13 (26/10), MULTi polonais sans VF ; apprentissage repris de Frank (09/11). */
class Groups666Test {
    @Test fun `groupe lu dans le nom du fichier`() {
        assertEquals("FW", Groups666.groupOf("American.Horror.Story.S13E01.MULTi.1080p.WEB.x264-FW.mkv"))
        assertNull(Groups666.groupOf("Film.2024.1080p.WEB-x265"))
        assertNull(Groups666.groupOf("Sans groupe"))
    }

    @Test fun `la lecture l'emporte, sinon il faut une vraie majorite`() {
        assertEquals("fr", Groups666.classify(Groups666.Entry(shieldFr = 1)))
        assertEquals("other", Groups666.classify(Groups666.Entry(fr = 5, shieldNo = 1)))
        assertEquals("fr", Groups666.classify(Groups666.Entry(fr = 2)))
        assertNull(Groups666.classify(Groups666.Entry(fr = 1, other = 1)))
        assertNull(Groups666.classify(Groups666.Entry(other = 2)))
        assertEquals("other", Groups666.classify(Groups666.Entry(other = 3)))
    }

    @Test fun `apprend des noms vus, sans compter les sous-titres`() {
        Groups666.resetForTest()
        Groups666.observe(listOf(
            "Show.S01E01.TRUEFRENCH.1080p.WEB-ABC", "Show.S01E02.FRENCH.1080p.WEB-ABC",
            "Show.S01E01.MULTi.PL.1080p-XYZ", "Show.S01E02.POLISH.1080p-XYZ", "Show.S01E03.ITA.1080p-XYZ",
            "Show.S01E01.VOSTFR.1080p-SUB", "Show.S01E02.VOSTFR.1080p-SUB"
        ))
        val (fr, other) = Groups666.lists()
        assertEquals(listOf("ABC"), fr)
        assertEquals(listOf("XYZ"), other)
        Groups666.resetForTest()
    }
}
