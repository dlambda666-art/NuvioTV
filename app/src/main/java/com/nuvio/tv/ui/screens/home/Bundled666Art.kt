package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Collection
import java.text.Normalizer

/**
 * 666TV : images de tuiles intégrées à l'appli. Elles remplacent l'image de couverture
 * des tuiles reconnues par leur nom, sans dépendre d'un lien internet.
 * (Affichage seulement : les réglages des collections ne sont pas modifiés.)
 */
private data class BundledTile(val keywords: List<String>, val coverRes: Int)

private val BUNDLED_TILES = listOf(
    BundledTile(listOf("pop corn", "popcorn", "nouveaute"), R.drawable.tile_popcorn_666),
    BundledTile(listOf("coming soon", "bientot", "prochainement", "a venir"), R.drawable.tile_coming_soon_666)
)

private fun resUri(res: Int) = "android.resource://${BuildConfig.APPLICATION_ID}/$res"

private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun applyBundled666Art(collections: List<Collection>): List<Collection> =
    collections.map { collection ->
        collection.copy(
            folders = collection.folders.map { folder ->
                val name = normalize(folder.title)
                val tile = BUNDLED_TILES.firstOrNull { tile -> tile.keywords.any { name.contains(it) } }
                if (tile == null) folder else folder.copy(coverImageUrl = resUri(tile.coverRes))
            }
        )
    }
