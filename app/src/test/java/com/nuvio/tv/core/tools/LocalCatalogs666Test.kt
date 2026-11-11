package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/** « En VF cette semaine » tenu sur la Shield (09/11). */
class LocalCatalogs666Test {
    private val day = 24L * 3600 * 1000

    @Test fun `un titre garde sa date de decouverte et part apres 30 jours`() {
        val now = 100 * day
        var list = LocalCatalogs666.mergeWeek(emptyList(), LocalCatalogs666.Entry("tt1", "movie", "Crown Jewel", null, now - 6 * day), now - 6 * day)
        list = LocalCatalogs666.mergeWeek(list, LocalCatalogs666.Entry("tt1", "movie", "Crown Jewel", "p", now), now)
        assertEquals(now - 6 * day, list.single().at)
        assertEquals("p", list.single().poster)
        list = LocalCatalogs666.mergeWeek(list, LocalCatalogs666.Entry("tt2", "series", "Monstre", null, now + 25 * day), now + 25 * day)
        assertEquals(listOf("tt2"), list.map { it.id })
    }

    @Test fun `film et serie du meme id restent separes`() {
        var list = LocalCatalogs666.mergeWeek(emptyList(), LocalCatalogs666.Entry("tt1", "movie", "A", null, 1), 1)
        list = LocalCatalogs666.mergeWeek(list, LocalCatalogs666.Entry("tt1", "series", "A", null, 2), 2)
        assertEquals(2, list.size)
    }

    // 11/11 piège : L'Odyssée enregistrée mais absente de « En VF cette semaine »
    @Test fun `ce qui est enregistre depuis moins de 30 jours entre dans la tuile`() {
        val now = 100 * day
        val recs = listOf(
            LocalCatalogs666.RecLite("tt33764258", "movie", "L'Odyssée", "p", now - 12 * day, null),
            LocalCatalogs666.RecLite("tt0000001:1:2", "series", "Raw", null, now - day, 1),
            LocalCatalogs666.RecLite("tt0000002", "movie", "Vieux", null, now - 40 * day, null)
        )
        assertEquals(listOf("tt33764258"), LocalCatalogs666.recordedEntries(recs, "movie", now).map { it.id })
        assertEquals(listOf("tt0000001"), LocalCatalogs666.recordedEntries(recs, "series", now).map { it.id })
    }
}
