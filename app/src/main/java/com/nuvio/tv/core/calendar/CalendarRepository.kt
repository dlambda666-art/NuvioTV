package com.nuvio.tv.core.calendar

import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.util.parseEpisodeReleaseInstant
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Un épisode dans le calendrier de 666TV. */
data class CalendarEpisode(
    val seriesId: String,
    val seriesName: String,
    val poster: String?,
    val season: Int,
    val episode: Int,
    val title: String?,
    val airInstant: Instant,
    val addonBaseUrl: String?,
    /** Identifiant de l'épisode chez aiometadata (tt…:saison:épisode), comme à la lecture. */
    val videoId: String? = null
) {
    val key: String get() = "$seriesId:$season:$episode"
    val code: String get() = "S%02dE%02d".format(season, episode)
    fun airDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = airInstant.atZone(zone).toLocalDate()
    fun hasAired(now: Instant = Instant.now()): Boolean = !airInstant.isAfter(now)
}

enum class VfState { UNKNOWN, CHECKING, AVAILABLE, NOT_YET, NO_CHECKER }

/**
 * 666TV : calendrier des sorties.
 * - Séries suivies : celles de la bibliothèque + celles en cours (vues ces 90 derniers jours).
 * - Dates des épisodes : la fiche de la série (aiometadata).
 * - VF : épisode déjà vu, ou un des plugins 666 trouve un lien VF prêt (StreamFusion d'abord).
 * - Cloche : séries pour lesquelles on veut être prévenu d'un nouvel épisode en VF.
 * Idée de Nuvio Arc (ysosrs123/NuvioTV-Fork), réécrite pour 666TV.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CalendarRepository @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val metaRepository: MetaRepository,
    private val tmdbService: TmdbService,
    private val pluginManager: PluginManager,
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
    private val localDebridService: com.nuvio.tv.core.debrid.LocalDebridService,
    private val debridSettingsDataStore: com.nuvio.tv.data.local.DebridSettingsDataStore,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) {
    private data class SeriesSeed(val id: String, val name: String, val poster: String?, val addonBaseUrl: String?)

    private data class CachedEpisodes(val at: Long, val episodes: List<CalendarEpisode>)

    private val episodeCache = ConcurrentHashMap<String, CachedEpisodes>()
    private val vfNegativeCache = ConcurrentHashMap<String, Long>()
    private val metaSemaphore = Semaphore(6)
    private val vfSemaphore = Semaphore(2)

    private val bellsKey = stringSetPreferencesKey("calendar_bells_666")
    private val vfFoundKey = stringSetPreferencesKey("calendar_vf_found_666_v3") // v3 : VF sûre seulement, épisode vu jusqu'au bout (on revérifie tout)
    private val notifiedKey = stringSetPreferencesKey("calendar_notified_666")
    // 666TVF : séries dont la VF arrive vite (WWE Raw…) et retards déjà signalés
    private val fastVfKey = stringSetPreferencesKey("calendar_fast_vf_666")
    private val lateAlertedKey = stringSetPreferencesKey("calendar_late_alerted_666")
    private val lastRefreshKey = stringPreferencesKey("calendar_last_refresh_666")

    private fun store(pid: Int = profileManager.activeProfileId.value) = factory.get(pid, FEATURE)

    val bells: Flow<Set<String>> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { it[bellsKey].orEmpty() }
    }

    suspend fun toggleBell(seriesId: String) {
        store().edit { prefs ->
            val current = prefs[bellsKey].orEmpty()
            prefs[bellsKey] = if (seriesId in current) current - seriesId else current + seriesId
        }
    }

    /** Épisodes de [daysBack] jours en arrière à [daysAhead] jours en avant, triés par date. */
    suspend fun episodes(daysBack: Long = 7, daysAhead: Long = 30, force: Boolean = false): List<CalendarEpisode> =
        withContext(Dispatchers.IO) {
            val seeds = followedSeries()
            val now = Instant.now()
            val from = now.minusSeconds(daysBack * 86_400)
            val to = now.plusSeconds(daysAhead * 86_400)
            coroutineScope {
                seeds.map { seed ->
                    async { metaSemaphore.withPermit { episodesFor(seed, force) } }
                }.awaitAll()
            }.flatten()
                .filter { !it.airInstant.isBefore(from) && !it.airInstant.isAfter(to) }
                .sortedBy { it.airInstant }
        }

    /**
     * Séries suivies. Au démarrage, bibliothèque et historique se chargent (Trakt…) : on attend
     * qu'ils aient vraiment des données (jusqu'à 12 s) au lieu de prendre une première liste vide.
     */
    private suspend fun followedSeries(): List<SeriesSeed> {
        // Ordre de priorité : d'abord ce que tu regardes en ce moment (en cours, puis vu jusqu'au
        // bout récemment : WWE…), ensuite le reste de la bibliothèque (souvent des séries finies).
        val seeds = LinkedHashMap<String, SeriesSeed>()
        val recentLimit = System.currentTimeMillis() - 90L * 86_400_000L
        val progress = withTimeoutOrNull(12_000) { watchProgressRepository.allProgress.firstOrNull { it.isNotEmpty() } }
            ?: withTimeoutOrNull(2_000) { watchProgressRepository.allProgress.first() }.orEmpty()
        val watched = withTimeoutOrNull(8_000) { watchProgressRepository.watchedItems.firstOrNull { it.isNotEmpty() } }
            .orEmpty()
        val recent = progress.filter { it.contentType.isSeriesType() && it.lastWatched >= recentLimit }
            .map { Triple(it.lastWatched, it.contentId, SeriesSeed(it.contentId, it.name, it.poster, it.addonBaseUrl)) } +
            watched.filter { it.contentType.isSeriesType() && it.watchedAt >= recentLimit }
                .map { Triple(it.watchedAt, it.contentId, SeriesSeed(it.contentId, it.title, it.poster, null)) }
        recent.sortedByDescending { it.first }.forEach { (_, id, seed) -> seeds.putIfAbsent(id, seed) }
        val library = withTimeoutOrNull(12_000) { libraryRepository.libraryItems.firstOrNull { it.isNotEmpty() } }
            ?: withTimeoutOrNull(2_000) { libraryRepository.libraryItems.first() }.orEmpty()
        library.filter { it.type.isSeriesType() }.forEach {
            val existing = seeds[it.id]
            if (existing == null) {
                seeds[it.id] = SeriesSeed(it.id, it.name, it.poster, it.addonBaseUrl)
            } else if (existing.addonBaseUrl == null && it.addonBaseUrl != null) {
                seeds[it.id] = existing.copy(addonBaseUrl = it.addonBaseUrl)
            }
        }
        lastFollowedCount = seeds.size
        return seeds.values.take(MAX_SERIES)
    }

    /** 666TVF (07/11) : ids (tt…) des séries déjà suivies par le calendrier (le guetteur séries les laisse à la cloche). */
    suspend fun followedSeriesIds(): Set<String> = withContext(Dispatchers.IO) {
        followedSeries().map { it.id.substringBefore(':').lowercase() }.toSet()
    }

    /** Nombre de séries suivies au dernier chargement (affiché dans le calendrier). */
    @Volatile
    var lastFollowedCount: Int = 0
        private set

    private suspend fun episodesFor(seed: SeriesSeed, force: Boolean): List<CalendarEpisode> {
        val cached = episodeCache[seed.id]
        if (!force && cached != null && System.currentTimeMillis() - cached.at < META_TTL_MS) return cached.episodes
        val meta: Meta = withTimeoutOrNull(15_000) {
            metaRepository.getMetaFromPrimaryAddon("series", seed.id)
                .firstOrNull { it is NetworkResult.Success || it is NetworkResult.Error }
                ?.let { (it as? NetworkResult.Success)?.data }
                ?.takeIf { it.videos.isNotEmpty() }
        } ?: cinemeta666(seed.id) ?: run {
            // 666TVF (11/11) : Cinemeta en panne → les dates de TMDB (les deux dernières saisons)
            val viaTmdb = tmdbEpisodes666(seed)
            if (viaTmdb.isNullOrEmpty()) return cached?.episodes.orEmpty()
            recheckOnce666(viaTmdb)
            episodeCache[seed.id] = CachedEpisodes(System.currentTimeMillis(), viaTmdb)
            return viaTmdb
        }
        val episodes = meta.videos.mapNotNull { video ->
            val season = video.season ?: return@mapNotNull null
            val episode = video.episode ?: return@mapNotNull null
            if (season <= 0) return@mapNotNull null
            val instant = parseEpisodeReleaseInstant(video.released) ?: return@mapNotNull null
            CalendarEpisode(
                seriesId = seed.id,
                seriesName = meta.name.ifBlank { seed.name },
                poster = meta.poster ?: seed.poster,
                season = season,
                episode = episode,
                title = video.title.takeIf { it.isNotBlank() && !it.equals("Episode $episode", ignoreCase = true) },
                airInstant = instant,
                addonBaseUrl = seed.addonBaseUrl,
                videoId = video.id.takeIf { it.isNotBlank() }
            )
        }
        val fixed = renumber666(seed, episodes)
        recheckOnce666(fixed)
        episodeCache[seed.id] = CachedEpisodes(System.currentTimeMillis(), fixed)
        return fixed
    }

    /**
     * 666TVF (09/11) : aiometa retiré — les épisodes viennent de Cinemeta, demandé directement (même s'il
     * n'est pas activé dans les addons) ; un id TMDB est d'abord traduit en id IMDb.
     */
    private suspend fun cinemeta666(id: String): Meta? = runCatching {
        val base = id.substringBefore(':')
        val imdb = if (base.startsWith("tt")) base else
            id.removePrefix("tmdb:").substringBefore(':').toIntOrNull()?.let { tmdbService.tmdbToImdb(it, "series") }
        if (imdb.isNullOrBlank()) return null
        withTimeoutOrNull(15_000) {
            metaRepository.getMeta("https://v3-cinemeta.strem.io", "series", imdb)
                .firstOrNull { it is NetworkResult.Success || it is NetworkResult.Error }
                ?.let { (it as? NetworkResult.Success)?.data }
        }
    }.getOrNull()

    /**
     * 666TVF (11/11) : repli si Cinemeta ne répond pas. TMDB ne donne que le jour : l'heure est mise à
     * 20 h (heure de New York), comme la plupart des diffusions américaines suivies.
     */
    private suspend fun tmdbEpisodes666(seed: SeriesSeed): List<CalendarEpisode>? = runCatching {
        val tmdbNum = tmdbService.ensureTmdbId(seed.id, "series")?.removePrefix("tmdb:")?.toIntOrNull() ?: return null
        val (name, seasons) = tmdbService.tvBasics666(tmdbNum) ?: return null
        val ny = java.time.ZoneId.of("America/New_York")
        (maxOf(1, seasons - 1)..seasons).flatMap { season ->
            tmdbService.seasonAirDates666(tmdbNum, season).orEmpty().mapNotNull { (ep, date) ->
                date ?: return@mapNotNull null
                CalendarEpisode(
                    seriesId = seed.id,
                    seriesName = seed.name.ifBlank { name.orEmpty() },
                    poster = seed.poster,
                    season = season,
                    episode = ep,
                    title = null,
                    airInstant = date.atTime(20, 0).atZone(ny).toInstant(),
                    addonBaseUrl = seed.addonBaseUrl
                )
            }
        }
    }.getOrNull()

    /**
     * 666TVF (06/11) : si les métadonnées numérotent autrement que les liens (NXT : PLE comptés comme
     * épisodes → « S20E49 » au lieu de S20E44, et des épisodes 45-48 annoncés alors que pas diffusés),
     * on reprend la numérotation TMDB par date de diffusion et on retire les PLE (le guetteur des PLE
     * s'en occupe). Les séries normales ne changent pas.
     */
    private suspend fun renumber666(seed: SeriesSeed, episodes: List<CalendarEpisode>): List<CalendarEpisode> = runCatching {
        val tmdbNum = tmdbService.ensureTmdbId(seed.id, "series")?.removePrefix("tmdb:")?.toIntOrNull() ?: return episodes
        val ny = java.time.ZoneId.of("America/New_York")
        val out = mutableListOf<CalendarEpisode>()
        for ((season, eps) in episodes.groupBy { it.season }) {
            val tmdb = tmdbService.seasonAirDates666(tmdbNum, season)
            val map = if (tmdb == null) emptyMap()
                else com.nuvio.tv.core.tmdb.renumberByDate666(eps.map { it.episode to it.airInstant.atZone(ny).toLocalDate() }, tmdb)
            if (map.isEmpty()) { out += eps; continue }
            val renamed = eps.mapNotNull { e ->
                val n = map[e.episode] ?: return@mapNotNull null   // PLE / spécial sans équivalent : retiré
                e.copy(episode = n, videoId = null)
            }
            out += renamed
            // Les numéros notés avec l'ancienne numérotation au-delà du dernier épisode diffusé
            // (ex. « S20E45-49 en VF ») désigneraient des épisodes futurs : on les oublie.
            val now = java.time.Instant.now()
            val lastAired = renamed.filter { !it.airInstant.isAfter(now) }.maxOfOrNull { it.episode } ?: 0
            val stale = (eps.map { it.episode } + renamed.map { it.episode }).filter { it > lastAired }
                .map { "${seed.id}:$season:$it" }.toSet()
            if (stale.isNotEmpty()) forget666(stale)
        }
        out.distinctBy { "${it.season}:${it.episode}" }
    }.getOrDefault(episodes)

    /**
     * 666TVF (07/11) : une seule fois, les « VF dispo » des 10 derniers jours sont revérifiés avec la
     * règle stricte (SmackDown / AHS annoncés à tort). S'ils sont vraiment en VF, la cloche resonnera.
     */
    private suspend fun recheckOnce666(episodes: List<CalendarEpisode>) {
        val prefs = appContext.getSharedPreferences("666tv_calendar_recheck2", android.content.Context.MODE_PRIVATE)
        val done = prefs.getStringSet("done", emptySet()).orEmpty()
        val seriesId = episodes.firstOrNull()?.seriesId ?: return
        if (seriesId in done) return
        val since = java.time.Instant.now().minus(java.time.Duration.ofDays(10))
        val watchedOrOld = episodes.filter { it.airInstant.isBefore(since) || isWatched(it) }.map { it.key }.toSet()
        val keys = episodes.map { it.key }.toSet() - watchedOrOld
        val known = knownVf()
        val toForget = keys.filter { it in known }.toSet()
        if (toForget.isNotEmpty()) forget666(toForget)
        prefs.edit().putStringSet("done", done + seriesId).apply()
    }

    /** « Actualiser » : on revérifie tout de suite les épisodes marqués « pas encore de VF ». */
    fun clearVfNegativeCache() = vfNegativeCache.clear()

    /** VF déjà trouvée (mémorisée) pour cet épisode ? */
    suspend fun knownVf(): Set<String> = store().data.first()[vfFoundKey].orEmpty()

    /** 666TVF (01/11) : suit en direct les VF trouvées (la cloche les note), pour que le calendrier se mette à jour tout seul. */
    fun knownVfFlow(): Flow<Set<String>> = store().data.map { it[vfFoundKey].orEmpty() }

    /**
     * L'épisode est-il dispo en VF ?
     * 1. Déjà vu dans 666TV → oui (on ne regarde que de la VF).
     * 2. Sinon on demande aux plugins 666 dans l'ordre choisi (Outils 666, Comet666 d'abord par défaut),
     *    Wastream, puis Comet666) et on s'arrête au premier qui trouve un lien VF prêt.
     */
    suspend fun checkVf(episode: CalendarEpisode): VfState = withContext(Dispatchers.IO) {
        if (!episode.hasAired()) return@withContext VfState.UNKNOWN
        if (episode.key in knownVf()) return@withContext VfState.AVAILABLE
        if (isWatched(episode)) {
            rememberVf(episode)
            return@withContext VfState.AVAILABLE
        }
        vfNegativeCache[episode.key]?.let { at ->
            if (System.currentTimeMillis() - at < VF_NEGATIVE_TTL_MS) return@withContext VfState.NOT_YET
        }
        val checkers = pluginManager.enabledScrapers.first()
            .mapNotNull { scraper ->
                val rank = VF_CHECKERS.indexOf(scraper.id.substringAfterLast(':').lowercase())
                // 666TVF (06/11) : jamais StreamFusion en arrière-plan (HTTP 429)
                if (rank >= 0 && com.nuvio.tv.core.tools.Background666.allowed(scraper.id)) rank to scraper else null
            }
            .sortedBy { it.first }
            .map { it.second }
        if (checkers.isEmpty()) return@withContext VfState.NO_CHECKER
        val found = vfSemaphore.withPermit {
            // Même demande qu'à l'ouverture de l'épisode : id TMDB, sinon id IMDb (WWE, UFC…),
            // et la même numérotation saison/épisode.
            val videoId = episode.videoId ?: "${episode.seriesId}:${episode.season}:${episode.episode}"
            val tmdbId = runCatching { tmdbService.ensureTmdbId(videoId, "series") }.getOrNull()
            val imdbBase = videoId.substringBefore(":").takeIf { Regex("^tt\\d+$", RegexOption.IGNORE_CASE).matches(it) }
            val requestId = tmdbId ?: imdbBase ?: return@withPermit false
            val (pluginSeason, pluginEpisode) = com.nuvio.tv.core.plugin.resolvePluginSeasonEpisode(
                videoId = videoId,
                season = episode.season,
                episode = episode.episode
            )
            val airDates666 = setOf(
                episode.airInstant.atZone(java.time.ZoneId.of("America/New_York")).toLocalDate(),
                episode.airInstant.atZone(java.time.ZoneId.of("Europe/Paris")).toLocalDate()
            )
            suspend fun lookFor(ps: Int?, pe: Int?): Boolean = checkers.any { scraper ->
                runCatching {
                    val results = withTimeoutOrNull(30_000) {
                        com.nuvio.tv.core.tools.Background666.paced {
                            pluginManager.executeScraper(scraper, requestId, "tv", ps, pe)
                        }
                    }.orEmpty()
                    val sure = results.filter { result ->
                        (result.url.isNotBlank() || !result.infoHash.isNullOrBlank()) &&
                            result.name?.contains("diagnostic", ignoreCase = true) != true &&
                            // 666TVF : un « MULTi (VF ?) » ne prouve rien (AHS S13 : c'était du polonais).
                            // Le calendrier n'annonce que de la VF sûre.
                            !result.title.contains("VF ?") && !result.title.contains("VF?") &&
                            // 666TVF (07/11) : bon épisode, bonne date, pas de VOSTFR, pas de 720p
                            CalendarVfRule666.counts(
                                listOfNotNull(result.name, result.title).joinToString(" "),
                                result.quality, ps, pe, airDates666
                            )
                    }
                    // Lien prêt (http) : VF dispo. Torrent (Comet666) : seulement s'il est en cache
                    // chez le débrideur, sinon il ne se lancerait pas.
                    // 666TVF (07/11, 2) : un lien prêt ne prouve la VF que s'il NOMME l'épisode (SxxEyy ou
                    // date) : Lumio renvoyait le SmackDown de la semaine passée sous un titre sans numéro.
                    val proof = sure.firstOrNull { r ->
                        r.url.startsWith("http", ignoreCase = true) &&
                            CalendarVfRule666.namesEpisode(listOfNotNull(r.name, r.title).joinToString(" "), ps, pe, airDates666)
                    }
                    if (proof != null) {
                        trace666(episode, scraper.id, listOfNotNull(proof.name, proof.title).joinToString(" "), "lien prêt")
                        true
                    } else {
                        val hashes = sure.mapNotNull { r ->
                            r.infoHash?.lowercase() ?: Regex("btih:([0-9a-fA-F]{40})").find(r.url)?.groupValues?.get(1)?.lowercase()
                        }
                        val cached = anyCached666(hashes)
                        if (cached) trace666(episode, scraper.id, sure.firstOrNull { !it.infoHash.isNullOrBlank() }?.let { listOfNotNull(it.name, it.title).joinToString(" ") }.orEmpty(), "torrent en cache")
                        cached
                    }
                }.onFailure { Log.d(TAG, "VF check ${scraper.id} failed for ${episode.key}: ${it.message}") }
                    .getOrDefault(false)
            }
            if (lookFor(pluginSeason, pluginEpisode)) return@withPermit true
            // 666TVF (06/11) : numérotation différente (le calendrier compte les PLE de NXT) → on
            // retrouve l'épisode TMDB diffusé à la même date (jour américain) et on redemande.
            val tmdbNum = tmdbId?.removePrefix("tmdb:")?.toIntOrNull()
            val s0 = pluginSeason
            if (tmdbNum != null && s0 != null && s0 > 0) {
                val usDate = episode.airInstant.atZone(java.time.ZoneId.of("America/New_York")).toLocalDate()
                val alt = runCatching { tmdbService.episodeNumberForAirDate666(tmdbNum, s0, usDate) }.getOrNull()
                if (alt != null && alt != pluginEpisode) return@withPermit lookFor(s0, alt)
            }
            false
        }
        if (found) {
            rememberVf(episode)
            VfState.AVAILABLE
        } else {
            vfNegativeCache[episode.key] = System.currentTimeMillis()
            VfState.NOT_YET
        }
    }

    /**
     * 666TVF (09/11) : la cloche a sonné (VF prouvée) → la série rejoint « Séries en VF cette semaine »
     * (tenu sur la Shield ; avant, la cloche prévenait Frank). Rien ne sort de la Shield.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun trace666(episode: CalendarEpisode, scraperId: String, text: String, kind: String) {
        runCatching {
            com.nuvio.tv.core.tools.LocalCatalogs666.recordVf(episode.seriesId.substringBefore(':'), "series", episode.seriesName, episode.poster)
        }
    }

    private suspend fun anyCached666(hashes: List<String>): Boolean {
        if (hashes.isEmpty()) return false
        val account = runCatching { debridSettingsDataStore.settings.first().activeResolverCredential }.getOrNull() ?: return false
        val cached = runCatching { localDebridService.checkCached(account, hashes.distinct().take(40)) }.getOrNull() ?: return false
        return cached.isNotEmpty()
    }

    private suspend fun isWatched(episode: CalendarEpisode): Boolean = runCatching {
        withTimeoutOrNull(3_000) {
            watchProgressRepository.isWatched(
                contentId = episode.seriesId,
                season = episode.season,
                episode = episode.episode
            ).first()
        } == true
        // 666TVF : un épisode simplement commencé ne prouve plus rien (AHS S13 : on lance,
        // c'est du polonais, on arrête). Seul un épisode vu jusqu'au bout compte comme VF.
    }.getOrDefault(false)

    private suspend fun forget666(keys: Set<String>) {
        store().edit {
            it[vfFoundKey] = it[vfFoundKey].orEmpty() - keys
            it[notifiedKey] = it[notifiedKey].orEmpty() - keys
        }
        com.nuvio.tv.core.tools.AutoRecord666.forget(appContext, keys)
    }

    private suspend fun rememberVf(episode: CalendarEpisode) {
        store().edit { it[vfFoundKey] = (it[vfFoundKey].orEmpty() + episode.key).takeLast(MAX_REMEMBERED) }
    }

    /**
     * Pour la cloche : épisodes des séries suivies sortis ces 10 derniers jours, dispo en VF
     * et pas encore signalés. Les renvoie et les marque comme signalés.
     */
    suspend fun newVfEpisodesToAnnounce(): List<CalendarEpisode> = withContext(Dispatchers.IO) {
        val bellSet = store().data.first()[bellsKey].orEmpty()
        if (bellSet.isEmpty()) return@withContext emptyList()
        val notified = store().data.first()[notifiedKey].orEmpty()
        val candidates = episodes(daysBack = 10, daysAhead = 0)
            .filter { it.seriesId in bellSet && it.hasAired() && it.key !in notified }
        val announce = candidates.filter { checkVf(it) == VfState.AVAILABLE }
        if (announce.isNotEmpty()) {
            val now = Instant.now()
            val fast = announce.filter { isFastVf666(it.airInstant.toEpochMilli(), now.toEpochMilli()) }.map { it.seriesId }
            store().edit { prefs ->
                if (fast.isNotEmpty()) prefs[fastVfKey] = (prefs[fastVfKey].orEmpty() + fast).takeLast(MAX_SERIES)
                prefs[notifiedKey] = (prefs[notifiedKey].orEmpty() + announce.map { it.key }).takeLast(MAX_REMEMBERED)
                prefs[lastRefreshKey] = System.currentTimeMillis().toString()
            }
        }
        announce
    }

    /**
     * 666TVF : vérification des galas / émissions rapides (WWE Raw…). Une série dont la VF
     * arrive d'habitude en moins de 36 h, et dont l'épisode n'a toujours pas de VF 18 h après
     * sa diffusion : signalé une fois (« ⏳ pas encore en VF »). Aucune alerte pour les séries
     * dont la VF arrive des semaines plus tard (AHS…).
     */
    suspend fun lateVfEpisodes666(): List<CalendarEpisode> = withContext(Dispatchers.IO) {
        val prefs = store().data.first()
        val bellSet = prefs[bellsKey].orEmpty()
        val fast = prefs[fastVfKey].orEmpty()
        if (bellSet.isEmpty() || fast.isEmpty()) return@withContext emptyList()
        val notified = prefs[notifiedKey].orEmpty()
        val alerted = prefs[lateAlertedKey].orEmpty()
        val now = Instant.now().toEpochMilli()
        val late = episodes(daysBack = 3, daysAhead = 0)
            .filter { it.seriesId in bellSet && it.seriesId in fast && it.key !in notified && it.key !in alerted }
            .filter { isLateVf666(it.airInstant.toEpochMilli(), now) }
            .filter { checkVf(it) == VfState.NOT_YET }
        if (late.isNotEmpty()) {
            store().edit { it[lateAlertedKey] = (it[lateAlertedKey].orEmpty() + late.map { e -> e.key }).takeLast(MAX_REMEMBERED) }
        }
        late
    }

    /** 666TVF : le programme de l'écran de veille — prochains épisodes des séries avec la cloche. */
    suspend fun upcomingBells666(limit: Int = 3): List<CalendarEpisode> = withContext(Dispatchers.IO) {
        val bellSet = store().data.first()[bellsKey].orEmpty()
        if (bellSet.isEmpty()) return@withContext emptyList()
        val now = Instant.now()
        episodes(daysBack = 0, daysAhead = 8)
            .filter { it.seriesId in bellSet && it.airInstant.isAfter(now) }
            .sortedBy { it.airInstant }
            .take(limit)
    }

    private fun Set<String>.takeLast(n: Int): Set<String> = if (size <= n) this else toList().takeLast(n).toSet()

    private fun String.isSeriesType(): Boolean =
        equals("series", ignoreCase = true) || equals("tv", ignoreCase = true) || equals("show", ignoreCase = true)

    private companion object {
        const val TAG = "Calendar666"
        const val FEATURE = "player_settings"
        val VF_CHECKERS: List<String> get() = com.nuvio.tv.core.tools.PluginOrder666.order.map { it.lowercase() }
        const val MAX_SERIES = 120
        const val MAX_REMEMBERED = 400
        const val META_TTL_MS = 6L * 60 * 60 * 1000
        const val VF_NEGATIVE_TTL_MS = 30L * 60 * 1000
    }
}

/** 666TVF : VF arrivée moins de 36 h après la diffusion → série « rapide ». */
internal fun isFastVf666(airMs: Long, announcedMs: Long): Boolean =
    announcedMs >= airMs && announcedMs - airMs <= 36L * 3600_000

/** 666TVF : toujours sans VF entre 18 h et 72 h après la diffusion → retard à signaler. */
internal fun isLateVf666(airMs: Long, nowMs: Long): Boolean =
    nowMs - airMs in (18L * 3600_000)..(72L * 3600_000)
