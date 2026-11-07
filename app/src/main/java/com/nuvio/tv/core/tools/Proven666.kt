package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.domain.model.Stream

/**
 * 666TVF (07/11) : historique des liens qui marchent.
 * Quand un épisode d'une série se lance bien, on retient l'équipe de la release (« -TEAM » à la fin
 * du nom). À l'épisode suivant de la même série, les liens de cette équipe passent en tête
 * (liste et lecture auto), à qualité VF égale : moins de tâtonnement (NXT, Raw, séries suivies).
 */
object Proven666 {
    private const val PREFS = "666tv_proven"
    const val MAX_TEAMS = 3

    /** Série en cours de recherche → équipes qui ont marché (lu par la lecture auto). */
    @Volatile var active: Pair<String, List<String>>? = null
        private set

    private val NOT_TEAM = setOf(
        "MKV", "MP4", "AVI", "X264", "X265", "H264", "H265", "HEVC", "WEB", "WEBDL", "WEBRIP", "DL", "HDR", "DV",
        "REMUX", "BLURAY", "MULTI", "FRENCH", "VFF", "VFQ", "VOSTFR", "AAC", "AC3", "DTS", "ATMOS", "SDR",
        "1080P", "2160P", "1440P", "720P", "PROPER", "REPACK"
    )

    /** Équipe de la release (même règle que Comet666), ou null. */
    fun teamOf(release: String?): String? {
        val line = release?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            .replace(Regex("\\s*\\[[^\\]]*]\\s*$"), "")
            .replace(Regex("\\.(mkv|mp4|avi|ts|m2ts)$", RegexOption.IGNORE_CASE), "")
        val g = Regex("-([A-Za-z0-9][A-Za-z0-9_]{1,19})$").find(line)?.groupValues?.get(1)?.uppercase() ?: return null
        return if (g in NOT_TEAM || g.all { it.isDigit() }) null else g
    }

    fun teamOf(stream: Stream): String? = teamOf(stream.title) ?: teamOf(stream.behaviorHints?.filename)

    /** Id de série d'un videoId « tt123:1:5 » (null pour un film). */
    fun seriesOf(videoId: String): String? = videoId.takeIf { it.count { c -> c == ':' } >= 2 }?.substringBefore(':')

    /** Règle pure (testée) : nouvelle liste d'équipes, la plus récente d'abord, sans doublon. */
    fun pushTeam(teams: List<String>, team: String): List<String> =
        (listOf(team) + teams.filterNot { it == team }).take(MAX_TEAMS)

    /** Règle pure (testée) : 0 = équipe qui a déjà marché (la plus récente d'abord), sinon MAX_TEAMS. */
    fun rank(stream: Stream, teams: List<String>): Int {
        val t = teamOf(stream) ?: return MAX_TEAMS
        val i = teams.indexOf(t)
        return if (i < 0) MAX_TEAMS else i
    }

    /** Rang pour la lecture auto (série en cours). */
    fun activeRank(stream: Stream): Int = active?.second?.let { rank(stream, it) } ?: MAX_TEAMS

    fun teams(context: Context, seriesId: String): List<String> =
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(seriesId, "").orEmpty()
                .split(',').filter { it.isNotBlank() }
        }.getOrDefault(emptyList())

    /** Appelé au début d'une recherche de liens. */
    fun begin(context: Context, videoId: String): List<String> {
        val series = seriesOf(videoId)
        val teams = series?.let { teams(context, it) }.orEmpty()
        active = series?.let { it to teams }
        return teams
    }

    /** Appelé quand un lien s'est bien lancé. */
    fun remember(context: Context, videoId: String, stream: Stream) {
        val series = seriesOf(videoId) ?: return
        val team = teamOf(stream) ?: return
        val updated = pushTeam(teams(context, series), team)
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(series, updated.joinToString(",")).apply()
        }
        if (active?.first == series) active = series to updated
    }

    /** Ordre stable : équipes qui ont marché d'abord, le reste dans l'ordre d'origine. */
    fun sortProven(streams: List<Stream>, teams: List<String>): List<Stream> =
        if (teams.isEmpty()) streams else streams.withIndex()
            .sortedWith(compareBy<IndexedValue<Stream>> {
                // un lien « AllDebrid ? » ne passe jamais devant, même d'une équipe connue
                if (com.nuvio.tv.core.debrid.AllDebridFallback666.isGuess(it.value)) MAX_TEAMS else rank(it.value, teams)
            }.thenBy { it.index })
            .map { it.value }
}
