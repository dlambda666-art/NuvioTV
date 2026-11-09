package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/** « En VF cette semaine » tenu sur la Shield (09/11). */
class LocalCatalogs666Test {
    private val day = 24L * 3600 * 1000

    @Test fun `un titre garde sa date de decouverte et part apres 7 jours`() {
        val now = 100 * day
        var list = LocalCatalogs666.mergeWeek(emptyList(), LocalCatalogs666.Entry("tt1", "movie", "Crown Jewel", null, now - 6 * day), now - 6 * day)
        list = LocalCatalogs666.mergeWeek(list, LocalCatalogs666.Entry("tt1", "movie", "Crown Jewel", "p", now), now)
        assertEquals(now - 6 * day, list.single().at)
        assertEquals("p", list.single().poster)
        list = LocalCatalogs666.mergeWeek(list, LocalCatalogs666.Entry("tt2", "series", "Monstre", null, now + 2 * day), now + 2 * day)
        assertEquals(listOf("tt2"), list.map { it.id })
    }

    @Test fun `film et serie du meme id restent separes`() {
        var list = LocalCatalogs666.mergeWeek(emptyList(), LocalCatalogs666.Entry("tt1", "movie", "A", null, 1), 1)
        list = LocalCatalogs666.mergeWeek(list, LocalCatalogs666.Entry("tt1", "series", "A", null, 2), 2)
        assertEquals(2, list.size)
    }
}
