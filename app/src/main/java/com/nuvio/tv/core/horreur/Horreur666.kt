package com.nuvio.tv.core.horreur

import android.content.Context

/**
 * 23/11 : les clins d'œil aux films d'horreur dans Frank (idées de David : slasher, possession,
 * vampire, et The Thing, son film préféré). Les règles sont pures et testées ; le dessin et les sons
 * sont posés là où ils servent (écran de veille, Abattoir, Outils 666, annonces).
 */
object Horreur666 {
    // ---------- Vendredi 13 : « ki-ki-ki ma-ma-ma » à l'entrée de l'onglet Slasher ----------
    private val SLASHER = Regex("slasher", RegexOption.IGNORE_CASE)
    const val KIKI_GAP_MS = 6_000L   // 23/11 (réparation 3) : à chaque entrée sur Slasher (6 s : pas de doublon)
    @Volatile private var kikiAt = 0L
    @Volatile private var lastLabel: String? = null

    /** Règle pure (testée) : jouer le chuchotement ? Onglet / rangée Slasher, pas pendant un film, pas deux fois en 6 s. */
    fun shouldWhisper(tabLabel: String?, inPlayer: Boolean, sinceLastMs: Long): Boolean =
        tabLabel != null && SLASHER.containsMatchIn(tabLabel) && !inPlayer && (sinceLastMs < 0 || sinceLastMs >= KIKI_GAP_MS)

    fun onTab(context: Context, tabLabel: String?) {
        val now = System.currentTimeMillis()
        val since = if (kikiAt == 0L) -1L else now - kikiAt
        // seulement en ARRIVANT sur Slasher (pas à chaque redessin de la même rangée)
        val isSlasher = tabLabel != null && SLASHER.containsMatchIn(tabLabel)
        val entering = isSlasher && tabLabel != lastLabel
        lastLabel = tabLabel
        if (!entering || !shouldWhisper(tabLabel, com.nuvio.tv.core.tools.Announce666.inPlayer(), since)) return
        kikiAt = now
        runCatching {
            android.media.MediaPlayer.create(context.applicationContext, com.nuvio.tv.R.raw.kikimama_666)?.apply {
                setVolume(0.9f, 0.9f)
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    // ---------- L'Exorciste : le titre d'un nouveau film en VF s'affiche une seconde à l'envers ----------
    const val UPSIDE_MS = 1_100L
    const val UPSIDE_TURN_MS = 450L

    /** Règle pure (testée) : rotation du titre (degrés) à l'instant t de l'annonce : 180° puis se remet droit. */
    fun titleRotation(tMs: Long): Float = when {
        tMs < UPSIDE_MS -> 180f
        tMs < UPSIDE_MS + UPSIDE_TURN_MS -> {
            val k = (tMs - UPSIDE_MS).toFloat() / UPSIDE_TURN_MS
            val e = 1f - (1f - k) * (1f - k) * (1f - k)
            180f * (1f - e)
        }
        else -> 0f
    }

    // ---------- Evil Dead / Halloween / The Thing : rythme des apparitions dans l'écran de veille ----------
    /** 23/11 (David) : la tempête de neige est TOUJOURS là (fond du cimetière enneigé refait avec Gemini). */
    fun snowNight(@Suppress("UNUSED_PARAMETER") epochDay: Long): Boolean = true

    /** Règle pure (testée) : la caméra-entité d'Evil Dead passe toutes les 7 min (12 s de course). */
    const val ENTITY_EVERY_MS = 7 * 60_000L
    const val ENTITY_RUN_MS = 12_000L
    fun entityProgress(tMs: Long): Float? {
        val p = Math.floorMod(tMs - 3 * 60_000L, ENTITY_EVERY_MS)
        return if (p < ENTITY_RUN_MS) p.toFloat() / ENTITY_RUN_MS else null
    }

    /**
     * Règle pure (testée) : la silhouette masquée (clin d'œil à Halloween) est derrière la tombe
     * du milieu un passage de nuage sur trois ; au passage suivant, elle a disparu.
     */
    fun figureVisible(cloudPass: Int): Boolean = Math.floorMod(cloudPass, 3) == 1

    // ---------- Nosferatu (1922, domaine public) : l'ombre qui monte le long du mur de l'Abattoir ----------
    /**
     * Règle pure (testée) : progression (0..1) de l'ombre, ou null si elle n'est pas là. Elle apparaît quand le néon
     * se rallume après la grosse coupure (6,7 s du cycle de 9 s), un cycle sur trois, et disparaît à la coupure suivante.
     */
    fun nosferatu(t: Double): Float? {
        val u = t - 6.7
        if (u < 0) return null
        val cycle = Math.floor(u / 9.0).toLong()
        val within = u - cycle * 9.0
        if (Math.floorMod(cycle, 3L) != 1L || within >= 8.2) return null
        return (within / 8.2).toFloat()
    }
}
