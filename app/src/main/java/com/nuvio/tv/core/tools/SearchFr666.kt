package com.nuvio.tv.core.tools

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogExtra
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 666TVF (11/11) : la recherche en français, intégrée (avant : aiometa, TMDB en français).
 * Cinemeta ne connaît que les titres anglais / originaux : « L'Odyssée », « Les Dents de la mer »
 * ne donnaient rien. TMDB cherche dans les titres français, originaux et alternatifs.
 * Apparaît en tête des résultats, comme un addon (rien à installer, rien d'hébergé).
 */
object SearchFr666 {
    const val ID = "666tv.searchfr"
    const val BASE = "https://666tv.searchfr.local"

    fun isMine(baseUrl: String) = baseUrl == BASE

    private fun catalog(type: ContentType, id: String, name: String) = CatalogDescriptor(
        type = type, id = id, name = name,
        extra = listOf(CatalogExtra(name = "search", isRequired = true))
    )

    val addon: Addon = Addon(
        id = ID, name = "Recherche en français", version = "1.0.0", description = "TMDB en français, sur la Shield",
        logo = null, baseUrl = BASE,
        catalogs = listOf(catalog(ContentType.MOVIE, "fr-films", "Films (titres français)"), catalog(ContentType.SERIES, "fr-series", "Séries (titres français)")),
        types = listOf(ContentType.MOVIE, ContentType.SERIES), resources = emptyList()
    )

    /** Règle pure (testée) : une réponse de /search/{movie|tv} → fiches (les plus connues d'abord, sans les inconnues). */
    fun parse(json: JSONObject?, series: Boolean): List<MetaPreview> {
        val arr = json?.optJSONArray("results") ?: return emptyList()
        val t = if (series) "series" else "movie"
        val ct = if (series) ContentType.SERIES else ContentType.MOVIE
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
            val poster = o.optString("poster_path").takeIf { it.startsWith("/") } ?: return@mapNotNull null
            val date = o.optString(if (series) "first_air_date" else "release_date")
            MetaPreview(
                id = "tmdb:$id", type = ct, rawType = t,
                name = o.optString(if (series) "name" else "title").ifBlank { o.optString(if (series) "original_name" else "original_title") },
                poster = "https://image.tmdb.org/t/p/w342$poster",
                posterShape = PosterShape.POSTER,
                background = o.optString("backdrop_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w1280$it" },
                logo = null, description = o.optString("overview").ifBlank { null },
                releaseInfo = date.take(4).ifBlank { null }, imdbRating = null, genres = emptyList(), released = date.ifBlank { null }
            )
        }
    }

    suspend fun search(type: String, query: String): CatalogRow? = withContext(Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return@withContext null
        val series = type == "series" || type == "tv"
        val kind = if (series) "tv" else "movie"
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val items = (1..2).flatMap { page ->
            val raw = Http666.get("https://api.themoviedb.org/3/search/$kind?language=fr-FR&include_adult=false&query=$q&page=$page&api_key=$key", 10_000)
            parse(raw?.let { runCatching { JSONObject(it) }.getOrNull() }, series)
        }.distinctBy { it.id }
        val ct = if (series) ContentType.SERIES else ContentType.MOVIE
        CatalogRow(
            addonId = ID, addonName = addon.name, addonBaseUrl = BASE,
            catalogId = if (series) "fr-series" else "fr-films",
            catalogName = if (series) "Séries (titres français)" else "Films (titres français)",
            type = ct, rawType = if (series) "series" else "movie", items = items, isLoading = false, hasMore = false,
            extraArgs = mapOf("search" to query)
        )
    }
}
