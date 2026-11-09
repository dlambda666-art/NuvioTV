package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.TmdbCollectionMediaType
import com.nuvio.tv.domain.model.TmdbCollectionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges : Frank mis en pause par Hugging Face (09/11), tuiles sans affiches / écran noir. */
class FrankFree666Test {
    private val frank = FrankFree666.FRANK_ADDON_ID
    private fun src(id: String, type: String = "movie", addon: String = frank) = AddonCatalogCollectionSource(addon, type, id)
    private fun tmdb(s: Any) = s as TmdbCollectionSource

    @Test fun `en VF cette semaine et pour toi passent sur la Shield`() {
        val week = FrankFree666.replacement(src("fs-vf-week", "series"), "FS - En VF cette semaine", "🆕") as AddonCatalogCollectionSource
        assertEquals(LocalCatalogs666.VFWEEK, week.addonId); assertEquals("series", week.type)
        val reco = FrankFree666.replacement(src("fs-pour-toi"), "Pour toi, Lambda", "Pour toi") as AddonCatalogCollectionSource
        assertEquals(LocalCatalogs666.POURTOI, reco.addonId)
    }

    @Test fun `cannibal - liste Scary Only de Frank devient TMDB horreur + mot-cle cannibale, sortis en France`() {
        val r = tmdb(FrankFree666.replacement(src("fs-ext-8"), "VF · Cannibal", "Horreur"))
        assertEquals("Cannibal", r.title)
        assertEquals("27", r.filters.withGenres)
        assertEquals("14895", r.filters.withKeywords)
        assertEquals("FR", r.filters.watchRegion)
    }

    @Test fun `nom de liste inconnu - le nom de la porte sert de repli`() {
        val r = tmdb(FrankFree666.replacement(src("fs-ext-3"), null, "Vampires"))
        assertEquals("3133", r.filters.withKeywords)
    }

    @Test fun `classiques de l'horreur - 1930 a 1990`() {
        val r = tmdb(FrankFree666.replacement(src("fs-horreur-classiques"), "Classiques de l'horreur en VF", "Horreur"))
        assertEquals("1930-01-01", r.filters.releaseDateGte); assertEquals("1990-12-31", r.filters.releaseDateLte)
    }

    @Test fun `derniers films et series French-Stream`() {
        assertEquals(TmdbCollectionMediaType.TV, tmdb(FrankFree666.replacement(src("fs-dernieres-series", "series"), "FS - Dernières Séries", "🆕")).mediaType)
        assertEquals("primary_release_date.desc", tmdb(FrankFree666.replacement(src("fs-derniers-films"), "FS - Derniers Films", "🆕")).sortBy)
    }

    @Test fun `possession demoniaque, sorcellerie, lovecraft, horreur generale`() {
        assertEquals("161261|9712|2626", tmdb(FrankFree666.replacement(src("fs-ext-11"), "VF · Possession Démoniaque", "Horreur")).filters.withKeywords)
        assertEquals("616|40931", tmdb(FrankFree666.replacement(src("fs-ext-12"), "VF · Sorcellerie", "Horreur")).filters.withKeywords)
        assertEquals("287274|215959", tmdb(FrankFree666.replacement(src("fs-ext-13"), "VF · Cosmic / Lovecraftian", "Horreur")).filters.withKeywords)
        val h = tmdb(FrankFree666.replacement(src("fs-ext-0"), "VF · Horreur", "Horreur"))
        assertEquals("27", h.filters.withGenres); assertNull(h.filters.withKeywords)
    }

    @Test fun `les autres tuiles ne bougent pas (Shudder, Top 10)`() {
        val shudder = AddonCatalogCollectionSource("aiometadata", "movie", "streaming.shudder")
        assertFalse(FrankFree666.isFrank(shudder))
        val cols = listOf(Collection(id = "c", title = "A", folders = listOf(
            CollectionFolder(id = "s", title = "⚫", sources = listOf(shudder)),
            CollectionFolder(id = "h", title = "Horreur", sources = listOf(src("fs-ext-1"), shudder))
        )))
        val plan = FrankFree666.plan(cols) { if (it.catalogId == "fs-ext-1") "VF · Slasher" else null }
        assertEquals(cols[0].folders[0], plan.collections[0].folders[0])
        val h = plan.collections[0].folders[1]
        assertEquals("12339", tmdb(h.sources[0]).filters.withKeywords)
        assertEquals(shudder, h.sources[1])
        assertEquals(1, plan.report.size)
        // deuxième passage : plus rien de Frank → rien ne change
        assertEquals(plan.collections, FrankFree666.plan(plan.collections) { null }.collections)
        assertTrue(FrankFree666.plan(plan.collections) { null }.report.isEmpty())
    }

    @Test fun `Frank via aiometadata (id prefixe) reconnu`() {
        assertTrue(FrankFree666.isFrank(AddonCatalogCollectionSource("aiometadata", "movie", "fse.fs-ext-4")))
    }
}
