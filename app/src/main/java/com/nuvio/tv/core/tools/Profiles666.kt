package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.UserProfile

/**
 * 666TVF (11/11) : les profils ne disparaissent plus en silence quand l'autre Shield renvoie au compte
 * une liste où il en manque un (le profil « Freddy » de la Shield du bas). Règle pure (testée).
 */
object Profiles666 {
    private const val MAX = 6

    /** (liste à garder, profils locaux sauvés). Le compte fait foi pour les profils qu'il connaît. */
    fun merge(local: List<UserProfile>, remote: List<UserProfile>): Pair<List<UserProfile>, List<UserProfile>> {
        if (remote.isEmpty()) return local to emptyList()
        val remoteIds = remote.map { it.id }.toSet()
        val rescued = local.filter { it.id !in remoteIds }.take((MAX - remote.size).coerceAtLeast(0))
        return (remote + rescued).sortedBy { it.id } to rescued
    }
}
