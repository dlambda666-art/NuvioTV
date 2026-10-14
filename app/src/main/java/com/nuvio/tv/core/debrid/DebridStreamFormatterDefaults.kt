package com.nuvio.tv.core.debrid

/**
 * 666TV : formateur perso de David.
 * Ligne 1 : la tête bleue qui parle face au drapeau (🗣️ 🇫🇷).
 * Ligne 2 : 🎬 titre (année) · S01E01.
 * Source, qualité, HDR, codec, audio et taille : en badges image (Elite Badges,
 * importés dans Paramètres → Disposition → Badges de flux) + badge de taille.
 * Modifiable dans Intégrations → Debrid → Éditeur web.
 */
object DebridStreamFormatterDefaults {
    const val NAME_TEMPLATE = "🗣️ 🇫🇷"

    const val DESCRIPTION_TEMPLATE = "{stream.title::exists[\"🎬 {stream.title::title}\"||\"\"]}{stream.year::exists[\" ({stream.year})\"||\"\"]}{stream.seasonEpisode::exists[\" · {stream.seasonEpisode::join(' ')}\"||\"\"]}"

    // Ancien formateur Nuvio (« 4K Debrid Instant » / « Ready ») resté enregistré sur
    // certaines Shield : on le remplace par celui de 666TV. Un formateur perso est gardé.
    private const val LEGACY_NAME_TEMPLATE = "{stream.resolution::=2160p[\"4K \"||\"\"]}{stream.resolution::=1440p[\"QHD \"||\"\"]}{stream.resolution::=1080p[\"FHD \"||\"\"]}{stream.resolution::=720p[\"HD \"||\"\"]}{stream.resolution::exists[\"\"||\"Direct \"]}{service.shortName::exists[\"{service.shortName} \"||\"Debrid \"]}Instant"

    private const val LEGACY_DESCRIPTION_TEMPLATE = "{stream.title::exists[\"{stream.title::title} \"||\"\"]}{stream.year::exists[\"({stream.year})\"||\"\"]}\n{stream.quality::exists[\"{stream.quality} \"||\"\"]}{stream.visualTags::exists[\"{stream.visualTags::join(' | ')} \"||\"\"]}{stream.encode::exists[\"{stream.encode} \"||\"\"]}\n{stream.audioTags::exists[\"{stream.audioTags::join(' | ')}\"||\"\"]}{stream.audioTags::exists::and::stream.audioChannels::exists[\" | \"||\"\"]}{stream.audioChannels::exists[\"{stream.audioChannels::join(' | ')}\"||\"\"]}\n{stream.size::>0[\"{stream.size::bytes} \"||\"\"]}{stream.releaseGroup::exists[\"{stream.releaseGroup} \"||\"\"]}{stream.indexer::exists[\"{stream.indexer}\"||\"\"]}\n{service.cached::istrue[\"Ready\"||\"Not Ready\"]}{service.shortName::exists[\" ({service.shortName})\"||\"\"]}{stream.filename::exists[\"\n{stream.filename}\"||\"\"]}"

    // Formateur à emojis précédent (🎞️ 4K Lumio666 / 📺 🔊 📦 ⚡ Prêt).
    private const val EMOJI_NAME_TEMPLATE = "{stream.resolution::=2160p[\"🎞️ 4K\"||\"\"]}{stream.resolution::=1440p[\"🎞️ 1440p\"||\"\"]}{stream.resolution::=1080p[\"🎞️ 1080p\"||\"\"]}{stream.resolution::=720p[\"🎞️ 720p\"||\"\"]} {addon.name}"

    private const val EMOJI_DESCRIPTION_TEMPLATE = "{stream.title::exists[\"🎬 {stream.title::title}\"||\"\"]}{stream.year::exists[\" ({stream.year})\"||\"\"]}{stream.seasonEpisode::exists[\" · {stream.seasonEpisode::join(' ')}\"||\"\"]}\n{stream.languageEmojis::exists[\"🗣️ {stream.languageEmojis::join(' ')}\"||\"\"]}\n{stream.quality::exists[\"📺 {stream.quality} \"||\"\"]}{stream.visualTags::exists[\"✨ {stream.visualTags::join(' · ')} \"||\"\"]}{stream.encode::exists[\"⚙️ {stream.encode}\"||\"\"]}\n{stream.audioTags::exists[\"🔊 {stream.audioTags::join(' · ')} \"||\"\"]}{stream.audioChannels::exists[\"{stream.audioChannels::join(' ')}\"||\"\"]}\n{stream.size::>0[\"📦 {stream.size::bytes} \"||\"\"]}{stream.releaseGroup::exists[\"🏷️ {stream.releaseGroup}\"||\"\"]}\n{service.cached::istrue[\"⚡ Prêt\"||\"⏳ Pas prêt\"]}{service.shortName::exists[\" ({service.shortName})\"||\"\"]}"

    private val LEGACY_NAMES = setOf(LEGACY_NAME_TEMPLATE, EMOJI_NAME_TEMPLATE)
    private val LEGACY_DESCRIPTIONS = setOf(LEGACY_DESCRIPTION_TEMPLATE.trim(), EMOJI_DESCRIPTION_TEMPLATE.trim())

    fun upgradeName(stored: String?): String =
        if (stored.isNullOrBlank() || stored.trim() in LEGACY_NAMES) NAME_TEMPLATE else stored

    fun upgradeDescription(stored: String?): String =
        if (stored.isNullOrBlank() || stored.trim() in LEGACY_DESCRIPTIONS) DESCRIPTION_TEMPLATE else stored
}
