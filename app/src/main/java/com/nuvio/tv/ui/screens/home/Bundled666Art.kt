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
    val titleLogoRes: Int? = null,
    /** Ou bien : nom de la tuile (sans accents ni majuscules), quand l'image d'origine n'est pas connue. */
    val titleKey: String? = null,
    /** 666TVF : le nom doit être exactement celui-là (portes de l'horreur). */
    val exact: Boolean = false
)

/** 666TVF : les portes de l'horreur — même image (la porte qui s'ouvre), un titre par sous-genre. */
private val PORTES_666 = listOf(
    "horreur" to R.drawable.title_horreur_666,
    "classiques" to R.drawable.title_classiques_666,
    "slasher" to R.drawable.title_slasher_666,
    "les plus effrayants" to R.drawable.title_effrayants_666,
    "creatures" to R.drawable.title_creatures_666,
    "vampires" to R.drawable.title_vampires_666,
    "loups-garous" to R.drawable.title_loups_garous_666,
    "zombies" to R.drawable.title_zombies_666,
    "cannibales" to R.drawable.title_cannibales_666,
    "body horror" to R.drawable.title_body_horror_666,
    "maisons hantees" to R.drawable.title_maisons_hantees_666,
    "possessions" to R.drawable.title_possessions_666,
    "sorcellerie" to R.drawable.title_sorcellerie_666,
    "horreur cosmique" to R.drawable.title_cosmique_666
).map { (key, title) -> BundledArt("\u0000", coverRes = R.drawable.tile_porte_666, focusGifRes = R.raw.anim_porte_666, titleLogoRes = title, titleKey = key, exact = true) }

private val BUNDLED_ART = listOf(
    BundledArt("PrRXbjxT", coverRes = R.drawable.tile_horreur_666),
    BundledArt("FKYdvQR5", coverRes = R.drawable.tile_frankenstream_666),
    BundledArt("g0hvcfM4", coverRes = R.drawable.tile_popcorn_666, titleLogoRes = R.drawable.title_nouveaute_vf_666),
    BundledArt(com.nuvio.tv.core.tools.Top10Folder666.COVER, coverRes = R.drawable.tile_top10_666, titleLogoRes = R.drawable.title_top10_666),
    BundledArt("cLGKrgcd", coverRes = R.drawable.tile_coming_soon_666, titleLogoRes = R.drawable.title_prochainement_666),
    BundledArt("\u0000", coverRes = R.drawable.tile_pour_toi_666, titleLogoRes = R.drawable.title_pour_toi_666, titleKey = "pour toi"),
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
                val name = java.text.Normalizer.normalize(folder.title, java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "").lowercase()
                // 666TVF : portes de l'horreur d'abord (nom exact), puis les images connues
                val art = (if (cover == com.nuvio.tv.core.tools.HomeReorg666.PORTE_COVER || cover.contains("PrRXbjxT"))
                    PORTES_666.firstOrNull { it.titleKey == name.trim() } else null)
                    ?: BUNDLED_ART.firstOrNull { cover.contains(it.coverUrlKey) }
                    ?: BUNDLED_ART.firstOrNull { it.titleKey != null && !it.exact && name.contains(it.titleKey) }
                if (art == null) folder else folder.copy(
                    coverImageUrl = art.coverRes?.let(::resUri) ?: folder.coverImageUrl,
                    focusGifUrl = art.focusGifRes?.let(::resUri) ?: folder.focusGifUrl,
                    // 666TVF : les portes s'ouvrent toujours au survol
                    focusGifEnabled = if (art.focusGifRes != null) true else folder.focusGifEnabled,
                    titleLogoUrl = art.titleLogoRes?.let(::resUri) ?: folder.titleLogoUrl
                )
            }
        )
    }
