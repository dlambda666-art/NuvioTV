package com.nuvio.tv.core.tools

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 666TVF (06/11) : les vérifications en arrière-plan (guetteur, PLE, calendrier VF) ne doivent jamais
 * saturer un cavalier. StreamFusion répondait « HTTP 429 » (trop de demandes) depuis que le guetteur
 * vérifie ~60 films deux fois par jour : il est réservé aux recherches que TU lances, et les autres
 * cavaliers sont interrogés posément (une demande à la fois, avec une pause entre deux).
 */
object Background666 {
    /** Cavaliers qui n'acceptent pas les rafales de demandes : jamais en arrière-plan. */
    private val NOT_IN_BACKGROUND = listOf("streamfusion")

    const val GAP_MS = 2_500L

    /** Règle pure (testée) : ce cavalier peut-il servir aux vérifications en arrière-plan ? */
    fun allowed(scraperId: String): Boolean {
        val id = scraperId.substringAfterLast(':').lowercase()
        return NOT_IN_BACKGROUND.none { id.contains(it) }
    }

    // 26/11 (David : Raw / NXT seulement sur YGG) : StreamFusion peut quand même servir au cri, mais au compte-gouttes
    const val RARE_GAP_MS = 10 * 60_000L          // jamais deux questions à moins de 10 min
    const val RARE_PER_EPISODE_MS = 4 * 3600_000L // la même question au plus toutes les 4 h

    /** Règle pure (testée) : peut-on poser une question « rare » maintenant ? */
    fun rareOk(lastAny: Long, lastForKey: Long, now: Long): Boolean =
        now - lastAny >= RARE_GAP_MS && now - lastForKey >= RARE_PER_EPISODE_MS

    private var rareLastAny = 0L
    private val rareLastByKey = HashMap<String, Long>()

    /** Réserve une question rare à [scraperId] pour [key] (épisode) ; faux si c'est trop tôt. */
    @Synchronized
    fun takeRare(scraperId: String, key: String, now: Long = System.currentTimeMillis()): Boolean {
        val k = scraperId + "|" + key
        if (!rareOk(rareLastAny, rareLastByKey[k] ?: 0L, now)) return false
        rareLastAny = now
        rareLastByKey[k] = now
        return true
    }

    private val lock = Mutex()
    private var last = 0L

    /** Une demande d'arrière-plan à la fois, espacées de [GAP_MS]. */
    suspend fun <T> paced(block: suspend () -> T): T = lock.withLock {
        val wait = last + GAP_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try { block() } finally { last = System.currentTimeMillis() }
    }
}
