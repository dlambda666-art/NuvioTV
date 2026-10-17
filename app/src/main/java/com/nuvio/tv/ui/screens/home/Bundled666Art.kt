package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Collection

/**
 * 666TV : images de tuiles intégrées à l'appli. Chaque tuile est reconnue par le lien de
 * son image actuelle et reçoit l'image embarquée : plus de dépendance à internet.
 * (Affichage seulement : les réglages des collections ne sont pas modifiés.)
 */
private data class BundledArt(
    /** Morceau unique du lien d'image d'origine (couverture). */
    val coverUrlKey: String,
    val coverRes: Int? = null,
    val focusGifRes: Int? = null,
    val titleLogoRes: Int? = null
)

private val BUNDLED_ART = listOf(
    BundledArt("PrRXbjxT", coverRes = R.drawable.tile_horreur_666),
    BundledArt("FKYdvQR5", coverRes = R.drawable.tile_frankenstream_666),
    BundledArt("g0hvcfM4", coverRes = R.drawable.tile_popcorn_666),
    BundledArt("cLGKrgcd", coverRes = R.drawable.tile_coming_soon_666)
)

private fun resUri(res: Int) = "android.resource://${BuildConfig.APPLICATION_ID}/$res"

internal fun applyBundled666Art(collections: List<Collection>): List<Collection> =
    collections.map { collection ->
        collection.copy(
            folders = collection.folders.map { folder ->
                val cover = folder.coverImageUrl.orEmpty()
                val art = BUNDLED_ART.firstOrNull { cover.contains(it.coverUrlKey) }
                if (art == null) folder else folder.copy(
                    coverImageUrl = art.coverRes?.let(::resUri) ?: folder.coverImageUrl,
                    focusGifUrl = art.focusGifRes?.let(::resUri) ?: folder.focusGifUrl,
                    titleLogoUrl = art.titleLogoRes?.let(::resUri) ?: folder.titleLogoUrl
                )
            }
        )
    }
