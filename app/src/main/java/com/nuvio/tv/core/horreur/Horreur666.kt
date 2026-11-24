package com.nuvio.tv.core.horreur

import android.content.Context

/**
 * 23/11 : les clins d'œil aux films d'horreur dans Frank (idées de David : slasher, possession,
 * vampire, et The Thing, son film préféré). Les règles sont pures et testées ; le dessin et les sons
 * sont posés là où ils servent (écran de veille, Abattoir, Outils 666, annonces).
 */
object Horreur666 {
    // ---------- 23/11 : un son court joué jusqu'au bout (lecteur gardé, sinon Android peut le couper) ----------
    private val held = java.util.Collections.synchronizedSet(HashSet<android.media.MediaPlayer>())

    fun playHeld(context: Context, res: Int, volume: Float) {
        runCatching {
            android.media.MediaPlayer.create(context.applicationContext, res)?.apply {
                held += this
                setVolume(volume, volume)
                setOnCompletionListener { mp -> held -= mp; mp.release() }
                start()
            }
        }
    }

    // ---------- Vendredi 13 : « ki-ki-ki ma-ma-ma » à l'entrée de l'onglet Slasher ----------
    private val SLASHER = Regex("slasher", RegexOption.IGNORE_CASE)
    const val KIKI_GAP_MS = 6_000L   // 23/11 (réparation 3) : à chaque entrée sur Slasher (6 s : pas de doublon)
    @Volatile private var kikiAt = 0L
    @Volatile private var lastLabel: String? = null
    @Volatile private var whisper: android.media.MediaPlayer? = null

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
        // 23/11 (réparation) : le lecteur est gardé jusqu'au bout (sans référence, Android pouvait le couper
        // en plein son : « seulement 2 ki ») ; un nouveau passage relance proprement depuis le début.
        runCatching { whisper?.let { it.stop(); it.release() } }
        whisper = runCatching {
            android.media.MediaPlayer.create(context.applicationContext, com.nuvio.tv.R.raw.kikimama_666)?.apply {
                setVolume(0.55f, 0.55f)
                setOnCompletionListener { mp -> if (whisper === mp) whisper = null; mp.release() }
                start()
            }
        }.getOrNull()
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

    // ---------- Predator : le viseur (trois points laser en triangle) sur le bouton du programme en cours ----------
    const val LASER_EVERY_MS = 40_000L
    const val LASER_SHOW_MS = 5_000L

    /** Règle pure (testée) : progression (0..1) du viseur, ou null s'il n'est pas là (5 s toutes les 40 s, après 12 s). */
    fun predatorLaser(tMs: Long): Float? {
        val u = tMs - 12_000L
        if (u < 0) return null
        val p = Math.floorMod(u, LASER_EVERY_MS)
        return if (p < LASER_SHOW_MS) p.toFloat() / LASER_SHOW_MS else null
    }

    /** Règle pure (testée) : les trois points arrivent de loin (0..0,35) puis se calent en triangle et tremblent. */
    fun laserSpread(progress: Float): Float = if (progress < 0.35f) 1f - progress / 0.35f else 0f

    // ---------- Predator : vision thermique dans l'écran de veille ----------
    const val THERMAL_EVERY_MS = 5 * 60_000L
    const val THERMAL_SHOW_MS = 7_000L

    /** Règle pure (testée) : progression (0..1) de la vision thermique, ou null (7 s toutes les 5 min, après 90 s). */
    fun thermalVision(tMs: Long): Float? {
        val u = tMs - 90_000L
        if (u < 0) return null
        val p = Math.floorMod(u, THERMAL_EVERY_MS)
        return if (p < THERMAL_SHOW_MS) p.toFloat() / THERMAL_SHOW_MS else null
    }

    /** Règle pure (testée) : bascule du casque : clignote à l'entrée et à la sortie, plein au milieu. */
    fun thermalAlpha(p: Float): Float = when {
        p < 0.06f -> if ((p * 100).toInt() % 2 == 0) 1f else 0f
        p > 0.94f -> if ((p * 100).toInt() % 2 == 0) 0f else 1f
        else -> 1f
    }

    /** Règle pure (testée) : couleur de chaleur pour une luminosité 0..255 (froid bleu → vert → jaune → rouge chaud). */
    fun thermalColor(lum: Int): Int {
        val stops = intArrayOf(0x000820, 0x0A1F8F, 0x00A0C8, 0x18D048, 0xF0E020, 0xFF8A00, 0xFF2010, 0xFFFFFF)
        val x = lum.coerceIn(0, 255) / 255f * (stops.size - 1)
        val i = x.toInt().coerceAtMost(stops.size - 2)
        val f = x - i
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
        fun mix(sh: Int) = (ch(stops[i], sh) + (ch(stops[i + 1], sh) - ch(stops[i], sh)) * f).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * Règle pure (testée) : niveau de chaleur : le décor reste froid (bleu), les corps chauds montent à l'orange-rouge
     * en gardant leurs détails (visage, mains plus chauds que les vêtements), comme la retouche Gemini de David.
     */
    fun thermalLevel(lum: Int, hot: Int): Int {
        val l = lum.coerceIn(0, 255)
        val cold = (l * 0.40f).toInt()
        if (hot <= 8) return cold
        val body = (150 + hot * 0.22f + l * 0.12f).toInt()   // 24/11 : corps orange-rouge comme la retouche de David
        val k = (hot / 255f).coerceIn(0f, 1f)
        return (cold + (body - cold) * k).toInt().coerceIn(0, 255)
    }

    // ---------- Alien : l'œuf de l'Abattoir s'entrouvre de temps en temps ----------
    /** Règle pure (testée) : intensité (0..1) : 6 s toutes les 75 s (montée, tenue, retombée), 0 le reste du temps. */
    fun eggStir(t: Double): Float {
        val u = (t - 20.0)
        if (u < 0) return 0f
        val p = u % 75.0
        return when {
            p < 1.5 -> (p / 1.5).toFloat()
            p < 4.5 -> 1f
            p < 6.0 -> ((6.0 - p) / 1.5).toFloat()
            else -> 0f
        }
    }

    // ---------- Alien : le chestburster quand un film sort en VF le jour même de sa sortie ----------
    /** Règle pure (testée) : la VF arrive le jour de la sortie (ou la veille au soir / le lendemain : ±1 jour). */
    fun releaseDay(released: java.time.LocalDate?, today: java.time.LocalDate): Boolean =
        released != null && kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(released, today)) <= 1

    fun markReleaseDay(context: Context, imdbId: String) {
        val p = context.getSharedPreferences("666tv_horreur", Context.MODE_PRIVATE)
        val set = p.getStringSet("jour_j", emptySet()).orEmpty().toMutableSet()
        set += imdbId
        p.edit().putStringSet("jour_j", set.toList().takeLast(30).toSet()).apply()
    }

    /** Règle pure (testée) : l'annonce contient-elle un film du jour J ? (id de la forme « film:tt… ») */
    fun chestburster(seriesIds: List<String>, releaseDayIds: Set<String>): Boolean =
        seriesIds.any { it.startsWith("film:") && it.removePrefix("film:") in releaseDayIds }

    fun chestbursterFor(context: Context, seriesIds: List<String>): Boolean = runCatching {
        chestburster(seriesIds, context.getSharedPreferences("666tv_horreur", Context.MODE_PRIVATE).getStringSet("jour_j", emptySet()).orEmpty())
    }.getOrDefault(false)

    /** Règle pure (testée) : taille du chestburster à l'instant t : il jaillit (rebond), reste, puis s'efface après 4 s. */
    fun burstScale(tMs: Long): Float = when {
        tMs < 0 -> 0f
        tMs < 450 -> { val k = tMs / 450f; (k * (1.25f - 0.25f * k)).coerceAtMost(1.15f) }
        tMs < 700 -> 1.15f - 0.15f * ((tMs - 450) / 250f)
        else -> 1f
    }

    fun burstAlpha(tMs: Long): Float = when {
        tMs < 0 -> 0f
        tMs < 4_000 -> 1f
        tMs < 5_000 -> 1f - (tMs - 4_000) / 1_000f
        else -> 0f
    }

    /**
     * Règle pure (testée) : le facehugger de l'œuf de droite, une ouverture sur trois (≈ toutes les 3 min 45).
     * 1) ses pattes sortent de l'œuf grand ouvert (1,2 s) ; 2) il bondit vers nous (1,1 s).
     */
    private fun leapCyclePhase(t: Double): Double? {
        val u = t - 37.0 - 20.0
        if (u < 0) return null
        val cycle = Math.floor(u / 75.0).toLong()
        if (Math.floorMod(cycle, 3L) != 1L) return null
        return u - cycle * 75.0
    }

    /** Pattes qui sortent de l'œuf : progression 0..1 (1,4 s → 2,6 s du cycle), ou null. */
    fun facehuggerEmerge(t: Double): Float? {
        val p = leapCyclePhase(t) ?: return null
        return if (p >= 1.4 && p < 2.6) ((p - 1.4) / 1.2).toFloat() else null
    }

    /** Le bond : progression 0..1 (2,6 s → 3,7 s du cycle), ou null. */
    fun facehuggerLeap(t: Double): Float? {
        val p = leapCyclePhase(t) ?: return null
        return if (p >= 2.6 && p < 3.7) ((p - 2.6) / 1.1).toFloat() else null
    }
}
