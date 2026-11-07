package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

/**
 * 666TVF (07/11) : hibernation des cavaliers.
 * Quand tu ouvres la liste des liens, seul le 1er cavalier (Comet666) part. Les autres ne se
 * réveillent que s'il ne trouve aucun lien VF lisible (en cache TorBox ou prêt chez AllDebrid).
 * Le guetteur et la cloche du calendrier ne changent pas : ils gardent tous les cavaliers allumés
 * en secours derrière le 1er. Réglable dans Outils 666 (activé par défaut).
 */
object Hibernate666 {
    private const val PREFS = "666tv_hibernate"

    fun enabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)
    }.getOrDefault(true)

    fun setEnabled(context: Context, on: Boolean) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply() }
    }

    /** Id court du cavalier de tête (« comet666 »). */
    fun leaderId(): String = PluginOrder666.order.firstOrNull()?.lowercase().orEmpty()

    fun isLeader(scraperId: String): Boolean = scraperId.substringAfterLast(':').lowercase() == leaderId()

    /**
     * Règle pure (testée) : faut-il réveiller les autres cavaliers ?
     * Oui si aucun lien du 1er n'est lisible : en cache TorBox ou prêt chez AllDebrid ([ready]),
     * et pas un fichier menteur ([liars] : déjà essayé, sans piste française).
     */
    fun shouldWake(leaderStreams: List<Stream>, ready: Set<String>, liars: Set<String>): Boolean =
        leaderStreams.none { s ->
            val hash = s.getEffectiveInfoHash()?.trim()?.lowercase()
            val playable = s.debridCacheStatus?.state == StreamDebridCacheState.CACHED || (hash != null && hash in ready)
            playable && hash !in liars
        }
}
