package com.nuvio.tv.core.debrid

/**
 * 666TV : formateur par défaut dans l'esprit de celui de Duck (emojis) :
 * 🎞️ résolution + source, puis 🎬 titre, 🗣️ 🇫🇷 VFF/VFQ…, 📺 qualité, 🔊 audio,
 * 📦 taille, ⚡ prêt. Modifiable dans Intégrations → Debrid → Éditeur web.
 */
object DebridStreamFormatterDefaults {
    const val NAME_TEMPLATE = "{stream.resolution::=2160p[\"🎞️ 4K\"||\"\"]}{stream.resolution::=1440p[\"🎞️ 1440p\"||\"\"]}{stream.resolution::=1080p[\"🎞️ 1080p\"||\"\"]}{stream.resolution::=720p[\"🎞️ 720p\"||\"\"]} {addon.name}"

    const val DESCRIPTION_TEMPLATE = "{stream.title::exists[\"🎬 {stream.title::title}\"||\"\"]}{stream.year::exists[\" ({stream.year})\"||\"\"]}{stream.seasonEpisode::exists[\" · {stream.seasonEpisode::join(' ')}\"||\"\"]}\n{stream.languageEmojis::exists[\"🗣️ {stream.languageEmojis::join(' ')}\"||\"\"]}\n{stream.quality::exists[\"📺 {stream.quality} \"||\"\"]}{stream.visualTags::exists[\"✨ {stream.visualTags::join(' · ')} \"||\"\"]}{stream.encode::exists[\"⚙️ {stream.encode}\"||\"\"]}\n{stream.audioTags::exists[\"🔊 {stream.audioTags::join(' · ')} \"||\"\"]}{stream.audioChannels::exists[\"{stream.audioChannels::join(' ')}\"||\"\"]}\n{stream.size::>0[\"📦 {stream.size::bytes} \"||\"\"]}{stream.releaseGroup::exists[\"🏷️ {stream.releaseGroup}\"||\"\"]}\n{service.cached::istrue[\"⚡ Prêt\"||\"⏳ Pas prêt\"]}{service.shortName::exists[\" ({service.shortName})\"||\"\"]}"
}
