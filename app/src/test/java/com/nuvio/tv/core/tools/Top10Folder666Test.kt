package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 666TVF : la tuile TOP 10 (31/10). */
class Top10Folder666Test {
    private fun f(id: String, title: String, cover: String? = null) = CollectionFolder(id = id, title = title, coverImageUrl = cover)

    @Test fun `entre Shudder et Prochainement, une seule fois`() {
        val row = Collection(id = "r1", title = "666", folders = listOf(f("a", "En VF cette semaine"), f("b", "Pour toi, Lambda"), f("c", "Shudder", "x/streaming-shudder.png"), f("d", "Prochainement")))
        val other = Collection(id = "r2", title = "Horreur", folders = listOf(f("h", "Horreur")))
        val out = Top10Folder666.withTop10(listOf(row, other))!!
        assertEquals(listOf("a", "b", "c", Top10Folder666.ID, "d"), out[0].folders.map { it.id })
        assertEquals(other, out[1])
        assertNull(Top10Folder666.withTop10(out))
    }

    @Test fun `sans Shudder, juste avant Prochainement`() {
        val row = Collection(id = "r1", title = "666", folders = listOf(f("a", "Pour toi"), f("d", "Coming soon")))
        assertEquals(listOf("a", Top10Folder666.ID, "d"), Top10Folder666.withTop10(listOf(row))!![0].folders.map { it.id })
    }

    @Test fun `rien a faire si la 1re rangee est introuvable`() {
        assertNull(Top10Folder666.withTop10(listOf(Collection(id = "r", title = "x", folders = listOf(f("z", "Zombies"))))))
    }

    @Test fun `les deux Top 10 dedans, films puis series`() {
        val src = Top10Folder666.folder(null).sources.map { (it as com.nuvio.tv.domain.model.AddonCatalogCollectionSource).addonId }
        assertEquals(listOf(Top10Folder666.FILMS_ADDON, Top10Folder666.SERIES_ADDON), src)
    }
}
