package com.nuvio.tv.core.tools

import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder

/**
 * 666TVF (31/10) : la tuile « TOP 10 » de la 1re rangée (entre Shudder et Prochainement).
 * Dedans : Top 10 films VF et Top 10 séries VF (calculés par l'accueil), qui quittent l'accueil.
 */
object Top10Folder666 {
    const val ID = "666top10"
    const val COVER = "666art:top10"
    const val FILMS_ADDON = "666tv.top10vf"
    const val SERIES_ADDON = "666tv.top10series"

    /** Les deux Top 10, posés par l'accueil dès qu'ils sont prêts ; lus par la page de la tuile. */
    @Volatile var films: CatalogRow? = null
    @Volatile var series: CatalogRow? = null

    fun isTop10Source(addonId: String) = addonId == FILMS_ADDON || addonId == SERIES_ADDON
    fun rowFor(addonId: String): CatalogRow? = if (addonId == FILMS_ADDON) films else if (addonId == SERIES_ADDON) series else null

    fun isTop10(f: CollectionFolder) = f.id == ID || f.coverImageUrl == COVER

    fun folder(like: CollectionFolder?): CollectionFolder = CollectionFolder(
        id = ID,
        title = "Top 10",
        coverImageUrl = COVER,
        tileShape = like?.tileShape ?: com.nuvio.tv.domain.model.PosterShape.LANDSCAPE,
        hideTitle = like?.hideTitle ?: false,
        sources = listOf(
            AddonCatalogCollectionSource(addonId = FILMS_ADDON, type = "movie", catalogId = "top10-vf"),
            AddonCatalogCollectionSource(addonId = SERIES_ADDON, type = "series", catalogId = "top10-series-vf")
        )
    )

    /**
     * Règle pure (testée) : ajoute la tuile juste après Shudder (sinon avant Prochainement),
     * dans la rangée qui les contient. Null si elle existe déjà ou s'il n'y a pas où la mettre.
     */
    fun withTop10(collections: List<Collection>): List<Collection>? {
        if (collections.any { c -> c.folders.any(::isTop10) }) return null
        val idx = collections.indexOfFirst { c ->
            c.folders.any { HomeReorg666.isShudder(it) || HomeReorg666.isComingSoon(it) }
        }
        if (idx < 0) return null
        val c = collections[idx]
        val shudder = c.folders.indexOfFirst { HomeReorg666.isShudder(it) }
        val coming = c.folders.indexOfFirst { HomeReorg666.isComingSoon(it) }
        val at = if (shudder >= 0) shudder + 1 else coming
        val like = c.folders.getOrNull(if (shudder >= 0) shudder else coming)
        val folders = c.folders.toMutableList().apply { add(at, folder(like)) }
        return collections.toMutableList().apply { this[idx] = c.copy(folders = folders) }
    }

    suspend fun ensure(store: CollectionsDataStore) {
        val current = runCatching { store.getCurrentCollections() }.getOrNull() ?: return
        val next = withTop10(current) ?: return
        runCatching { store.setCollections(next) }
    }
}
