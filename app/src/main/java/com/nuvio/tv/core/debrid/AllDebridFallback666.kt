package com.nuvio.tv.core.debrid

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus

/**
 * 666TVF (07/11) : liens de nos plugins que TorBox n'a pas en cache.
 * Avant, ils étaient masqués. Maintenant, si AllDebrid est configuré :
 *  - le torrent est déjà prêt dans le compte AllDebrid → « ⚡ AllDebrid ✓ » (lisible, comme un lien en cache) ;
 *  - sinon → « ⚡ AllDebrid ? » (peut-être en cache chez AllDebrid, rangé en dernier, jamais en lecture auto).
 * Ces liens passent directement par AllDebrid (jamais par TorBox : pas de téléchargement lancé).
 */
object AllDebridFallback666 {
    const val PROVIDER_ID = "alldebrid666"
    const val NAME_READY = "AllDebrid ✓"
    const val NAME_GUESS = "AllDebrid ?"
    /** Nombre max de liens « ? » proposés par réponse de plugin (pas de liste interminable). */
    const val MAX_GUESS = 6

    fun isFallback(stream: Stream): Boolean = stream.debridCacheStatus?.providerId == PROVIDER_ID

    fun isGuess(stream: Stream): Boolean =
        isFallback(stream) && stream.debridCacheStatus?.state != StreamDebridCacheState.CACHED

    /** Ligne affichée sur la carte du lien, ou null. */
    fun label(stream: Stream): String? = when {
        !isFallback(stream) -> null
        isGuess(stream) -> "⚡ $NAME_GUESS"
        else -> "⚡ $NAME_READY"
    }

    /**
     * Règle pure (testée). [ready] = empreintes (hash) prêtes dans le compte AllDebrid.
     * Les liens en cache TorBox restent devant, puis les ✓, puis les ? (au plus [MAX_GUESS]).
     * Sans AllDebrid (ou P2P actif) : comportement d'avant (on ne touche à rien).
     */
    fun relabel(streams: List<Stream>, allDebridConfigured: Boolean, ready: Set<String>): List<Stream> {
        if (!allDebridConfigured) return streams
        val keep = ArrayList<Stream>(streams.size)
        val readyOnes = ArrayList<Stream>()
        val guesses = ArrayList<Stream>()
        for (stream in streams) {
            val notCached = stream.debridCacheStatus?.state == StreamDebridCacheState.NOT_CACHED
            val ours = stream.behaviorHints?.bingeGroup?.startsWith("666|") == true
            val hash = stream.getEffectiveInfoHash()?.trim()?.lowercase()
            if (!notCached || !ours || hash.isNullOrBlank()) { keep += stream; continue }
            if (hash in ready) {
                readyOnes += stream.copy(
                    debridCacheStatus = StreamDebridCacheStatus(PROVIDER_ID, NAME_READY, StreamDebridCacheState.CACHED)
                )
            } else if (guesses.size < MAX_GUESS) {
                guesses += stream.copy(
                    debridCacheStatus = StreamDebridCacheStatus(PROVIDER_ID, NAME_GUESS, StreamDebridCacheState.UNKNOWN)
                )
            } else {
                keep += stream // reste NOT_CACHED → masqué comme avant
            }
        }
        return keep + readyOnes + guesses
    }
}
