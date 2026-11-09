package com.nuvio.tv.core.tools

import com.nuvio.tv.core.poster.withCustomPosterUrl
import com.nuvio.tv.domain.model.MetaPreview

/**
 * 666TVF (09/11) : affiches de secours. Des affiches passaient par Frank (mis en pause par
 * Hugging Face) : Shudder, Prochainement… s'affichaient sans image. Une affiche de Frank, absente
 * ou qui ne charge pas → l'affiche publique du titre (Metahub, par l'identifiant IMDb).
 */
object Posters666 {
    fun isDead(url: String?): Boolean =
        url != null && (url.contains("frankenstream", ignoreCase = true) || url.contains("french-stream-enhanced", ignoreCase = true))

    /** Affiche de secours (règle pure, testée), ou null si on n'a pas d'identifiant IMDb. */
    fun fallback(id: String?, imdbId: String? = null): String? {
        val tt = listOfNotNull(imdbId, id?.substringBefore(':'))
            .firstOrNull { Regex("^tt\\d+$").matches(it) } ?: return null
        return "https://images.metahub.space/poster/medium/$tt/img"
    }

    /** L'affiche à utiliser : celle d'origine, sauf si elle vient de Frank ou manque. */
    fun pick(poster: String?, id: String?, imdbId: String? = null): String? =
        if (poster.isNullOrBlank() || isDead(poster)) fallback(id, imdbId) ?: poster?.takeUnless { isDead(it) } else poster

    /**
     * 666TVF (09/11, 3) : les affiches BetterPosters (btttr.cc) qu'avaient les listes de Frank.
     * Ton modèle d'affiche (Réglages → affiches personnalisées) s'il y en a un, sinon BetterPosters
     * par défaut. Il faut l'identifiant IMDb ; sinon l'affiche reste celle d'origine.
     */
    fun better(item: MetaPreview, pattern: String): MetaPreview {
        val tt = listOfNotNull(item.imdbId, item.id.substringBefore(':')).firstOrNull { Regex("^tt\\d+$").matches(it) }
            ?: return item
        val withId = item.copy(imdbId = tt)
        if (pattern.isNotBlank()) return withId.withCustomPosterUrl(pattern)
        return withId.copy(poster = "https://btttr.cc/poster/imdb/poster-default/$tt.jpg", rawPosterUrl = item.rawPosterUrl ?: item.poster)
    }
}
