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
    BundledArt("g0hvcfM4", coverRes = R.drawable.tile_popcorn_666, titleLogoRes = R.drawable.title_nouveaute_vf_666),
    BundledArt("cLGKrgcd", coverRes = R.drawable.tile_coming_soon_666),
    // Services de streaming : image néon + animation au survol.
    BundledArt("streaming-netflix", coverRes = R.drawable.tile_netflix_666, focusGifRes = R.raw.anim_netflix_666),
    BundledArt("streaming-prime-video", coverRes = R.drawable.tile_prime_666, focusGifRes = R.raw.anim_prime_666),
    BundledArt("streaming-hbo-max", coverRes = R.drawable.tile_hbo_666, focusGifRes = R.raw.anim_hbo_666),
    BundledArt("streaming-shudder", coverRes = R.drawable.tile_shudder_666, focusGifRes = R.raw.anim_shudder_666),
    BundledArt("sf00sWZ2", coverRes = R.drawable.tile_canal_666, focusGifRes = R.raw.anim_canal_666)
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
