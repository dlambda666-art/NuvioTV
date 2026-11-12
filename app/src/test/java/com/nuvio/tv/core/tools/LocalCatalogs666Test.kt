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

    // 11/11 piège : Malfaisante vue dans la tuile puis disparue (sortie du flux French-Stream au passage suivant)
    @Test fun `un titre trouve reste 30 jours meme s'il n'est plus dans les sources`() {
        val now = 100 * day
        val old = listOf(LocalCatalogs666.Entry("tt15140973", "movie", "Malfaisante", null, now - 3 * day))
        val fresh = listOf(LocalCatalogs666.Entry("tt1", "movie", "Autre", null, now))
        val m = LocalCatalogs666.mergeSeed(old, fresh, now)
        assertEquals(listOf("tt1", "tt15140973"), m.map { it.id })
        val again = LocalCatalogs666.mergeSeed(m, listOf(LocalCatalogs666.Entry("tt15140973", "movie", "Malfaisante", null, now)), now)
        assertEquals(now - 3 * day, again.first { it.id == "tt15140973" }.at)
        assertEquals(emptyList<String>(), LocalCatalogs666.mergeSeed(old, emptyList(), now + 40 * day).map { it.id })
    }

    // 12/11 piège : Scream 7 et Marty Supreme (VF trouvée cette semaine, sortis il y a des mois) ne s'enregistrent pas
    @Test fun `nouveaute - sortie depuis moins de 3 mois seulement`() {
        val today = java.time.LocalDate.parse("2026-11-12")
        assertTrue(LocalCatalogs666.isRecentRelease(java.time.LocalDate.parse("2026-10-07"), today))    // Malfaisante
        assertFalse(LocalCatalogs666.isRecentRelease(java.time.LocalDate.parse("2026-02-27"), today))   // Scream 7
        assertFalse(LocalCatalogs666.isRecentRelease(java.time.LocalDate.parse("2025-12-25"), today))   // Marty Supreme
        assertFalse(LocalCatalogs666.isRecentRelease(null, today))
        assertFalse(LocalCatalogs666.isRecentRelease(java.time.LocalDate.parse("2026-12-01"), today))   // pas encore sorti
    }
}
