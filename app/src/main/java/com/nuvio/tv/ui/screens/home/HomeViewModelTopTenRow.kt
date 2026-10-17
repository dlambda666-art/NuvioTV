package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 666TV : rangée « Top 10 VF de la semaine ».
 * Les films les plus populaires du moment parmi ceux déjà sortis en vidéo en France
 * (VOD, Blu-ray). Un film seulement au cinéma n'existe qu'en CAM/TS : il n'entre jamais.
 * Le titre contient « Top 10 », donc les grands chiffres s'affichent tout seuls.
 */
internal fun HomeViewModel.observeTopTenVfRow() {
    viewModelScope.launch {
        runCatching { profileManager.activeProfileReady.first { it } }
        delay(6_000L)
        while (true) {
            val row = runCatching { buildTopTenVfRow() }.getOrNull()
            if (row != null && row != topTenVfRow) {
                topTenVfRow = row
                scheduleUpdateCatalogRows()
            }
            delay(if (row == null) 5L * 60L * 1000L else 12L * 60L * 60L * 1000L)
        }
    }
}

internal const val TOP_TEN_VF_ROW_ADDON_ID = "666tv.top10vf"

private suspend fun HomeViewModel.buildTopTenVfRow(): CatalogRow? {
    val candidates = tmdbService.popularDigitalReleasesFr()
        .filter { !it.posterPath.isNullOrBlank() }
        .take(16)
    if (candidates.isEmpty()) return null
    val items = kotlinx.coroutines.coroutineScope {
        candidates.map { movie ->
            async {
                val imdbId = runCatching { tmdbService.tmdbToImdb(movie.id, "movie") }.getOrNull()
                    ?.takeIf { it.startsWith("tt") }
                    ?: return@async null
                MetaPreview(
                    id = imdbId,
                    type = ContentType.MOVIE,
                    name = movie.title ?: movie.originalTitle ?: return@async null,
                    poster = "https://image.tmdb.org/t/p/w500${movie.posterPath}",
                    posterShape = PosterShape.POSTER,
                    background = movie.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    logo = null,
                    description = movie.overview?.takeIf { it.isNotBlank() },
                    releaseInfo = movie.releaseDate?.take(4),
                    imdbRating = movie.voteAverage?.takeIf { it > 0 }?.toFloat(),
                    genres = emptyList()
                )
            }
        }.awaitAll()
    }.filterNotNull().distinctBy { it.id }.take(TOP_TEN_MAX_RANK)
    if (items.size < 3) return null
    return CatalogRow(
        addonId = TOP_TEN_VF_ROW_ADDON_ID,
        addonName = "666TV",
        addonBaseUrl = "",
        catalogId = "top10-vf",
        catalogName = "🔥 Top 10 VF de la semaine",
        type = ContentType.MOVIE,
        items = items,
        hasMore = false
    )
}
