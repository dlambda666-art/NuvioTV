package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nuvio.tv.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 666TVF (30/10) : le fond « abattoir » est vivant sur l'accueil.
 *  - du sang goutte des sacs, des crochets, du plafond, de l'étagère et de la table
 *    (certaines gouttes plus visqueuses, qui filent avant de lâcher) ;
 *  - le néon clignote de temps en temps ;
 *  - derrière les tuiles, jamais par-dessus ; s'efface avec le fondu vers le cimetière.
 * Coordonnées sur une image 1280×720 (le fond fait 1920×1080, recadré en haut à droite).
 */
object Abattoir666 {
    data class Src(val x: Float, val y0: Float, val y1: Float, val period: Float)

    val SOURCES = listOf(
        Src(135f, 410f, 560f, 1.5f), Src(152f, 414f, 562f, 2.2f), Src(240f, 282f, 575f, 1.2f), Src(266f, 276f, 575f, 1.9f),
        Src(400f, 332f, 560f, 1.1f), Src(388f, 336f, 560f, 1.7f), Src(330f, 432f, 560f, 1.4f), Src(345f, 440f, 562f, 2.3f),
        Src(598f, 342f, 600f, 1.6f), Src(876f, 560f, 655f, 0.85f), Src(902f, 540f, 652f, 1.5f), Src(850f, 530f, 650f, 2.0f),
        Src(1030f, 210f, 520f, 1.8f), Src(1046f, 205f, 522f, 2.6f), Src(1012f, 392f, 520f, 1.3f), Src(1180f, 386f, 515f, 1.15f),
        Src(1162f, 380f, 512f, 1.9f), Src(884f, 300f, 650f, 2.4f), Src(965f, 398f, 520f, 1.7f),
        Src(520f, 120f, 700f, 2.9f), Src(640f, 165f, 700f, 3.4f), Src(760f, 150f, 700f, 2.7f), Src(1110f, 110f, 640f, 3.1f),
        Src(300f, 90f, 600f, 3.6f), Src(260f, 410f, 560f, 2.0f), Src(330f, 412f, 560f, 2.6f), Src(450f, 410f, 565f, 1.8f),
        Src(420f, 474f, 630f, 1.0f), Src(470f, 470f, 628f, 1.6f), Src(520f, 476f, 630f, 1.25f), Src(560f, 478f, 632f, 2.1f),
        Src(610f, 474f, 630f, 1.4f), Src(650f, 470f, 628f, 1.9f), Src(690f, 468f, 625f, 1.1f), Src(740f, 465f, 622f, 1.7f),
        Src(780f, 462f, 620f, 1.3f), Src(815f, 440f, 600f, 2.2f),
        // 666TVF : le corps sur la table — ça goutte du bout des doigts (30/10)
        Src(752f, 571f, 603f, 0.9f), Src(757f, 573f, 605f, 1.45f)
    )

    private const val GRAVITY = 1700f

    sealed class Drop {
        /** La goutte gonfle et s'étire sous la source (len : longueur, r : rayon, neck : demi-largeur du col). */
        data class Forming(val len: Float, val r: Float, val neck: Float) : Drop()
        /** Elle tombe ; threadTo : bas du fil visqueux encore accroché (sinon null). */
        data class Falling(val y: Float, val r: Float, val streak: Float, val threadTo: Float?) : Drop()
        /** Elle touche le sol : k de 0 à 1. */
        data class Splash(val floorY: Float, val k: Float, val r: Float) : Drop()
    }

    fun isViscous(i: Int) = i % 3 == 0

    /** Règle pure (testée) : où en est la goutte [i] au temps [t] (secondes). */
    fun state(i: Int, t: Double): Drop {
        val s = SOURCES[i]
        val visc = isViscous(i)
        val size = 1.4f + 0.6f * ((i * 7) % 5) / 4f
        val release = if (visc) 0.78f else 0.6f
        val per = if (visc) s.period * 1.6f else s.period
        val y1 = s.y1 + ((i * 13) % 21) - 10
        val ph = (((t / per) + i * 0.37) % 1.0).toFloat()
        if (ph < release) {
            val k = ph / release
            val r = (1.4f + 1.6f * k) * size
            val len = (if (visc) 2f + 24f * Math.pow(k.toDouble(), 2.2).toFloat() else 2f + 7f * k * k) * size
            val neck = r * (if (visc) 0.55f - 0.32f * k else 0.55f)
            return Drop.Forming(len, r, neck)
        }
        val tau = (ph - release) * per
        val y = s.y0 + 0.5f * GRAVITY * tau * tau
        val v = GRAVITY * tau
        return if (y < y1) {
            Drop.Falling(
                y = y, r = 2.3f * size, streak = min(28f, v * 0.012f),
                threadTo = if (visc && tau < 0.14f) y - 1f else null
            )
        } else {
            Drop.Splash(y1, min(1f, (y - y1) / 260f), size)
        }
    }

    private val NEON = listOf(
        Triple(2.10, 0.07, 1f), Triple(2.24, 0.05, 1f), Triple(2.36, 0.12, 0.8f),
        Triple(6.0, 0.45, 1f), Triple(6.55, 0.06, 1f), Triple(6.68, 0.04, 0.6f), Triple(8.2, 0.05, 0.5f)
    )

    // ---------- La cloche coupe le néon, la porte le fait mourir (30/10) ----------
    @Volatile var cutAt: Long = 0L
        private set

    /** Nouvel épisode VF annoncé : le néon coupe une seconde au moment du cri. */
    fun requestCut(now: Long = System.currentTimeMillis()) { cutAt = now }

    private val DOOR_FLICKER = floatArrayOf(1f, 0f, 1f, 0.4f, 0f, 1f, 1f, 0f, 0.6f, 1f, 0f, 1f, 1f)
    const val DOOR_DIE_MS = 900L
    const val CUT_MS = 1_200L

    /**
     * Règle pure (testée) : néon éteint (0 = allumé, 1 = éteint).
     * Porte qui s'ouvre (sinceDoorMs ≥ 0) : grésille 0,9 s puis meurt. Cloche (sinceCutMs ≥ 0) :
     * un clignement puis coupé 1 s. Sinon le clignotement normal.
     */
    fun neonLevel(t: Double, sinceCutMs: Long, sinceDoorMs: Long): Float {
        if (sinceDoorMs >= 0) {
            return if (sinceDoorMs < DOOR_DIE_MS) DOOR_FLICKER[((sinceDoorMs / 70) % DOOR_FLICKER.size).toInt()] else 1f
        }
        if (sinceCutMs in 0 until CUT_MS) return if (sinceCutMs in 120 until 200) 0f else 1f
        return neonOff(t)
    }

    /** Le bzzt de la grosse coupure ne sonne qu'un cycle sur trois (toutes les 27 s), sinon ça lasse. */
    fun bigCutBuzzes(t: Double): Boolean = ((t / 9.0).toLong() % 3L) == 0L

    fun isBigCutStart(prevT: Double, t: Double): Boolean {
        val a = prevT % 9.0; val b = t % 9.0
        return (a < 6.0 && b >= 6.0 && b < 6.45) || (a > b && b >= 6.0 && b < 6.45)
    }

    /** Réglage Outils 666 : le bzzt du néon. */
    fun buzzEnabled(context: android.content.Context): Boolean =
        context.getSharedPreferences("666tv_neon", android.content.Context.MODE_PRIVATE).getBoolean("bzzt", true)

    fun setBuzzEnabled(context: android.content.Context, on: Boolean) =
        context.getSharedPreferences("666tv_neon", android.content.Context.MODE_PRIVATE).edit().putBoolean("bzzt", on).apply()

    /** Règle pure (testée) : néon éteint (0 = allumé, 1 = éteint), cycle de 9 s. */
    fun neonOff(t: Double): Float {
        val tt = t % 9.0
        for ((start, dur, level) in NEON) if (tt >= start && tt < start + dur) return level
        return 0f
    }
}

private val BloodRim = Color(0xFF240302)
private val BloodLight = Color(0xFF5E100B)
private val BloodDark = Color(0xFF3A0605)
private val BloodShine = Color(0x55C8A096)

/** À poser juste au-dessus de l'image de fond (Crop, aligné en haut à droite). */
@Composable
fun Abattoir666Layer(visible: Boolean, modifier: Modifier = Modifier) {
    val opening = Door666.opening
    // la porte : le néon meurt d'abord (1,2 s dans le noir), puis le cimetière apparaît
    val alpha by animateFloatAsState(
        targetValue = if (visible && !opening) 1f else 0f,
        animationSpec = if (opening) {
            tween(durationMillis = DOOR_FADE_MS_666 - 1_200, delayMillis = 1_200, easing = LinearEasing)
        } else {
            tween(durationMillis = 400, easing = LinearEasing)
        },
        label = "abattoir666"
    )
    if (alpha <= 0f) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val neonOff: ImageBitmap = ImageBitmap.imageResource(R.drawable.neon_off_666)
    var time by remember { mutableFloatStateOf(0f) }
    var level by remember { mutableFloatStateOf(0f) }
    var deep by remember { mutableFloatStateOf(0f) }
    var doorAt by remember { mutableStateOf(-1L) }
    LaunchedEffect(opening) {
        doorAt = if (opening) System.currentTimeMillis() else -1L
        if (opening && Abattoir666.buzzEnabled(context)) Neon666Sound.play(context, R.raw.neon_meurt_666)
    }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        var prevT = 0.0
        var lastCutSeen = Abattoir666.cutAt
        while (true) {
            withFrameMillis { now ->
                if (com.nuvio.tv.core.tools.Announce666.inPlayer()) return@withFrameMillis
                val t = (now - start) / 1000.0
                val wall = System.currentTimeMillis()
                val sinceDoor = if (doorAt >= 0) wall - doorAt else -1L
                val sinceCut = if (Abattoir666.cutAt > 0) wall - Abattoir666.cutAt else -1L
                level = Abattoir666.neonLevel(t, sinceCut, sinceDoor)
                deep = if (sinceDoor > Abattoir666.DOOR_DIE_MS) min(0.6f, (sinceDoor - Abattoir666.DOOR_DIE_MS) / 666f) else 0f
                val buzz = Abattoir666.buzzEnabled(context)
                if (buzz && sinceDoor < 0) {
                    if (Abattoir666.cutAt != lastCutSeen && sinceCut in 0 until 500) Neon666Sound.play(context, R.raw.neon_bzzt_666)
                    else if (Abattoir666.isBigCutStart(prevT, t) && Abattoir666.bigCutBuzzes(t)) Neon666Sound.play(context, R.raw.neon_bzzt_666)
                }
                lastCutSeen = Abattoir666.cutAt
                prevT = t
                time = t.toFloat()
            }
        }
    }
    Canvas(modifier) {
        val t = time.toDouble()
        // même cadrage que l'image : Crop, en haut à droite
        val scale = max(size.width / 1920f, size.height / 1080f)
        val iw = 1920f * scale
        val ih = 1080f * scale
        val left = size.width - iw
        val off = level
        if (off > 0f) {
            drawImage(
                neonOff,
                dstOffset = IntOffset(left.toInt(), 0),
                dstSize = IntSize(iw.toInt(), ih.toInt()),
                alpha = off * alpha
            )
        }
        if (deep > 0f) drawRect(Color.Black, alpha = deep * alpha)
        val u = iw / 1280f
        Abattoir666.SOURCES.forEachIndexed { i, s ->
            drawDrop(Abattoir666.state(i, t), left + s.x * u, s.y0 * u, u, alpha)
        }
    }
}

private fun DrawScope.drawDrop(d: Abattoir666.Drop, x: Float, y0: Float, u: Float, alpha: Float) {
    when (d) {
        is Abattoir666.Drop.Forming -> {
            val r = d.r * u
            val bottom = y0 + d.len * u
            val neck = d.neck * u
            val path = Path().apply {
                moveTo(x - neck, y0); lineTo(x + neck, y0)
                lineTo(x + neck * 1.3f, bottom - r); lineTo(x - neck * 1.3f, bottom - r); close()
            }
            drawPath(path, BloodDark, alpha = alpha)
            bulb(x, bottom - r, r, alpha)
        }
        is Abattoir666.Drop.Falling -> {
            val y = d.y * u
            val r = d.r * u
            d.threadTo?.let { drawRect(BloodDark, Offset(x - 0.35f * u, y0), Size(0.7f * u, max(0f, it * u - y0)), alpha = alpha) }
            val tail = Path().apply {
                moveTo(x - r * 0.7f, y - r * 0.4f); lineTo(x + r * 0.7f, y - r * 0.4f); lineTo(x, y - r - d.streak * u); close()
            }
            drawPath(tail, BloodDark, alpha = alpha)
            bulb(x, y, r, alpha)
        }
        is Abattoir666.Drop.Splash -> {
            val a = (1f - d.k) * alpha
            if (a <= 0f) return
            val rr = (3f + 12f * d.k) * d.r * u
            val fy = d.floorY * u
            for (j in 0 until 5) {
                val ang = Math.PI * (0.1 + 0.8 * j / 4.0)
                val px = x + (kotlin.math.cos(ang) * rr * 1.3).toFloat()
                val py = fy - (kotlin.math.sin(ang) * rr * (0.9 + 0.4 * (j % 2))).toFloat()
                drawCircle(BloodLight, radius = 1.6f * u, center = Offset(px, py), alpha = a)
            }
        }
    }
}

/** Goutte brillante : bord sombre, cœur rouge sang, petit reflet en haut à gauche. */
private fun DrawScope.bulb(cx: Float, cy: Float, r: Float, alpha: Float) {
    drawOval(BloodRim, Offset(cx - r * 1.08f, cy - r * 1.25f), Size(r * 2.16f, r * 2.4f), alpha = alpha)
    drawOval(
        Brush.radialGradient(listOf(BloodLight, BloodDark), center = Offset(cx - r * 0.3f, cy - r * 0.4f), radius = r * 1.4f),
        Offset(cx - r, cy - r * 1.15f), Size(r * 2f, r * 2.2f), alpha = alpha
    )
    val h = max(0.6f, r * 0.32f)
    drawOval(BloodShine, Offset(cx - r * 0.55f, cy - r * 0.75f), Size(h, h * 1.3f), alpha = alpha)
}

/** 666TVF : le bzzt du néon, joué doucement (jamais pendant un film). */
object Neon666Sound {
    @Volatile private var lastAt = 0L
    fun play(context: android.content.Context, res: Int) {
        val now = System.currentTimeMillis()
        if (now - lastAt < 800 || com.nuvio.tv.core.tools.Announce666.inPlayer()) return
        lastAt = now
        runCatching {
            android.media.MediaPlayer.create(context.applicationContext, res)?.apply {
                setVolume(0.3f, 0.3f)
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }
}
