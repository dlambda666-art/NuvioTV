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

    private val lock = Mutex()
    private var last = 0L

    /** Une demande d'arrière-plan à la fois, espacées de [GAP_MS]. */
    suspend fun <T> paced(block: suspend () -> T): T = lock.withLock {
        val wait = last + GAP_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try { block() } finally { last = System.currentTimeMillis() }
    }
}
