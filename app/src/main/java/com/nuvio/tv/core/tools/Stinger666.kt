package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * FrankenStream (15/11) : scène pendant / après le générique (les Marvel…).
 * TMDB le note avec deux mots-clés : « duringcreditsstinger » (179430) et
 * « aftercreditsstinger » (179431). Gardé en mémoire 30 jours par film.
 */
object Stinger666 {
    enum class Kind { DURING, AFTER, BOTH }

    const val DURING_ID = 179430
    const val AFTER_ID = 179431
    private const val TTL_MS = 30L * 24 * 3600 * 1000

    /** Règle pure (testée) : les mots-clés TMDB → quelle scène. */
    fun kindFromKeywords(ids: Collection<Int>, names: Collection<String> = emptyList()): Kind? {
        val n = names.map { it.lowercase() }
        val during = DURING_ID in ids || n.any { "duringcreditsstinger" in it.replace(" ", "") }
        val after = AFTER_ID in ids || n.any { "aftercreditsstinger" in it.replace(" ", "") }
        return when {
            during && after -> Kind.BOTH
            after -> Kind.AFTER
            during -> Kind.DURING
            else -> null
        }
    }

    /** Le texte affiché (fiche et lecteur). */
    fun label(k: Kind): String = when (k) {
        Kind.DURING -> "Scène pendant le générique"
        Kind.AFTER -> "Scène après le générique"
        Kind.BOTH -> "Scènes pendant et après le générique"
    }

    fun warning(k: Kind): String = when (k) {
        Kind.DURING -> "🎬 Ne coupe pas : une scène t'attend pendant le générique"
        Kind.AFTER -> "🎬 Ne coupe pas : une scène t'attend après le générique"
        Kind.BOTH -> "🎬 Ne coupe pas : des scènes t'attendent pendant et après le générique"
    }

    /** [id] = « tt… » ou « tmdb:… » d'un FILM. Null si rien (ou inconnu). */
    suspend fun forMovie(context: Context, id: String?): Kind? = withContext(Dispatchers.IO) {
        val clean = id?.trim().orEmpty()
        if (clean.isEmpty()) return@withContext null
        val key = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return@withContext null
        val prefs = context.getSharedPreferences("666tv_stinger", Context.MODE_PRIVATE)
        val cached = prefs.getString(clean, null)
        if (cached != null) {
            val (at, v) = cached.split('|', limit = 2).let { (it.getOrNull(0)?.toLongOrNull() ?: 0L) to it.getOrNull(1).orEmpty() }
            if (System.currentTimeMillis() - at < TTL_MS) return@withContext runCatching { Kind.valueOf(v) }.getOrNull()
        }
        val tmdbId = when {
            clean.startsWith("tmdb:") -> clean.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
            clean.startsWith("tt") -> Http666.get("https://api.themoviedb.org/3/find/${clean.substringBefore(':')}?external_source=imdb_id&api_key=$key", 10_000)
                ?.let { runCatching { JSONObject(it).optJSONArray("movie_results")?.optJSONObject(0)?.optInt("id") }.getOrNull() }
            else -> null
        } ?: return@withContext null
        val raw = Http666.get("https://api.themoviedb.org/3/movie/$tmdbId/keywords?api_key=$key", 10_000) ?: return@withContext null
        val kind = runCatching {
            val arr = JSONObject(raw).optJSONArray("keywords") ?: return@runCatching null
            val ids = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optInt("id") }
            val names = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
            kindFromKeywords(ids, names)
        }.getOrNull()
        prefs.edit().putString(clean, "${System.currentTimeMillis()}|${kind?.name.orEmpty()}").apply()
        kind
    }

    /**
     * Règle pure (testée) : quand prévenir dans le lecteur. Début du générique (chapitre ou IntroDB)
     * si on le connaît, sinon 8 % avant la fin (au moins 4 min, au plus 12 min).
     */
    fun warnAtMs(durationMs: Long, creditsStartMs: Long?): Long? {
        if (durationMs <= 0L) return null
        creditsStartMs?.takeIf { it in 1 until durationMs }?.let { return it }
        val lead = (durationMs * 0.08).toLong().coerceIn(4 * 60_000L, 12 * 60_000L)
        return (durationMs - lead).coerceAtLeast(0L)
    }
}
