package com.nuvio.tv.core.player

import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

object StreamAutoPlaySelector {
    /**
     * 666TVF : ordre des sources dans « Tous » et dans les pastilles : celui choisi
     * dans Outils 666 → « Ordre des cavaliers » (par défaut Comet666 d'abord).
     * Les autres sources gardent leur ordre, après celles-ci.
     */
    val PLUGIN_666_ORDER: List<String> get() = com.nuvio.tv.core.tools.PluginOrder666.order

    fun plugin666Rank(name: String): Int =
        PLUGIN_666_ORDER.indexOfFirst { it.equals(name.trim(), ignoreCase = true) }
            .let { if (it < 0) Int.MAX_VALUE else it }

    fun orderAddonStreams(
        streams: List<AddonStreams>,
        installedOrder: List<String>
    ): List<AddonStreams> {
        if (streams.isEmpty()) return streams

        val addonRankByName = HashMap<String, Int>(installedOrder.size)
        installedOrder.forEachIndexed { index, addonName ->
            if (addonName !in addonRankByName) {
                addonRankByName[addonName] = index
            }
        }

        val (directDebridEntries, remainingEntries) = streams.partition {
            it.streams.any { stream -> stream.isDirectDebrid() }
        }
        if (installedOrder.isEmpty()) {
            return (directDebridEntries + remainingEntries).sortedBy { plugin666Rank(it.addonName) }.map(::dvOnlyLast666)
        }
        val (addonEntries, pluginEntries) = remainingEntries.partition { it.addonName in addonRankByName }
        val orderedAddons = addonEntries.sortedBy { addonRankByName.getValue(it.addonName) }
        return (directDebridEntries + orderedAddons + pluginEntries).sortedBy { plugin666Rank(it.addonName) }.map(::dvOnlyLast666)
    }

    /**
     * 666TVF : dans une source (ou un dépôt regroupé « Les 4 cavaliers »), l'ordre des cavaliers
     * choisi dans Outils 666, puis le Dolby Vision sans HDR en bas (image violette, 28/10).
     */
    private fun dvOnlyLast666(group: AddonStreams): AddonStreams {
        val mixed = group.streams.mapNotNull { cavalier666(it) }.distinct().size > 1
        if (!mixed && group.streams.none { isDvOnly(it) }) return group
        return group.copy(streams = group.streams.sortedWith(
            compareBy<Stream> { if (mixed) cavalier666(it)?.let(::plugin666Rank) ?: Int.MAX_VALUE else 0 }
                .thenBy { if (isDvOnly(it)) 1 else 0 }
        ))
    }

    /**
     * 666TVF : le cavalier qui a fourni ce flux (Comet666, Lumio666…), même quand les plugins
     * sont regroupés par dépôt. Lu dans le groupe Binge « 666|<plugin>|<qualité> ».
     */
    fun cavalier666(stream: Stream): String? = cavalierFromBinge666(stream.behaviorHints?.bingeGroup)

    fun cavalierFromBinge666(bingeGroup: String?): String? {
        val id = bingeGroup?.takeIf { it.startsWith("666|") }?.split("|")?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return com.nuvio.tv.core.tools.PluginOrder666.DEFAULT.firstOrNull { it.equals(id, ignoreCase = true) } ?: id
    }

    private fun isPlayable(stream: Stream): Boolean {
        // External URL streams (e.g. error pages, web links) are not playable.
        if (stream.isExternal()) return false
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CHECKING,
            StreamDebridCacheState.NOT_CACHED,
            StreamDebridCacheState.UNKNOWN -> return false
            StreamDebridCacheState.CACHED,
            null -> Unit
        }
        return stream.getStreamUrl() != null || stream.isTorrent() || stream.isDirectDebrid()
    }



    private val SXE = Regex("""(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})(?:[ ._-]?(?:-|E)[ ._-]?E?(\d{1,3}))?""")
    private val NXN = Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b""")

    /**
     * 666TV : 0 = le flux annonce bien l'épisode demandé, 1 = rien d'indiqué (pack, ou source
     * qui n'écrit pas l'épisode), 2 = le flux annonce un AUTRE épisode → à éviter en lecture auto.
     */
    fun episodeRank(stream: Stream, season: Int?, episode: Int?): Int {
        if (season == null || episode == null) return 0
        val text = listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints?.filename)
            .joinToString(" ")
        val found = ArrayList<Triple<Int, Int, Int>>()
        SXE.findAll(text).forEach { m ->
            val s = m.groupValues[1].toIntOrNull() ?: return@forEach
            val e1 = m.groupValues[2].toIntOrNull() ?: return@forEach
            val e2 = m.groupValues[3].toIntOrNull() ?: e1
            found += Triple(s, e1, maxOf(e1, e2))
        }
        NXN.findAll(text).forEach { m ->
            val s = m.groupValues[1].toIntOrNull() ?: return@forEach
            val e = m.groupValues[2].toIntOrNull() ?: return@forEach
            found += Triple(s, e, e)
        }
        // 666TVF : émission datée (WWE Raw) : un fichier daté d'un autre jour est un autre épisode
        if (dateMismatch(text, airDateFor(season, episode))) return 2
        if (found.isEmpty()) return 1
        return if (found.any { (s, a, b) -> s == season && episode in a..b }) 0 else 2
    }

    /**
     * 666TVF : date de diffusion de l'épisode en cours (posée par l'écran des flux, qui a la fiche).
     * Sert à reconnaître un vieux WWE Raw (2026.10.19 au lieu de 2026.10.26, 27/10).
     */
    @Volatile var airDateHint666: Triple<Int, Int, String>? = null

    fun airDateFor(season: Int?, episode: Int?): String? =
        airDateHint666?.takeIf { it.first == season && it.second == episode }?.third

    private val DATED = Regex("""\b(20\d\d)[ ._-](\d\d)[ ._-](\d\d)\b""")

    /** Vrai si le texte porte une date qui n'est pas celle de l'épisode (± 1 jour 1/2). */
    fun dateMismatch(text: String, airDate: String?): Boolean {
        val target = airDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return false
        var any = false
        for (m in DATED.findAll(text)) {
            val d = runCatching {
                java.time.LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull() ?: continue
            any = true
            if (kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(target, d)) <= 1) return false
        }
        return any
    }

    private val DV = Regex("""(?i)(?<![a-z0-9])(DV|DoVi|Dolby[ ._-]?Vision)(?![a-z0-9])""")
    private val HDR = Regex("""(?i)(?<![a-z0-9])(HDR|HDR10|HDR10\+|HDR10Plus|HLG)(?![a-z0-9])""")

    /**
     * 666TVF : Dolby Vision sans couche HDR10 de secours → image violette sur la Shield du bas
     * (28/10). Jamais lancé automatiquement, et rangé derrière les autres dans la liste.
     */
    fun isDvOnly(stream: Stream): Boolean {
        val text = listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints?.filename)
            .joinToString(" ")
        return DV.containsMatchIn(text) && !HDR.containsMatchIn(text)
    }

    /** Ce que la lecture auto et le lien suivant ne lancent jamais (s'il existe autre chose). */
    fun autoPlayBlocked(stream: Stream, season: Int?, episode: Int?): Boolean =
        episodeRank(stream, season, episode) >= 2 || isDvOnly(stream)

    private fun isUncertainFrench(stream: Stream): Boolean =
        listOf(stream.name, stream.title, stream.description).any { it?.contains("VF ?") == true }

    fun selectAutoPlayStream(
        streams: List<Stream>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false,
        season: Int? = null,
        episode: Int? = null
    ): Stream? {
        if (streams.isEmpty()) return null
        // 666TV : jamais de lecture auto d'un flux qui annonce un autre épisode.
        // 666TVF : ni un autre épisode (vieux WWE Raw), ni du Dolby Vision sans HDR (image violette).
        val epStreams = streams.filter { !autoPlayBlocked(it, season, episode) }
        @Suppress("NAME_SHADOWING")
        val streams = if (epStreams.isNotEmpty()) epStreams else streams

        val effectiveSource = if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY
        } else {
            source
        }

        val sourceScopedStreams = when (effectiveSource) {
            StreamAutoPlaySource.ALL_SOURCES -> streams
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { it.addonName in installedAddonNames }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { it.addonName !in installedAddonNames }
        }
        val candidateStreams = sourceScopedStreams.filter { stream ->
            val isAddonStream = stream.addonName in installedAddonNames
            if (isAddonStream) {
                selectedAddons.isEmpty() || stream.addonName in selectedAddons
            } else {
                selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        }
        if (candidateStreams.isEmpty()) return null

        // Binge group matching takes priority over mode — even in MANUAL mode,
        // a persisted binge group should auto-play without showing the picker.
        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            val bingeGroupMatch = candidateStreams.firstOrNull { stream ->
                stream.behaviorHints?.bingeGroup == targetBingeGroup && isPlayable(stream)
            }
            if (bingeGroupMatch != null) return bingeGroupMatch
            // When bingeGroupOnly is set (MANUAL mode with only binge-group
            // preference enabled), don't fall back to a non-matching stream —
            // return null so the caller shows the stream picker instead.
            if (bingeGroupOnly) return null
        }

        if (bingeGroupOnly) return null

        if (mode == StreamAutoPlayMode.MANUAL) return null

        return when (mode) {
            StreamAutoPlayMode.MANUAL -> null
            // 666TV : meilleure source d'abord — VF sûre avant « VF ? », puis la qualité
            // (ordre d'origine conservé à égalité).
            StreamAutoPlayMode.FIRST_STREAM -> candidateStreams
                .filter { isPlayable(it) }
                .sortedWith(
                    compareBy<Stream> { if (isUncertainFrench(it)) 1 else 0 }
                        .thenBy { episodeRank(it, season, episode) }
                        .thenByDescending { it.qualityValue }
                )
                .firstOrNull()
            StreamAutoPlayMode.REGEX_MATCH -> {
                val pattern = regexPattern.trim()
 
                // Try to compile the user regex
                val userRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                if (userRegex == null) return null

                // Auto-extract exclusion patterns from negative lookaheads
                val exclusionMatches = Regex("\\(\\?![^)]*?\\(([^)]+)\\)").findAll(pattern)

                val exclusionWords = exclusionMatches
                    .flatMap { match -> match.groupValues[1].split("|") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val excludeRegex = if (exclusionWords.isNotEmpty()) {
                    Regex("\\b(${exclusionWords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
                } else null

                // 1. Build list of ALL regex‑matching streams
                val matchingStreams = candidateStreams.filter { stream ->
                    if (!isPlayable(stream)) return@filter false

                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.title.orEmpty()).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(stream.getStreamUrl().orEmpty())
                        if (stream.isTorrent()) append(' ').append(stream.infoHash.orEmpty())
                    }

                    // Must match include pattern
                    if (!userRegex.containsMatchIn(searchableText)) return@filter false

                    // Must NOT match exclusion pattern
                    if (excludeRegex != null && excludeRegex.containsMatchIn(searchableText)) {
                        return@filter false
                    }

                    true
                }

                if (matchingStreams.isEmpty()) return null
                matchingStreams.firstOrNull { isPlayable(it) }
            }

        }
    }
}
