package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.plugin.PluginManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 666TVF : le guetteur des sorties. Deux fois par jour, la Shield fait vérifier par les cavaliers
 * les films récents : ceux que Frank croit encore sans VF, et (03/11) les films sortis récemment en
 * VOD / Blu-ray (TMDB, France et USA), sans rien présélectionner. Une vraie release VF (TRUEFRENCH,
 * VFF, VFQ…) en 1080p ou 4K (HDLight compris, jamais 720p) → le film arrive dans
 * « En VF cette semaine », et (03/11) cri + bannière, une seule fois par film.
 * Jamais pendant un film : il s'arrête dès qu'on lance et reprend au passage suivant.
 */
@Singleton
class VfWatcher666 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginManager: PluginManager,
    private val calendarRepository: dagger.Lazy<com.nuvio.tv.core.calendar.CalendarRepository>
) {
    private val prefs by lazy { context.getSharedPreferences("666tv_guetteur", Context.MODE_PRIVATE) }

    /** Compte rendu d'un passage terminé : films vérifiés, titres trouvés en VF, et ceux à crier. */
    data class Run(val checked: Int, val found: List<String>)

    /** Film trouvé en VF pendant ce passage (pour la bannière et le cri). */
    data class Found(val imdbId: String, val name: String, val poster: String?, val ple: Boolean = false, val season: Int = 0)

    private data class Cand(val name: String, val poster: String?, val released: LocalDate? = null, val tmdbId: Int = 0)

    /** Null si pas encore l'heure, ou passage mis en pause (il reprendra). */
    suspend fun dailyRun(busy: () -> Boolean): Run? {
        // 666TVF : la version qui nomme les films refait un passage complet une fois (29/10)
        if (!prefs.getBoolean("named_v2", false)) {
            prefs.edit().remove("at").remove("day").remove("done").putBoolean("named_v2", true).apply()
        }
        // 666TVF (03/11) : nouveau guetteur (sorties TMDB + cri) → un passage tout de suite
        if (!prefs.getBoolean("tmdb_v1", false)) {
            prefs.edit().remove("at").remove("done").remove("found_names").putBoolean("pass_open", false).putBoolean("tmdb_v1", true).apply()
        }
        // 666TVF (13/11) : les films déjà criés sont revérifiés (Clayface criait avant la règle du cinéma)
        if (System.currentTimeMillis() - prefs.getLong("cinema_clean_at", 0L) > 12 * 3_600_000L) {
            runCatching { cleanCinemaOnly() }
            prefs.edit().putLong("cinema_clean_at", System.currentTimeMillis()).apply()
        }
        if (System.currentTimeMillis() - prefs.getLong("at", 0L) < INTERVAL_MS) return null
        val names = LinkedHashMap<String, Cand>()
        // 666TVF (09/11) : plus de liste de Frank (mis en pause par Hugging Face) : TMDB seulement.
        // 666TVF (03/11) : + les sorties VOD / Blu-ray récentes, sans présélection
        val known = prefs.getStringSet("vf_known", emptySet()).orEmpty()
        val firstTmdb = !prefs.getBoolean("tmdb_baseline", false)
        runCatching { recentReleases() }.getOrDefault(emptyList()).forEach { (id, cand) ->
            if (id !in known && id !in names && names.size < MAX_CANDIDATES) names[id] = cand
        }
        if (names.isEmpty()) return null
        val list = names.keys.toList()
        val order = PluginOrder666.order.map { it.lowercase() }
        val cavaliers = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
            .filter { s -> s.id.substringAfterLast(':').lowercase() in order && s.supportsType("movie") && Background666.allowed(s.id) }
            .sortedBy { s -> order.indexOf(s.id.substringAfterLast(':').lowercase()) }
        if (cavaliers.isEmpty()) return null
        // Reprise : un passage mis en pause reprend là où il en était (films déjà vérifiés pas refaits).
        val resuming = prefs.getBoolean("pass_open", false)
        if (!resuming) prefs.edit().putBoolean("pass_open", true).remove("done").remove("found_names").apply()
        val done = prefs.getStringSet("done", emptySet()).orEmpty().toMutableSet()
        val found = prefs.getString("found_names", null)?.split("\n")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        for (id in list) {
            if (id in done) continue
            // fiche / liste de flux ouverte ou film lancé : pause, reprise au prochain passage (20 min)
            if (busy()) { save(done, found); return null }
            // VF sûre ; le cri seulement en 1080p / 4K (jamais 720p ni CAM).
            var sure = false
            val cry = cavaliers.any { scraper ->
                if (busy()) return@any false
                val res = withTimeoutOrNull(30_000) {
                    runCatching { Background666.paced { pluginManager.executeScraper(scraper, id, "movie", null, null) } }.getOrNull()
                }.orEmpty()
                val texts = res.map { r -> listOfNotNull(r.name, r.title).joinToString(" ") }
                Groups666.observe(texts)
                if (texts.any { FrankVf666.isSureVfRelease(it) }) sure = true
                texts.any { isCryRelease(it) }
            }
            // 666TVF (13/11) : Clayface a crié en plein cinéma (French-Stream : VF en CAM). Un film étranger
            // encore en salle, sans aucune sortie VOD / Blu-ray : une « VF » en HD n'est qu'un son de salle ou un faux.
            if (cry && cinemaOnly(names[id]?.tmdbId ?: 0)) { done += id; continue }
            if (sure || cry) found += names[id]?.name ?: id
            if (cry) {
                val c = names[id]
                // Premier passage avec les sorties TMDB : ce qui est déjà en VF depuis longtemps ne crie pas
                // (sauf une sortie de la semaine : celle-là, on veut la savoir).
                if (shouldCry(firstTmdb, id in prefs.getStringSet("vf_known", emptySet()).orEmpty(), c?.released, LocalDate.now())) {
                    val poster = c?.poster ?: posterFor(id)
                    addFresh(Found(id, c?.name ?: id, poster))
                    // 666TVF (09/11) : « En VF cette semaine » est maintenant tenu sur la Shield
                    LocalCatalogs666.recordVf(id, "movie", (c?.name ?: id).replace(Regex("""\s*\(\d{4}\)$"""), ""), poster)
                }
                remember(id)
            }
            done += id
            if (busy()) { save(done, found); return null }   // pause : reprise au prochain passage
        }
        save(done, found)
        prefs.edit().putLong("at", System.currentTimeMillis()).putInt("last_found", found.size)
            .putBoolean("tmdb_baseline", true).putBoolean("pass_open", false).apply()
        return Run(checked = done.size, found = found)
    }

    private fun addFresh(f: Found) {
        val list = (pendingFresh() + f).distinctBy { "${it.imdbId}|${it.season}" }
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.imdbId).put("name", it.name).put("poster", it.poster ?: "").put("ple", it.ple).put("season", it.season)) }
        prefs.edit().putString("fresh", arr.toString()).apply()
    }

    /** Films trouvés en VF à crier maintenant (vidé à la lecture). */
    fun takePendingFresh(): List<Found> {
        val list = pendingFresh()
        if (list.isNotEmpty()) prefs.edit().remove("fresh").apply()
        return list
    }

    private fun pendingFresh(): List<Found> = runCatching {
        val arr = JSONArray(prefs.getString("fresh", "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Found(it.getString("id"), it.optString("name"), it.optString("poster").ifBlank { null }, it.optBoolean("ple"), it.optInt("season", 0)) } }
    }.getOrDefault(emptyList())

    // ---------- 666TVF (09/11) : dernier recours du moteur VF de Frank ----------

    /**
     * Les titres des listes VF que ni les plateformes ni French-Stream n'ont confirmés : le 1er
     * cavalier cherche, en douce (jamais pendant un film). Une release VF sûre (TRUEFRENCH, VFF,
     * FRENCH…, jamais CAM) → le titre est confirmé en VF. 15 titres par passage.
     */
    suspend fun vfStreamPass(busy: () -> Boolean) {
        val todo = VfCheck666.pendingStreamChecks(15)
        if (todo.isEmpty()) return
        val order = PluginOrder666.order.map { it.lowercase() }
        val all = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
            .filter { s -> s.id.substringAfterLast(':').lowercase() in order && Background666.allowed(s.id) }
            .sortedBy { s -> order.indexOf(s.id.substringAfterLast(':').lowercase()) }
        for ((imdb, type) in todo) {
            if (busy()) return
            val tv = type == "series"
            val scraper = all.firstOrNull { it.supportsType(if (tv) "tv" else "movie") } ?: return
            val res = withTimeoutOrNull(30_000) {
                runCatching {
                    Background666.paced { pluginManager.executeScraper(scraper, imdb, if (tv) "tv" else "movie", if (tv) 1 else null, if (tv) 1 else null) }
                }.getOrNull()
            }
            if (res == null && busy()) return
            val texts = res.orEmpty().map { r -> listOfNotNull(r.name, r.title).joinToString(" ") }
            Groups666.observe(texts)
            if (texts.any { FrankVf666.isSureVfRelease(it) && !VfCheck666.CAM_RELEASE.containsMatchIn(it) }) VfCheck666.confirm(imdb)
            VfCheck666.streamChecked(imdb, type)
        }
    }

    // ---------- 666TVF (07/11) : le guetteur séries ----------

    /**
     * Toutes les 12 h : les séries qui démarrent ou reprennent une saison (TMDB, 45 derniers jours),
     * que tu ne suis pas encore (les tiennes, c'est la cloche du calendrier). L'épisode 1 de la saison
     * en VF sûre 1080p / 4K → cri + bannière « 📺 », une fois par saison.
     */
    suspend fun seriesPass(busy: () -> Boolean) {
        if (System.currentTimeMillis() - prefs.getLong("series_at", 0L) < INTERVAL_MS) return
        val today = LocalDate.now()
        val followed = runCatching { calendarRepository.get().followedSeriesIds() }.getOrDefault(emptySet())
        val known = prefs.getStringSet("series_known", emptySet()).orEmpty()
        val firstPass = !prefs.getBoolean("series_baseline", false)
        val cands = runCatching { recentSeasons(today) }.getOrDefault(emptyList())
            .filter { it.imdbId.lowercase() !in followed && seasonKey(it) !in known }
        val order = PluginOrder666.order.map { it.lowercase() }
        val cavaliers = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
            .filter { s -> s.id.substringAfterLast(':').lowercase() in order && s.supportsType("tv") && Background666.allowed(s.id) }
            .sortedBy { s -> order.indexOf(s.id.substringAfterLast(':').lowercase()) }
        if (cavaliers.isEmpty()) return
        val done = prefs.getStringSet("series_done", emptySet()).orEmpty().toMutableSet()
        for (c in cands) {
            val key = seasonKey(c)
            if (key in done) continue
            if (busy()) { prefs.edit().putStringSet("series_done", done).apply(); return }   // reprise au prochain tour
            val cry = cavaliers.any { scraper ->
                if (busy()) return@any false
                val res = withTimeoutOrNull(30_000) {
                    runCatching { Background666.paced { pluginManager.executeScraper(scraper, c.imdbId, "tv", c.season, 1) } }.getOrNull()
                }.orEmpty()
                res.any { r -> isSeriesCryRelease(listOfNotNull(r.name, r.title).joinToString(" "), c.season) }
            }
            if (busy()) { prefs.edit().putStringSet("series_done", done).apply(); return }
            if (cry) {
                if (shouldCry(firstPass, false, c.seasonStart, today)) {
                    addFresh(Found(c.imdbId, c.name, c.poster, season = c.season))
                    LocalCatalogs666.recordVf(c.imdbId, "series", c.name, c.poster)
                    Announce666.log(context, "📺 Guetteur séries : ${c.name} saison ${c.season} en VF", "guetteur des séries")
                }
                rememberSeason(key)
            }
            done += key
        }
        prefs.edit().putLong("series_at", System.currentTimeMillis()).putBoolean("series_baseline", true)
            .remove("series_done").apply()
    }

    data class SeasonCand(val imdbId: String, val name: String, val poster: String?, val season: Int, val seasonStart: LocalDate?)

    private fun seasonKey(c: SeasonCand) = "${c.imdbId.lowercase()}:${c.season}"

    private fun rememberSeason(key: String) {
        val k = prefs.getStringSet("series_known", emptySet()).orEmpty()
        prefs.edit().putStringSet("series_known", (k.toList() + key).takeLast(800).toSet()).apply()
    }

    /** Séries avec un épisode diffusé ces 45 derniers jours, dont la saison en cours a démarré dans ce délai. */
    private suspend fun recentSeasons(today: LocalDate): List<SeasonCand> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return@withContext emptyList()
        val from = today.minusDays(SERIES_WINDOW_DAYS)
        val ids = LinkedHashSet<Int>()
        for (page in 1..3) {
            val url = "https://api.themoviedb.org/3/discover/tv?api_key=$key&language=fr-FR&air_date.gte=$from&air_date.lte=$today" +
                "&without_genres=10762%2C10763%2C10764%2C10766%2C10767%2C99&sort_by=popularity.desc&include_adult=false&page=$page"
            val raw = Http666.get(url, 20_000) ?: continue
            runCatching {
                val arr = JSONObject(raw).optJSONArray("results")
                if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.optInt("id")?.takeIf { it > 0 }?.let { ids += it }
            }
        }
        ids.take(MAX_SERIES_CANDIDATES).mapNotNull { id ->
            val raw = Http666.get("https://api.themoviedb.org/3/tv/$id?api_key=$key&language=fr-FR&append_to_response=external_ids", 15_000)
                ?: return@mapNotNull null
            runCatching { parseSeason(raw, today) }.getOrNull()
        }
    }

    // ---------- 666TVF (04/11) : les PLE de la WWE (Survivor Series, Royal Rumble, WrestleMania…) ----------

    /**
     * Toutes les 2 h : les PLE WWE / NXT sortis ces 4 derniers jours (TMDB les range comme des films)
     * sont vérifiés par les cavaliers. VF sûre en 1080p / 4K → cri + bannière « 🤼 », une fois par PLE,
     * et enregistrement auto en bas comme un film. Rien du tout s'il n'y a pas de PLE cette semaine.
     */
    suspend fun plePass(busy: () -> Boolean) {
        if (System.currentTimeMillis() - prefs.getLong("ple_at", 0L) < PLE_INTERVAL_MS) return
        val today = LocalDate.now()
        val window = pleCalendar().filter { inPleWindow(it.released, today) }
        val known = prefs.getStringSet("vf_known", emptySet()).orEmpty()
        val todo = window.filter { it.imdbId !in known }
        if (todo.isNotEmpty()) {
            val order = PluginOrder666.order.map { it.lowercase() }
            val cavaliers = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
                .filter { s -> s.id.substringAfterLast(':').lowercase() in order && s.supportsType("movie") && Background666.allowed(s.id) }
                .sortedBy { s -> order.indexOf(s.id.substringAfterLast(':').lowercase()) }
            if (cavaliers.isEmpty()) return
            for (p in todo) {
                if (busy()) return   // reprise au prochain tour (20 min), ple_at pas mis à jour
                val cry = cavaliers.any { scraper ->
                    if (busy()) return@any false
                    val res = withTimeoutOrNull(30_000) {
                        runCatching { Background666.paced { pluginManager.executeScraper(scraper, p.imdbId, "movie", null, null) } }.getOrNull()
                    }.orEmpty()
                    res.any { r -> isCryRelease(listOfNotNull(r.name, r.title).joinToString(" ")) }
                }
                if (cry) {
                    LocalCatalogs666.recordVf(p.imdbId, "movie", p.title, p.poster)
                    addFresh(Found(p.imdbId, p.title, p.poster, ple = true))
                    remember(p.imdbId)
                    Announce666.log(context, "🤼 Guetteur : ${p.title} trouvé en VF", "guetteur des PLE")
                }
            }
        }
        prefs.edit().putLong("ple_at", System.currentTimeMillis()).apply()
    }

    data class Ple(val imdbId: String, val title: String, val released: LocalDate?, val poster: String?)

    /** Les PLE de l'année (TMDB), gardés 24 h. */
    private suspend fun pleCalendar(): List<Ple> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cachedAt = prefs.getLong("ple_cal_at", 0L)
        if (System.currentTimeMillis() - cachedAt < 24L * 3600 * 1000) readPleCache()?.let { return@withContext it }
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return@withContext readPleCache().orEmpty()
        val year = LocalDate.now().year
        val seen = LinkedHashMap<Int, TmdbRelease>()
        for (q in PLE_QUERIES) {
            val url = "https://api.themoviedb.org/3/search/movie?api_key=$key&language=fr-FR&include_adult=false" +
                "&year=$year&query=" + java.net.URLEncoder.encode(q, "UTF-8")
            val raw = Http666.get(url, 15_000) ?: continue
            parseDiscover(raw).filter { isPleTitle(it.title) }.forEach { seen.putIfAbsent(it.tmdbId, it) }
        }
        val idCache = context.getSharedPreferences("666tv_guetteur_ids", Context.MODE_PRIVATE)
        val list = seen.values.mapNotNull { r ->
            val imdb = idCache.getString(r.tmdbId.toString(), null) ?: Http666.get(
                "https://api.themoviedb.org/3/movie/${r.tmdbId}/external_ids?api_key=$key", 15_000
            )?.let { runCatching { JSONObject(it).optString("imdb_id") }.getOrNull() }
                ?.takeIf { it.startsWith("tt") }?.also { idCache.edit().putString(r.tmdbId.toString(), it).apply() }
            imdb?.let { Ple(it, r.title, r.released, r.poster) }
        }
        if (list.isNotEmpty()) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("id", it.imdbId).put("t", it.title).put("d", it.released?.toString() ?: "").put("p", it.poster ?: "")) }
            prefs.edit().putString("ple_cal", arr.toString()).putLong("ple_cal_at", System.currentTimeMillis()).apply()
        }
        list.ifEmpty { readPleCache().orEmpty() }
    }

    private fun readPleCache(): List<Ple>? = runCatching {
        val arr = JSONArray(prefs.getString("ple_cal", null) ?: return null)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { Ple(it.getString("id"), it.optString("t"), runCatching { LocalDate.parse(it.optString("d")) }.getOrNull(), it.optString("p").ifBlank { null }) }
        }
    }.getOrNull()

    /** 666TVF (04/11) : l'affiche du film pour la bannière, quand Frank ne l'a pas donnée. */
    private suspend fun posterFor(imdbId: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return@withContext null
        val raw = Http666.get("https://api.themoviedb.org/3/find/$imdbId?api_key=$key&external_source=imdb_id", 15_000) ?: return@withContext null
        runCatching {
            JSONObject(raw).optJSONArray("movie_results")?.optJSONObject(0)?.optString("poster_path")
                ?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" }
        }.getOrNull()
    }

    private fun remember(id: String) {
        val k = prefs.getStringSet("vf_known", emptySet()).orEmpty()
        prefs.edit().putStringSet("vf_known", (k.toList() + id).takeLast(600).toSet()).apply()
    }

    /** Sorties VOD / Blu-ray des 75 derniers jours (France puis USA), les plus vues d'abord. */
    private suspend fun recentReleases(): List<Pair<String, Cand>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return@withContext emptyList()
        val today = LocalDate.now()
        val from = today.minusDays(75)
        val seen = LinkedHashMap<Int, TmdbRelease>()
        for (region in listOf("FR", "US")) for (page in 1..2) {
            val url = "https://api.themoviedb.org/3/discover/movie?api_key=$key&language=fr-FR&region=$region" +
                "&with_release_type=4%7C5&release_date.gte=$from&release_date.lte=$today" +
                "&without_genres=99%2C10770&sort_by=popularity.desc&include_adult=false&page=$page"
            val raw = Http666.get(url, 20_000) ?: continue
            parseDiscover(raw).forEach { seen.putIfAbsent(it.tmdbId, it) }
        }
        // 666TVF (08/11) : + les films encore au cinéma ou sortis en salle ces 5 derniers mois. L'Odyssée
        // est passé en HDLight VF avant sa sortie VOD (17/11) : il n'était pas surveillé. Le cri garde ses
        // règles (VF sûre, 1080p / 4K, jamais de CAM / TS / MD).
        val vod = seen.values.take(MAX_TMDB).toList()
        val cinema = LinkedHashMap<Int, TmdbRelease>()
        val cinemaFrom = today.minusDays(CINEMA_WINDOW_DAYS)
        for (region in listOf("FR", "US")) {
            val url = "https://api.themoviedb.org/3/discover/movie?api_key=$key&language=fr-FR&region=$region" +
                "&with_release_type=2%7C3&release_date.gte=$cinemaFrom&release_date.lte=$today" +
                "&without_genres=99%2C10770&sort_by=popularity.desc&include_adult=false&page=1"
            val raw = Http666.get(url, 20_000) ?: continue
            parseDiscover(raw).forEach { if (it.tmdbId !in seen) cinema.putIfAbsent(it.tmdbId, it) }
        }
        val idCache = context.getSharedPreferences("666tv_guetteur_ids", Context.MODE_PRIVATE)
        (vod + cinema.values.take(MAX_CINEMA)).mapNotNull { r ->
            val imdb = idCache.getString(r.tmdbId.toString(), null) ?: Http666.get(
                "https://api.themoviedb.org/3/movie/${r.tmdbId}/external_ids?api_key=$key", 15_000
            )?.let { runCatching { JSONObject(it).optString("imdb_id") }.getOrNull() }
                ?.takeIf { it.startsWith("tt") }?.also { idCache.edit().putString(r.tmdbId.toString(), it).apply() }
            imdb?.let { it to Cand(r.title + (r.year?.let { y -> " ($y)" } ?: ""), r.poster, r.released, r.tmdbId) }
        }
    }

    /**
     * Retire de « En VF cette semaine », de l'attente d'enregistrement et des verdicts VF les films
     * étrangers encore seulement au cinéma (faux cris d'avant la règle). Ils reviendront d'eux-mêmes
     * quand une vraie VF sortira (ils ne sont plus « connus » du guetteur).
     */
    private suspend fun cleanCinemaOnly() {
        val key = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return
        LocalCatalogs666.foundMovieIds().forEach { imdb ->
            val raw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Http666.get("https://api.themoviedb.org/3/find/$imdb?external_source=imdb_id&api_key=$key", 15_000)
            } ?: return@forEach
            val tmdbId = runCatching { JSONObject(raw).optJSONArray("movie_results")?.optJSONObject(0)?.optInt("id") ?: 0 }.getOrDefault(0)
            if (tmdbId > 0 && cinemaOnly(tmdbId)) {
                LocalCatalogs666.removeVf(imdb)
                AutoRecord666.removeFilm(context, imdb)
                VfCheck666.forget(imdb)
                val k = prefs.getStringSet("vf_known", emptySet()).orEmpty()
                prefs.edit().putStringSet("vf_known", k - imdb).apply()
            }
        }
    }

    /** Vrai si le film (TMDB) est encore seulement au cinéma (pas de sortie numérique) : pas de cri. */
    private suspend fun cinemaOnly(tmdbId: Int): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (tmdbId <= 0 || key.isBlank()) return@withContext false
        val raw = Http666.get("https://api.themoviedb.org/3/movie/$tmdbId?api_key=$key&append_to_response=release_dates", 15_000)
            ?: return@withContext false
        runCatching {
            val o = JSONObject(raw)
            val dates = mutableListOf<RelDate>()
            val arr = o.optJSONObject("release_dates")?.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val list = c.optJSONArray("release_dates") ?: continue
                for (j in 0 until list.length()) {
                    val d = list.optJSONObject(j) ?: continue
                    val day = runCatching { LocalDate.parse(d.optString("release_date").take(10)) }.getOrNull() ?: continue
                    dates += RelDate(c.optString("iso_3166_1"), d.optInt("type"), day)
                }
            }
            isCinemaOnly(o.optString("original_language"), dates, LocalDate.now())
        }.getOrDefault(false)
    }

    private fun save(done: Set<String>, found: List<String>) {
        prefs.edit().putStringSet("done", done).putString("found_names", found.joinToString("\n")).apply()
    }

    data class RelDate(val region: String, val type: Int, val date: LocalDate)

    data class TmdbRelease(val tmdbId: Int, val title: String, val year: String?, val poster: String?, val released: LocalDate? = null)

    companion object {
        const val INTERVAL_MS = 12L * 3600 * 1000
        const val MAX_CANDIDATES = 60
        const val MAX_TMDB = 40
        const val MAX_CINEMA = 20
        const val CINEMA_WINDOW_DAYS = 150L

        private val HD = Regex("""(?i)(?<![0-9])(1080[pi]?|2160p|4k|uhd)(?![0-9])|\bhd[ ._-]?light\b""")
        private val LOW = Regex("""(?i)(?<![0-9])(720p|576p|480p|360p)(?![0-9])|\b(cam|hdcam|camrip|ts|telesync|hdts|tc|hdtc|telecine|md|scr|screener|dvdscr)\b""")

        /** Règle pure (testée) : release qui fait crier — VF sûre, 1080p ou 4K (HDLight compris), pas de 720p ni de CAM. */
        fun isCryRelease(text: String?): Boolean =
            FrankVf666.isSureVfRelease(text) && HD.containsMatchIn(text!!) && !LOW.containsMatchIn(text)

        /** Lecture de /discover/movie (testée sans org.json réel : appelée seulement sur la Shield). */
        fun parseDiscover(raw: String): List<TmdbRelease> = runCatching {
            val arr = JSONObject(raw).optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title").ifBlank { return@mapNotNull null }
                TmdbRelease(
                    o.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null,
                    title,
                    o.optString("release_date").take(4).takeIf { it.length == 4 },
                    o.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" },
                    runCatching { LocalDate.parse(o.optString("release_date")) }.getOrNull()
                )
            }
        }.getOrDefault(emptyList())

        /**
         * Règle pure (testée) : un film trouvé en VF crie-t-il ? Jamais deux fois. Au tout premier
         * passage, seulement une sortie des 7 derniers jours (le reste est en VF depuis longtemps).
         */
        fun shouldCry(firstPass: Boolean, alreadyKnown: Boolean, released: LocalDate?, today: LocalDate): Boolean =
            !alreadyKnown && (!firstPass || (released != null && !released.isAfter(today) && !released.isBefore(today.minusDays(7))))

        private val CINEMA_REGIONS = setOf("FR", "BE", "CH", "CA", "US")

        /**
         * Règle pure (testée) : film étranger sorti en salle (France, Belgique, Suisse, Québec, USA) sans
         * aucune sortie VOD / Blu-ray (types 4 et 5) dans ces pays → encore au cinéma, une VF en HD est
         * impossible (son de salle ou faux) : pas de cri. Les films français gardent leurs propres règles.
         */
        fun isCinemaOnly(originalLanguage: String?, dates: List<RelDate>, today: LocalDate): Boolean {
            if (originalLanguage.equals("fr", ignoreCase = true)) return false
            val here = dates.filter { it.region.uppercase() in CINEMA_REGIONS && !it.date.isAfter(today) }
            if (here.any { it.type == 4 || it.type == 5 }) return false
            return here.any { it.type == 2 || it.type == 3 }
        }

        const val SERIES_WINDOW_DAYS = 45L
        const val MAX_SERIES_CANDIDATES = 30
        private val SEASON_EP = Regex("""(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})\b""")
        private val SEASON_ONLY = Regex("""(?i)\bS(\d{1,2})\b(?![ ._-]?E\d)|\bSaison[ ._-]?(\d{1,2})\b|\bSeason[ ._-]?(\d{1,2})\b""")

        /**
         * Règle pure (testée) : release qui fait crier le guetteur séries — comme un film (VF sûre, 1080p/4K)
         * ET de la bonne saison (épisode 1 ou pack de la saison) : jamais la VF d'une vieille saison.
         */
        fun isSeriesCryRelease(text: String?, season: Int): Boolean {
            if (!isCryRelease(text)) return false
            val t = text!!
            SEASON_EP.find(t)?.let { m -> return m.groupValues[1].toInt() == season && m.groupValues[2].toInt() == 1 }
            SEASON_ONLY.find(t)?.let { m ->
                val n = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toIntOrNull()
                return n == season
            }
            return false
        }

        /**
         * Lecture de /tv/{id} (Shield seulement) : saison en cours, son 1er jour, l'id IMDb.
         * Null si la saison a démarré il y a plus de [SERIES_WINDOW_DAYS] jours, ou pas d'id IMDb.
         */
        fun parseSeason(raw: String, today: LocalDate): SeasonCand? {
            val o = JSONObject(raw)
            val imdb = o.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.startsWith("tt") } ?: return null
            val last = o.optJSONObject("last_episode_to_air") ?: return null
            val season = last.optInt("season_number", 0).takeIf { it > 0 } ?: return null
            val seasons = o.optJSONArray("seasons")
            var start: LocalDate? = null
            if (seasons != null) for (i in 0 until seasons.length()) {
                val s = seasons.optJSONObject(i) ?: continue
                if (s.optInt("season_number") == season) start = runCatching { LocalDate.parse(s.optString("air_date")) }.getOrNull()
            }
            if (start == null || start.isAfter(today) || start.isBefore(today.minusDays(SERIES_WINDOW_DAYS))) return null
            val name = o.optString("name").ifBlank { o.optString("original_name") }.ifBlank { return null }
            val poster = o.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" }
            return SeasonCand(imdb, name, poster, season, start)
        }

        const val PLE_INTERVAL_MS = 2L * 3600 * 1000
        val PLE_QUERIES = listOf(
            "WWE", "NXT", "Survivor Series", "Royal Rumble", "WrestleMania", "SummerSlam", "Crown Jewel",
            "Elimination Chamber", "Money in the Bank", "Saturday Night's Main Event", "Wrestlepalooza",
            "Clash in Paris", "King and Queen of the Ring", "Night of Champions"
        )
        private val PLE_STRONG = Regex(
            """(?i)survivor series|royal rumble|wrestlemania|summerslam|crown jewel|elimination chamber|money in the bank|""" +
                """saturday night'?s main event|wrestlepalooza|clash in paris|king (and|&) queen of the ring"""
        )
        private val PLE_WWE = Regex("""(?i)(?<![a-z])(wwe|nxt)(?![a-z])""")
        private val NOT_PLE = Regex("""(?i)documentary|documentaire|best of|greatest|top 10|untold|legends|biography|2k\d""")

        /** Règle pure (testée) : un titre TMDB est-il un PLE WWE / NXT (pas un docu ni un « best of ») ? */
        fun isPleTitle(title: String): Boolean =
            !NOT_PLE.containsMatchIn(title) && (PLE_STRONG.containsMatchIn(title) || PLE_WWE.containsMatchIn(title))

        /** Règle pure (testée) : le PLE est sorti entre aujourd'hui et il y a 4 jours (la VF arrive dans ce délai). */
        fun inPleWindow(released: LocalDate?, today: LocalDate): Boolean =
            released != null && !released.isAfter(today) && !released.isBefore(today.minusDays(4))

        /** Ligne de « Dernières annonces » (testée). */
        fun summary(run: Run): String =
            if (run.found.isEmpty()) "🔎 Guetteur : ${run.checked} film(s) vérifié(s), rien de nouveau en VF"
            else "🔎 Guetteur : ${run.checked} film(s) vérifié(s) · " + run.found.joinToString(", ") + " en VF"
    }
}
