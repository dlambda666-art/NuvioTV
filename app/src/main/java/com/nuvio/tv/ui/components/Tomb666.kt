package com.nuvio.tv.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nuvio.tv.R
import kotlin.math.roundToInt

/**
 * 666TV : les affiches dans des pierres tombales (debout pour les affiches, plaque couchée
 * pour les cartes en largeur). L'affiche d'origine est gardée telle quelle (badges VF,
 * pourcentage restant, saison…) : elle est juste posée dans l'ouverture de la tombe.
 */
object Tomb666 {
    private const val PREFS = "666tv_tombes"
    private const val KEY = "enabled"

    /** Les tombes sont plus grandes que les affiches : on agrandit les cases d'autant. */
    const val PORTRAIT_SCALE = 1.3f
    const val LANDSCAPE_HEIGHT_SCALE = 1.27f

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        enabled = value
    }

    fun portrait(d: Dp): Dp = if (enabled) d * PORTRAIT_SCALE else d
    fun landscapeHeight(d: Dp): Dp = if (enabled) d * LANDSCAPE_HEIGHT_SCALE else d

    internal class Frame(val bitmap: ImageBitmap, val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
        val aspect = bitmap.width.toFloat() / bitmap.height
    }

    private var portraitFrame: Frame? = null
    private var landscapeFrame: Frame? = null

    internal fun frame(context: Context, landscape: Boolean): Frame? = runCatching {
        if (landscape) {
            landscapeFrame ?: Frame(decode(context, R.drawable.frame_plaque_666), 0.0518f, 0.0702f, 0.9475f, 0.7710f)
                .also { landscapeFrame = it }
        } else {
            portraitFrame ?: Frame(decode(context, R.drawable.frame_tombe_666), 0.2381f, 0.2456f, 0.7651f, 0.7379f)
                .also { portraitFrame = it }
        }
    }.getOrNull()

    private fun decode(context: Context, res: Int): ImageBitmap =
        BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply { inScaled = false })
            .asImageBitmap()
}

/**
 * Pose le contenu (l'affiche) dans l'ouverture de la tombe, et dessine la pierre par-dessus.
 * À mettre sur la boîte de l'image, avant le clip. Sans effet si les tombes sont désactivées.
 */
fun Modifier.tomb666(landscape: Boolean, focused: Boolean): Modifier = composed {
    if (!Tomb666.enabled) return@composed this
    val context = LocalContext.current
    val frame = Tomb666.frame(context, landscape) ?: return@composed this
    val glow by animateFloatAsState(if (focused) 1f else 0f, label = "tombGlow")
    val rect = androidx.compose.runtime.remember { IntArray(4) }  // x, y, w, h de la tombe dans la case
    this
        .graphicsLayer {
            val s = 1f + 0.05f * glow
            scaleX = s; scaleY = s
        }
        .drawWithContent {
            val fx = rect[0].toFloat(); val fy = rect[1].toFloat()
            val fw = rect[2].toFloat(); val fh = rect[3].toFloat()
            if (glow > 0.01f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8A2BD0).copy(alpha = 0.55f * glow), Color.Transparent),
                        center = Offset(fx + fw / 2f, fy + fh * 0.5f),
                        radius = maxOf(fw, fh) * 0.62f
                    )
                )
            }
            drawContent()
            drawImage(
                image = frame.bitmap,
                dstOffset = IntOffset(rect[0], rect[1]),
                dstSize = IntSize(rect[2], rect[3]),
                filterQuality = FilterQuality.Medium
            )
        }
        .layout { measurable, constraints ->
            val w = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            val h = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
            // La tombe garde ses proportions : on la cale dans la case, centrée.
            var tw = w; var th = (w / frame.aspect).roundToInt()
            if (th > h) { th = h; tw = (h * frame.aspect).roundToInt() }
            val tx = (w - tw) / 2; val ty = (h - th)
            rect[0] = tx; rect[1] = ty; rect[2] = tw; rect[3] = th
            val cx = tx + (tw * frame.x0).roundToInt(); val cy = ty + (th * frame.y0).roundToInt()
            val cw = (tw * (frame.x1 - frame.x0)).roundToInt().coerceAtLeast(1)
            val ch = (th * (frame.y1 - frame.y0)).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(cw, ch))
            layout(w, h) { placeable.place(cx, cy) }
        }
}

/** 666TV : fond cimetière (pages de catégories), seulement quand les tombes sont activées. */
fun Modifier.cemetery666(): Modifier = composed {
    if (!Tomb666.enabled) return@composed this
    val painter = androidx.compose.ui.res.painterResource(R.drawable.bg_cimetiere_666)
    this.drawBehind {
        with(painter) { draw(size) }
        drawRect(Color.Black.copy(alpha = 0.28f))
    }
}
