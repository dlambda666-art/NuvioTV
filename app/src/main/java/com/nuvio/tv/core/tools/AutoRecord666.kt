package com.nuvio.tv.core.tools

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.calendar.CalendarEpisode
import com.nuvio.tv.core.debrid.DirectDebridResolveResult
import com.nuvio.tv.core.debrid.DirectDebridResolver
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.player.StreamAutoPlaySelector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.repository.StreamRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 666TVF (30/10) : la suite des enregistrements, Shield du bas.
 *  - quand la cloche trouve un nouvel épisode VF d'une série suivie, il s'enregistre tout seul
 *    (1080p VF qui tient sur la clé, jamais de 4K ni de 720p, jamais un autre épisode) ;
 *  - « Vu ✓ » quand tu l'as regardé, et effacé tout seul 3 jours après (sauf « Garder »).
 */
@Singleton
class AutoRecord666 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val streamRepository: StreamRepository,
    private val directDebridResolver: DirectDebridResolver
) {
    private val prefs by lazy { context.getSharedPreferences("666tv_auto_rec", Context.MODE_PRIVATE) }

    data class Pending(
        val seriesId: String,
        val seriesName: String,
        val poster: String?,
        val season: Int,
        val episode: Int,
        val title: String?,
        val tries: Int,
        val nextAt: Long,
        /** 11/11 : jour de diffusion (retrouver le bon numéro si la source a numéroté autrement). */
        val air: Long = 0L,
        val why: String? = null
    ) {
        val key get() = "$seriesId:$season:$episode"
    }

    /** Pour l'écran Enregistrements : ce qui attend un lien (films et épisodes). */
    data class Waiting(val label: String, val poster: String?, val tries: Int, val nextAt: Long, val why: String?)

    var enabled: Boolean
        get() = prefs.getBoolean("on", true)
        set(v) { prefs.edit().putBoolean("on", v).apply() }

    /** La cloche vient d'annoncer ces épisodes en VF : on les met de côté pour les enregistrer. */
    fun offer(episodes: List<CalendarEpisode>) {
        if (!enabled || !Record666.enabledHere()) return
        val real = episodes.filter { !it.seriesId.contains(':') && it.seriesId.startsWith("tt") && it.season > 0 && it.episode > 0 }
        if (real.isEmpty()) return
        val seen = prefs.getStringSet("seen", emptySet())!!.toMutableSet()
        val list = pending().toMutableList()
        real.forEach { ep ->
            // 11/11 : même épisode numéroté autrement (TMDB au lieu de Cinemeta) → pas deux enregistrements
            if (ep.key in seen || ep.dayKeys666.any { it in seen }) return@forEach
            seen += ep.key
            seen += ep.dayKey666
            list += Pending(ep.seriesId, ep.seriesName, ep.poster, ep.season, ep.episode, ep.title, 0, 0, ep.airInstant.toEpochMilli())
        }
        prefs.edit().putStringSet("seen", seen.toList().takeLast(500).toSet()).apply()
        save(list)
    }

    fun pending(): List<Pending> = runCatching {
        val arr = JSONArray(prefs.getString("pending", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Pending(
                o.getString("id"), o.optString("name"), o.optString("poster").ifBlank { null },
                o.getInt("s"), o.getInt("e"), o.optString("title").ifBlank { null },
                o.optInt("tries"), o.optLong("next"), o.optLong("air"), o.optString("why").ifBlank { null }
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Pending>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.seriesId).put("name", p.seriesName).put("poster", p.poster ?: "")
                .put("s", p.season).put("e", p.episode).put("title", p.title ?: "")
                .put("tries", p.tries).put("next", p.nextAt).put("air", p.air).put("why", p.why ?: ""))
        }
        prefs.edit().putString("pending", arr.toString()).apply()
    }

    // ---------- Films trouvés en VF par le guetteur (04/11) ----------

    data class FilmPending(val imdbId: String, val name: String, val poster: String?, val tries: Int, val nextAt: Long, val why: String? = null)

    /** Le guetteur vient de crier pour ces films : on les enregistre aussi (Shield du bas). */
    fun offerFilms(films: List<VfWatcher666.Found>) {
        if (!enabled || !Record666.enabledHere()) return
        val real = films.filter { it.imdbId.startsWith("tt") }
        if (real.isEmpty()) return
        val seen = prefs.getStringSet("films_seen", emptySet())!!.toMutableSet()
        val list = filmsPending().toMutableList()
        real.forEach { f ->
            if (f.imdbId in seen) return@forEach
            seen += f.imdbId
            list += FilmPending(f.imdbId, f.name, f.poster, 0, 0)
        }
        prefs.edit().putStringSet("films_seen", seen.toList().takeLast(500).toSet()).apply()
        saveFilms(list)
    }

    fun filmsPending(): List<FilmPending> = runCatching {
        val arr = JSONArray(prefs.getString("films", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FilmPending(o.getString("id"), o.optString("name"), o.optString("poster").ifBlank { null }, o.optInt("tries"), o.optLong("next"), o.optString("why").ifBlank { null })
        }
    }.getOrDefault(emptyList())

    private fun saveFilms(list: List<FilmPending>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().put("id", f.imdbId).put("name", f.name).put("poster", f.poster ?: "").put("tries", f.tries).put("next", f.nextAt).put("why", f.why ?: ""))
        }
        prefs.edit().putString("films", arr.toString()).apply()
    }

    private suspend fun runFilms(busy: () -> Boolean) {
        val now = System.currentTimeMillis()
        for (f in filmsPending()) {
            if (busy()) return
            if (f.nextAt > now) continue
            val id = Record666.idFor(f.imdbId, null, null, null)
            if (Record666.items.value.any { it.id == id && it.status != Record666.Status.FAILED }) { dropFilm(f); continue }
            val msg = runCatching { tryRecordFilm(f) }.getOrElse { Log.w(TAG, "auto film ${f.imdbId}", it); null }
            if (msg != null) {
                dropFilm(f)
                Announce666.log(context, "⬇ Enregistrement auto lancé : ${f.name}", "enregistrement")
            } else if (f.tries + 1 >= MAX_TRIES) {
                dropFilm(f)
                Announce666.log(context, "Enregistrement auto : pas de lien 1080p VF qui tient sur le disque pour ${f.name}", "enregistrement")
            } else {
                saveFilms(filmsPending().map { if (it.imdbId == f.imdbId) it.copy(tries = it.tries + 1, nextAt = now + RETRY_MS, why = NO_LINK) else it })
            }
        }
    }

    private fun dropFilm(f: FilmPending) = saveFilms(filmsPending().filterNot { it.imdbId == f.imdbId })

    private suspend fun tryRecordFilm(f: FilmPending): String? {
        val place = Record666.place(context) ?: return null
        var last: List<Stream> = emptyList()
        withTimeoutOrNull(180_000) {
            streamRepository.getStreamsFromAllAddons("movie", f.imdbId, null, null).collect { r ->
                if (r is NetworkResult.Success) last = r.data.flatMap { it.streams }
            }
        }
        val room = minOf(place.maxFile, place.usable - Record666.queuedBytes())
        val title = f.name.replace(Regex("""\s*\((\d{4})\)\s*$"""), "")
        val year = Regex("""\((\d{4})\)\s*$""").find(f.name)?.groupValues?.get(1)
        for (s in pickFilm(last, room).take(5)) {
            val url = if (directDebridResolver.shouldResolveToPlayableStream(s)) {
                (directDebridResolver.resolve(s, null, null) as? DirectDebridResolveResult.Success)?.url
            } else s.getStreamUrl()
            if (url.isNullOrBlank() || !url.startsWith("http")) continue
            val msg = Record666.enqueue(
                context,
                Record666.Request666(
                    title = title, contentName = title, contentId = f.imdbId, contentType = "movie",
                    videoId = f.imdbId, season = null, episode = null, episodeTitle = null, year = year,
                    quality = Record666.qualityOf(textOf(s)), url = url, headers = s.behaviorHints?.proxyHeaders?.request ?: emptyMap(),
                    filename = s.behaviorHints?.filename, size = s.behaviorHints?.videoSize,
                    poster = f.poster, backdrop = null, logo = null
                ),
                auto = true
            )
            if (msg.startsWith("⬇") || msg.startsWith("Déjà")) return msg
        }
        return null
    }

    /** Un tour : cherche un lien pour chaque épisode en attente (quand tu ne regardes ni ne cherches rien). */
    suspend fun runOnce(busy: () -> Boolean, recent: suspend (String) -> List<CalendarEpisode> = { emptyList() }) {
        if (!Record666.enabledHere()) return
        Record666.init(context)
        runCatching { runFilms(busy) }
        val now = System.currentTimeMillis()
        for (p0 in pending()) {
            if (busy()) return
            if (p0.nextAt > now) continue
            // 11/11 : numéroté par TMDB (Cinemeta en panne) → le vrai numéro, retrouvé par le jour de diffusion
            val p = runCatching { renumbered(p0, recent(p0.seriesId)) }.getOrNull()?.also { replace(p0, it) } ?: p0
            val id = Record666.idFor(p.seriesId, null, p.season, p.episode)
            if (Record666.items.value.any { it.id == id && it.status != Record666.Status.FAILED }) { drop(p); continue }
            val msg = runCatching { tryRecord(p) }.getOrElse { Log.w(TAG, "auto ${p.key}", it); null }
            if (msg != null) {
                drop(p)
                Announce666.log(context, "⬇ Enregistrement auto lancé : ${p.seriesName} S${p.season}E${p.episode}", "enregistrement")
            } else if (p.tries + 1 >= MAX_TRIES) {
                drop(p)
                Announce666.log(context, "Enregistrement auto : pas de lien 1080p VF qui tient sur la clé pour ${p.seriesName} S${p.season}E${p.episode}", "enregistrement")
            } else {
                replace(p, p.copy(tries = p.tries + 1, nextAt = now + RETRY_MS, why = NO_LINK))
            }
        }
    }

    private fun drop(p: Pending) = save(pending().filterNot { it.key == p.key })
    private fun replace(old: Pending, new: Pending) = save(pending().map { if (it.key == old.key) new else it })

    /** Renvoie le message de Record666.enqueue si lancé, sinon null. */
    private suspend fun tryRecord(p: Pending): String? {
        val place = Record666.place(context) ?: return null
        val videoId = "${p.seriesId}:${p.season}:${p.episode}"
        var last: List<Stream> = emptyList()
        withTimeoutOrNull(180_000) {
            streamRepository.getStreamsFromAllAddons("series", videoId, p.season, p.episode).collect { r ->
                if (r is NetworkResult.Success) last = r.data.flatMap { it.streams }
            }
        }
        val room = minOf(place.maxFile, place.usable - Record666.queuedBytes())
        val candidates = pickAll(last, p.season, p.episode, room)
        for (s in candidates.take(5)) {
            val url = if (directDebridResolver.shouldResolveToPlayableStream(s)) {
                (directDebridResolver.resolve(s, p.season, p.episode) as? DirectDebridResolveResult.Success)?.url
            } else s.getStreamUrl()
            if (url.isNullOrBlank() || !url.startsWith("http")) continue
            val msg = Record666.enqueue(
                context,
                Record666.Request666(
                    title = p.seriesName, contentName = p.seriesName, contentId = p.seriesId, contentType = "series",
                    videoId = videoId, season = p.season, episode = p.episode, episodeTitle = p.title, year = null,
                    quality = Record666.qualityOf(textOf(s)), url = url, headers = s.behaviorHints?.proxyHeaders?.request ?: emptyMap(),
                    filename = s.behaviorHints?.filename, size = s.behaviorHints?.videoSize,
                    poster = p.poster, backdrop = null, logo = null
                ),
                auto = true
            )
            if (msg.startsWith("⬇") || msg.startsWith("Déjà")) return msg
        }
        return null
    }

    // ---------- Vu / ménage ----------

    /** Marque « Vu » ce que tu as regardé, efface ce qui est vu depuis plus de 3 jours. */
    suspend fun sweep(repo: WatchProgressRepository) {
        if (!Record666.enabledHere()) return
        Record666.init(context)
        val now = System.currentTimeMillis()
        Record666.items.value.filter { it.status == Record666.Status.DONE && it.watchedAt == 0L }.forEach { r ->
            val cid = r.contentId ?: return@forEach
            val watched = withTimeoutOrNull(5_000) {
                runCatching { repo.isWatched(cid, r.videoId, r.season, r.episode).first() }.getOrDefault(false)
            } == true
            if (watched) Record666.markWatched(context, r.id, now)
        }
        Record666.items.value.filter { Record666.shouldAutoDelete(it, now) }.forEach { r ->
            Record666.delete(context, r.id)
            Announce666.log(context, "🗑 Effacé (vu il y a 3 jours) : ${r.label}", "enregistrement")
        }
    }

    companion object {
        private const val TAG = "AutoRecord666"

        /** 666TVF (06/11) : oublie des épisodes notés sous une mauvaise numérotation (et leurs attentes). */
        fun forget(context: Context, keys: Set<String>) {
            val p = context.getSharedPreferences("666tv_auto_rec", Context.MODE_PRIVATE)
            val seen = p.getStringSet("seen", emptySet()).orEmpty() - keys
            val pend = runCatching {
                val arr = JSONArray(p.getString("pending", "[]")); val out = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if ("${o.getString("id")}:${o.getInt("s")}:${o.getInt("e")}" !in keys) out.put(o)
                }
                out.toString()
            }.getOrNull()
            p.edit().putStringSet("seen", seen).apply { if (pend != null) putString("pending", pend) }.apply()
        }
        const val NO_LINK = "Pas encore de lien 1080p VF prêt"

        /**
         * Règle pure (testée) : le même épisode dans le calendrier du moment, s'il porte un autre numéro.
         * Par le jour de diffusion ; sans jour (anciennes attentes) : si ce numéro n'existe pas dans le
         * calendrier, le dernier épisode sorti de la série. Null si rien à changer.
         */
        fun renumbered(p: Pending, cal: List<CalendarEpisode>, now: Long = System.currentTimeMillis()): Pending? {
            val mine = cal.filter { it.seriesId == p.seriesId && it.season > 0 && it.episode > 0 && it.airInstant.toEpochMilli() <= now }
            if (mine.isEmpty() || mine.any { it.season == p.season && it.episode == p.episode }) return null
            val match = if (p.air > 0) {
                val day = java.time.Instant.ofEpochMilli(p.air).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                mine.firstOrNull { e -> e.dayKeys666.contains("${p.seriesId}@$day") }
            } else mine.maxByOrNull { it.airInstant }
            match ?: return null
            return p.copy(season = match.season, episode = match.episode, title = match.title ?: p.title, air = match.airInstant.toEpochMilli())
        }

        fun waiting(context: Context): List<Waiting> {
            val prefs = context.getSharedPreferences("666tv_auto_rec", Context.MODE_PRIVATE)
            val films = runCatching {
                val arr = JSONArray(prefs.getString("films", "[]"))
                (0 until arr.length()).map { i -> arr.getJSONObject(i) }.map { o ->
                    Waiting(o.optString("name"), o.optString("poster").ifBlank { null }, o.optInt("tries"), o.optLong("next"), o.optString("why").ifBlank { null })
                }
            }.getOrDefault(emptyList())
            val eps = runCatching {
                val arr = JSONArray(prefs.getString("pending", "[]"))
                (0 until arr.length()).map { i -> arr.getJSONObject(i) }.map { o ->
                    Waiting("${o.optString("name")} · S${o.optInt("s")}E${o.optInt("e")}", o.optString("poster").ifBlank { null },
                        o.optInt("tries"), o.optLong("next"), o.optString("why").ifBlank { null })
                }
            }.getOrDefault(emptyList())
            return films + eps
        }

        const val MAX_TRIES = 8
        const val RETRY_MS = 3L * 3600 * 1000

        private val LOW = Regex("""(?i)(?<![0-9])(720p|576p|480p|360p)|\bSD\b|\bCAM\b|\bTS\b""")
        private val UHD = Regex("""(?i)2160p|\b4k\b|\bUHD\b""")

        fun textOf(s: Stream): String =
            listOfNotNull(s.name, s.title, s.description, s.behaviorHints?.filename).joinToString(" ")

        /**
         * Règle pure (testée) : les liens enregistrables tout seuls, du meilleur au moins bon.
         * 1080p, VF sûre (pas « VF ? »), le bon épisode, taille connue qui tient, pas de DV sans HDR,
         * pas « pas en cache ». Ordre : épisode écrit d'abord, puis l'ordre des cavaliers (celui de la liste).
         */
        fun pickAll(streams: List<Stream>, season: Int?, episode: Int?, roomBytes: Long): List<Stream> =
            streams.withIndex().filter { (_, s) ->
                val t = textOf(s)
                val size = s.behaviorHints?.videoSize ?: 0L
                !s.isExternal() &&
                    s.debridCacheStatus?.state.let { it == null || it == StreamDebridCacheState.CACHED } &&
                    (s.getStreamUrl() != null || s.isTorrent() || s.isDirectDebrid()) &&
                    !t.contains("VF ?") &&
                    "1080" in t && !UHD.containsMatchIn(t) && !LOW.containsMatchIn(t) &&
                    size in 300L * 1024 * 1024..roomBytes &&
                    !StreamAutoPlaySelector.autoPlayBlocked(s, season, episode)
            }.sortedWith(
                compareBy<IndexedValue<Stream>> { StreamAutoPlaySelector.episodeRank(it.value, season, episode) }
                    .thenBy { it.index }
            ).map { it.value }

        /** Règle pure (testée) : un film trouvé par le guetteur — comme un épisode, et en VF sûre seulement. */
        fun pickFilm(streams: List<Stream>, roomBytes: Long): List<Stream> =
            pickAll(streams, null, null, roomBytes).filter { FrankVf666.isSureVfRelease(textOf(it)) }
    }
}
