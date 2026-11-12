package com.nuvio.tv.core.tools

import android.content.Context
import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 666TVF (09/11) : les catalogues de Frank, refaits sur la Shield (Frank a été mis en pause par
 * Hugging Face ; plus d'hébergement du tout).
 *  - « En VF cette semaine » (films / séries) : ce que le guetteur et la cloche ont trouvé en VF
 *    1080p / 4K ces 7 derniers jours (jamais de CAM, jamais de 720p : mêmes règles que le cri) ;
 *  - « Pour toi, Lambda » : recommandations TMDB d'après ce que tu as vraiment regardé, sans le
 *    déjà-vu, seulement des titres sortis en France (donc distribués en VF).
 * Tout reste sur la Shield ; seul TMDB est interrogé (avec la clé TMDB de l'appli).
 */
object LocalCatalogs666 {
    const val VFWEEK = "666tv.vfweek"
    const val POURTOI = "666tv.pourtoi"
    const val UPCOMING = "666tv.upcoming"
    private const val TAG = "LocalCatalogs666"
    private const val PREFS = "666tv_local_catalogs"
    // 11/11 : 30 jours (David : remplace aussi « Derniers films / séries »)
    private const val WEEK_MS = 30L * 24 * 3600 * 1000
    private const val POURTOI_EVERY_MS = 3L * 3600 * 1000

    @Volatile private var ctx: Context? = null
    fun init(context: Context) { if (ctx == null) ctx = context.applicationContext }
    private fun prefs() = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isLocal(addonId: String) = addonId == VFWEEK || addonId == POURTOI || addonId == UPCOMING

    fun nameFor(addonId: String, type: String): String? = when (addonId) {
        VFWEEK -> if (type == "series") "Séries en VF cette semaine" else "En VF cette semaine"
        POURTOI -> "Pour toi, Lambda"
        UPCOMING -> if (type == "series") "Séries à venir" else "Prochainement"
        else -> null
    }

    // ---------------- En VF cette semaine ----------------

    data class Entry(val id: String, val type: String, val name: String, val poster: String?, val at: Long)

    /** Le guetteur / la cloche ont trouvé ce titre en VF sûre (1080p / 4K). */
    fun recordVf(id: String, type: String, name: String, poster: String?, now: Long = System.currentTimeMillis()) {
        val p = prefs() ?: return
        if (!id.startsWith("tt")) return
        VfCheck666.confirm(id)   // trouvé en VF par le guetteur / la cloche : VF pour de bon
        val t = if (type == "series" || type == "tv") "series" else "movie"
        synchronized(this) {
            val list = mergeWeek(readWeek(), Entry(id, t, name, poster, now), now)
            p.edit().putString("vfweek", writeEntries(list)).apply()
        }
    }

    /** Règle pure (testée) : l'ancienne liste + la nouvelle, sans doublon, la date de 1re découverte gardée, 30 jours max. */
    fun mergeSeed(old: List<Entry>, fresh: List<Entry>, now: Long): List<Entry> {
        val byId = LinkedHashMap<String, Entry>()
        (old + fresh).forEach { e -> byId[e.id] = byId[e.id]?.let { o -> e.copy(at = minOf(o.at, e.at)) } ?: e }
        return byId.values.filter { now - it.at < WEEK_MS }.sortedByDescending { it.at }.take(200)
    }

    data class RecLite(val contentId: String?, val contentType: String?, val name: String, val poster: String?, val at: Long, val season: Int?)

    /** Règle pure (testée) : les enregistrements des 30 derniers jours, un par titre, le plus récent d'abord. */
    fun recordedEntries(recs: List<RecLite>, type: String, now: Long): List<Entry> =
        recs.filter { r ->
            val id = r.contentId?.substringBefore(':').orEmpty()
            val t = if (r.contentType == "series" || r.contentType == "tv" || (r.season ?: 0) > 0) "series" else "movie"
            id.startsWith("tt") && t == type && now - r.at < WEEK_MS
        }.sortedByDescending { it.at }
            .map { Entry(it.contentId!!.substringBefore(':'), type, it.name, it.poster, it.at) }
            .distinctBy { it.id }

    /** Règle pure (testée) : ajoute / rafraîchit un titre, oublie ce qui a plus de 30 jours. */
    fun mergeWeek(current: List<Entry>, add: Entry, now: Long): List<Entry> {
        val prev = current.firstOrNull { it.id == add.id && it.type == add.type }
        // un titre déjà là garde sa date de découverte (il ne remonte pas à chaque passage)
        val merged = add.copy(at = prev?.at ?: add.at, poster = add.poster ?: prev?.poster, name = add.name.ifBlank { prev?.name.orEmpty() })
        return (listOf(merged) + current.filterNot { it.id == add.id && it.type == add.type })
            .filter { now - it.at < WEEK_MS }
            .sortedByDescending { it.at }
            .take(200)
    }

    fun week(type: String, now: Long = System.currentTimeMillis()): List<Entry> {
        val found = readWeek().filter { it.type == type && now - it.at < WEEK_MS }.sortedByDescending { it.at }
        // + les sorties de la semaine confirmées en VF (règles de Frank), comme le faisait Frank
        val seed = readEntries(prefs()?.getString("vfweek_seed_$type", null))
        // 11/11 : + ce qui a été enregistré ces 30 jours (L'Odyssée enregistrée mais absente de la tuile)
        val recs = runCatching { Record666.ready() }.getOrDefault(emptyList())
        val recorded = recordedEntries(recs.map { RecLite(it.contentId, it.contentType, it.contentName, it.posterUrl, it.addedAt, it.season) }, type, now)
        return (found + recorded + seed).distinctBy { it.id }
    }

    /**
     * 666TVF (09/11, 3) : « En VF cette semaine » était vide (le guetteur part de zéro). Comme Frank :
     * les sorties récentes (films sortis en VOD / Blu-ray en France ces 14 jours, séries avec un
     * épisode ces 7 jours, sur une plateforme FR) gardées seulement si la VF est confirmée.
     */
    private suspend fun refreshVfWeek() = withContext(Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return@withContext
        val today = LocalDate.now()
        val sources = mapOf(
            "movie" to (1..2).map { page ->
                "/discover/movie?language=fr-FR&region=FR&with_release_type=4%7C5&release_date.gte=${today.minusDays(30)}" +
                    "&release_date.lte=$today&without_genres=99%2C10770&sort_by=popularity.desc&include_adult=false&page=$page"
            },
            "series" to (1..2).map { page ->
                "/discover/tv?language=fr-FR&watch_region=FR&with_watch_monetization_types=flatrate%7Cfree%7Cads&air_date.gte=${today.minusDays(14)}" +
                    "&air_date.lte=$today&without_genres=99%2C10763%2C10764%2C10767%2C10762&vote_count.gte=10&sort_by=popularity.desc&page=$page"
            }
        )
        // 666TVF (11/11) : d'abord les derniers ajouts EN VF de French-Stream (son flux de nouveautés)
        val news = runCatching { VfCheck666.fsNews() }.getOrDefault(emptyList())
        for ((type, paths) in sources) {
            val cands = LinkedHashMap<Int, Pair<String, String?>>()
            for (n in news.filter { it.series == (type == "series") }.take(25)) {
                val kind = if (type == "series") "tv" else "movie"
                val res = tmdb("/search/$kind?language=fr-FR&query=${java.net.URLEncoder.encode(n.title, "UTF-8")}", key)?.optJSONArray("results")
                val id = VfCheck666.pickTmdb(res, n.title, type == "series", today.year - 1, today.toString()) ?: continue
                val o = (0 until (res?.length() ?: 0)).mapNotNull { res?.optJSONObject(it) }.firstOrNull { it.optInt("id") == id }
                val poster = o?.optString("poster_path")?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" }
                cands.putIfAbsent(id, n.title to poster)
            }
            for (path in paths) {
                val arr = tmdb(path, key)?.optJSONArray("results") ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("title").ifBlank { o.optString("name") }
                    if (SPORT.containsMatchIn(name)) continue
                    val poster = o.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" }
                    o.optInt("id").takeIf { it > 0 }?.let { cands.putIfAbsent(it, name to poster) }
                }
            }
            val now = System.currentTimeMillis()
            val kept = ArrayList<Entry>()
            for ((id, np) in cands.entries.take(55)) {
                val tag = withTimeoutOrNull(60_000) { VfCheck666.check("tmdb:$id", type) } ?: continue
                if (!VfCheck666.isVf(tag)) continue
                val imdb = VfCheck666.imdbFor("tmdb:$id") ?: continue
                kept += Entry(imdb, type, np.first, np.second, now)
            }
            // 11/11 : on garde aussi ce qui avait été trouvé avant (30 jours) — Malfaisante sortait du
            // flux de French-Stream et disparaissait de la tuile au passage suivant
            val merged = mergeSeed(readEntries(prefs()?.getString("vfweek_seed_$type", null)), kept, now)
            prefs()?.edit()?.putString("vfweek_seed_$type", writeEntries(merged))?.apply()
            Log.i(TAG, "vf semaine $type : ${kept.size} / ${cands.size}")
        }
    }

    private fun readWeek(): List<Entry> = readEntries(prefs()?.getString("vfweek", null))

    // ---------------- Pour toi ----------------

    fun pourToi(type: String): List<Entry> = readEntries(prefs()?.getString("pourtoi_$type", null))

    /** Au démarrage puis toutes les 3 heures (remplace l'envoi à Frank). */
    suspend fun pourToiLoop(repo: WatchProgressRepository) {
        delay(30_000L) // laisse l'appli démarrer tranquillement
        while (true) {
            runCatching { refreshVfWeek() }.onFailure { Log.w(TAG, "vf semaine: ${it.message}") }
            runCatching { refreshPourToi(repo) }.onFailure { Log.w(TAG, "pour toi: ${it.message}") }
            delay(POURTOI_EVERY_MS)
        }
    }

    private val EXCLUDED_GENRES = setOf(99, 10770, 10763, 10764, 10767, 10762)
    private val SPORT = Regex("""\b(UFC|WWE|AEW|NXT|Bellator|PFL|ONE Championship|SmackDown|Raw|WrestleMania|Royal Rumble|Formule 1|F1|Grand Prix|MotoGP|NBA|NFL|Ligue 1|Champions League)\b""", RegexOption.IGNORE_CASE)

    private suspend fun refreshPourToi(repo: WatchProgressRepository) = withContext(Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return@withContext
        val progress = withTimeoutOrNull(15_000L) { repo.allProgress.first() } ?: emptyList()
        val watchedItems = withTimeoutOrNull(15_000L) { repo.watchedItems.first() } ?: emptyList()
        val seeds = LinkedHashMap<String, Pair<String, Long>>() // tt → (type, at)
        fun add(id: String, rawType: String, at: Long) {
            if (!id.startsWith("tt")) return
            val t = if (rawType == "series" || rawType == "tv") "series" else "movie"
            val prev = seeds[id]
            if (prev == null || at > prev.second) seeds[id] = t to at
        }
        progress.forEach { p ->
            val percent = p.progressPercent ?: if (p.duration > 0) p.position * 100f / p.duration else 0f
            if (percent >= 20f || p.season != null) add(p.contentId.substringBefore(':'), p.contentType, p.lastWatched)
        }
        watchedItems.forEach { w -> add(w.contentId.substringBefore(':'), w.contentType, w.watchedAt) }
        if (seeds.isEmpty()) return@withContext
        val watched = (progress.map { it.contentId.substringBefore(':') } + watchedItems.map { it.contentId.substringBefore(':') })
            .filter { it.startsWith("tt") }.toHashSet()

        for (type in listOf("movie", "series")) {
            val kind = if (type == "series") "tv" else "movie"
            val mySeeds = seeds.entries.filter { it.value.first == type }.sortedByDescending { it.value.second }.take(20)
            if (mySeeds.isEmpty()) continue
            val score = HashMap<Int, Double>()
            val seedTmdb = HashSet<Int>()
            mySeeds.forEachIndexed { rank, (imdb, _) ->
                val found = tmdb("/find/$imdb?external_source=imdb_id", key) ?: return@forEachIndexed
                val hit = found.optJSONArray(if (kind == "tv") "tv_results" else "movie_results")?.optJSONObject(0) ?: return@forEachIndexed
                val name = hit.optString("title").ifBlank { hit.optString("name") }
                if (SPORT.containsMatchIn(name) || hit.genreIds().any { it in EXCLUDED_GENRES }) return@forEachIndexed
                val sid = hit.optInt("id"); seedTmdb += sid
                // les visionnages récents pèsent un peu plus
                val weight = 1.0 + (mySeeds.size - rank) / mySeeds.size.toDouble()
                for (list in listOf("recommendations", "similar")) {
                    val arr = tmdb("/$kind/$sid/$list?language=fr-FR&page=1", key)?.optJSONArray("results") ?: continue
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.genreIds().any { it in EXCLUDED_GENRES }) continue
                        val id = o.optInt("id").takeIf { it > 0 } ?: continue
                        score[id] = (score[id] ?: 0.0) + weight * (if (list == "recommendations") 1.0 else 0.6)
                    }
                }
            }
            val today = LocalDate.now().toString()
            val picked = ArrayList<Entry>()
            for (id in score.entries.filter { it.key !in seedTmdb }.sortedByDescending { it.value }.map { it.key }.take(70)) {
                if (picked.size >= 30) break
                val d = tmdb("/$kind/$id?language=fr-FR&append_to_response=external_ids,watch/providers", key) ?: continue
                val imdb = d.optJSONObject("external_ids")?.optString("imdb_id").orEmpty().ifBlank { d.optString("imdb_id") }
                if (!imdb.startsWith("tt") || imdb in watched) continue
                val released = d.optString(if (kind == "tv") "first_air_date" else "release_date")
                if (released.isBlank() || released > today) continue
                // sorti en France (abonnement, location, achat, gratuit) = distribué en VF
                val fr = d.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject("FR")
                if (fr == null || listOf("flatrate", "rent", "buy", "free", "ads").none { fr.optJSONArray(it)?.length() ?: 0 > 0 }) continue
                val name = d.optString("title").ifBlank { d.optString("name") }
                if (SPORT.containsMatchIn(name)) continue
                val poster = d.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" }
                picked += Entry(imdb, type, name, poster, 0L)
            }
            if (picked.isNotEmpty()) prefs()?.edit()?.putString("pourtoi_$type", writeEntries(picked))?.apply()
            Log.i(TAG, "pour toi $type : ${picked.size}")
        }
    }

    private fun JSONObject.genreIds(): List<Int> =
        optJSONArray("genre_ids")?.let { a -> (0 until a.length()).map { a.optInt(it) } }
            ?: optJSONArray("genres")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optInt("id") } }
            ?: emptyList()

    /**
     * 12/11 : date de sortie (la première, en général le cinéma) d'un film IMDb, via TMDB. Gardée en
     * mémoire. Sert à n'enregistrer que les vraies nouveautés (Scream 7, Marty Supreme : VF trouvée
     * cette semaine, mais sortis il y a des mois).
     */
    fun releaseDate(imdbId: String): java.time.LocalDate? {
        val p = prefs()
        p?.getString("rel_$imdbId", null)?.let { return runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val key = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return null
        val d = tmdb("/find/$imdbId?external_source=imdb_id", key)?.optJSONArray("movie_results")?.optJSONObject(0)
            ?.optString("release_date")?.takeIf { it.length >= 10 } ?: return null
        p?.edit()?.putString("rel_$imdbId", d)?.apply()
        return runCatching { java.time.LocalDate.parse(d) }.getOrNull()
    }

    /** Règle pure (testée) : une nouveauté = sortie depuis moins de 3 mois (date inconnue → non). */
    fun isRecentRelease(date: java.time.LocalDate?, today: java.time.LocalDate): Boolean =
        date != null && !date.isAfter(today) && !date.isBefore(today.minusMonths(3))

    private fun tmdb(path: String, key: String): JSONObject? {
        val sep = if ('?' in path) '&' else '?'
        val raw = Http666.get("https://api.themoviedb.org/3$path${sep}api_key=$key", 15_000) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    // ---------------- Prochainement (aiometa → intégré, 09/11) ----------------

    /** Films à venir au cinéma en France, séries dont le 1er épisode arrive (TMDB, en français). */
    suspend fun upcoming(type: String): CatalogRow? = withContext(Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return@withContext null
        val t = if (type == "series") "series" else "movie"
        val today = LocalDate.now()
        val paths = if (t == "movie") (1..3).map { "/movie/upcoming?language=fr-FR&region=FR&page=$it" }
        else (1..2).map { "/discover/tv?language=fr-FR&first_air_date.gte=${today.plusDays(1)}&first_air_date.lte=${today.plusDays(120)}" +
            "&without_genres=99%2C10763%2C10764%2C10767%2C10762&sort_by=popularity.desc&page=$it" }
        val ct = if (t == "series") ContentType.SERIES else ContentType.MOVIE
        val items = paths.flatMap { path ->
            val arr = tmdb(path, key)?.optJSONArray("results") ?: return@flatMap emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val date = o.optString("release_date").ifBlank { o.optString("first_air_date") }
                if (date.isBlank() || date <= today.toString()) return@mapNotNull null
                val id = o.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
                MetaPreview(
                    id = "tmdb:$id", type = ct, rawType = t,
                    name = o.optString("title").ifBlank { o.optString("name") },
                    poster = o.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" },
                    posterShape = PosterShape.POSTER,
                    background = o.optString("backdrop_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    logo = null, description = o.optString("overview").ifBlank { null },
                    releaseInfo = date.take(4), imdbRating = null, genres = emptyList(), released = date
                )
            }
        }.distinctBy { it.id }.sortedBy { it.released }
        CatalogRow(
            addonId = UPCOMING, addonName = "666TVF", addonBaseUrl = "", catalogId = "upcoming",
            catalogName = nameFor(UPCOMING, t).orEmpty(), type = ct, rawType = t, items = items, isLoading = false, hasMore = false
        )
    }

    // ---------------- Lignes pour les tuiles ----------------

    fun row(addonId: String, type: String): CatalogRow? {
        val t = if (type == "series") "series" else "movie"
        val entries = when (addonId) {
            VFWEEK -> week(t)
            POURTOI -> pourToi(t)
            else -> return null
        }
        val ct = if (t == "series") ContentType.SERIES else ContentType.MOVIE
        return CatalogRow(
            addonId = addonId,
            addonName = "666TVF",
            addonBaseUrl = "",
            catalogId = if (addonId == VFWEEK) "vf-week" else "pour-toi",
            catalogName = nameFor(addonId, t).orEmpty(),
            type = ct,
            rawType = t,
            items = entries.map { e ->
                MetaPreview(
                    id = e.id, type = ct, rawType = t, name = e.name.ifBlank { e.id }, poster = e.poster,
                    posterShape = PosterShape.POSTER, background = null, logo = null, description = null,
                    releaseInfo = null, imdbRating = null, genres = emptyList(), imdbId = e.id
                )
            },
            isLoading = false,
            hasMore = false
        )
    }

    // ---------------- stockage ----------------

    private fun writeEntries(list: List<Entry>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("t", it.type).put("n", it.name).put("p", it.poster ?: "").put("at", it.at)) }
    }.toString()

    internal fun readEntries(raw: String?): List<Entry> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Entry(o.optString("id"), o.optString("t", "movie"), o.optString("n"), o.optString("p").ifBlank { null }, o.optLong("at"))
        }
    }.getOrDefault(emptyList())
}
