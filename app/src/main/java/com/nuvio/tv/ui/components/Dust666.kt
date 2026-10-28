package com.nuvio.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 666TV : quand on retire un film/une série de « Continuer à regarder », la carte part
 * en poussière vers le haut (de gauche à droite, braises rouges et cendres grises).
 */
object Dust666 {
    private class Source(val layer: GraphicsLayer, var bounds: Rect)
    private val sources = HashMap<String, Source>()

    internal var hiddenKey by mutableStateOf<String?>(null)
    internal var burst by mutableStateOf<Burst?>(null)

    internal class Burst(
        val x: FloatArray, val y: FloatArray, val vx: FloatArray, val vy: FloatArray,
        val delay: FloatArray, val color: IntArray, val size: Float, val startNanos: Long
    )

    internal fun register(key: String, layer: GraphicsLayer, bounds: Rect) {
        val s = sources[key]
        if (s != null && s.layer === layer) s.bounds = bounds else sources[key] = Source(layer, bounds)
    }

    internal fun unregister(key: String, layer: GraphicsLayer) {
        if (sources[key]?.layer === layer) sources.remove(key)
    }

    /** Lance l'effet sur la carte [key], puis [onRemove] (le vrai retrait). */
    fun dissolve(scope: CoroutineScope, key: String, onRemove: () -> Unit) {
        if (com.nuvio.tv.core.tools.Light666.active) { onRemove(); return }  // 666TV : mode léger
        val src = sources[key]
        if (src == null) { onRemove(); return }
        scope.launch {
            val bmp: Bitmap? = runCatching {
                src.layer.toImageBitmap().asAndroidBitmap().let {
                    if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
                }
            }.getOrNull()
            val b = src.bounds
            val step = max(5f, b.width / 90f)
            val cols = (b.width / step).toInt().coerceAtLeast(1)
            val rows = (b.height / step).toInt().coerceAtLeast(1)
            val n = cols * rows
            val x = FloatArray(n); val y = FloatArray(n); val vx = FloatArray(n); val vy = FloatArray(n)
            val delay = FloatArray(n); val color = IntArray(n)
            var i = 0
            for (r in 0 until rows) for (c in 0 until cols) {
                val px = c * step; val py = r * step
                x[i] = b.left + px; y[i] = b.top + py
                vx[i] = (Random.nextFloat() - 0.3f) * 40f * step / 3f
                vy[i] = -(60f + Random.nextFloat() * 120f) * step / 3f
                delay[i] = (px / b.width) * 0.45f + Random.nextFloat() * 0.15f
                color[i] = bmp?.let {
                    runCatching {
                        it.getPixel(
                            (px * it.width / b.width).toInt().coerceIn(0, it.width - 1),
                            (py * it.height / b.height).toInt().coerceIn(0, it.height - 1)
                        )
                    }.getOrNull()
                } ?: 0xFF3A0C5C.toInt()
                i++
            }
            hiddenKey = key
            burst = Burst(x, y, vx, vy, delay, color, step, System.nanoTime())
            onRemove()
        }
    }
}

/** À poser sur une carte « Continuer à regarder » : permet de la réduire en poussière. */
fun Modifier.dust666Source(key: String): Modifier = composed {
    val layer = rememberGraphicsLayer()
    androidx.compose.runtime.DisposableEffect(key, layer) {
        onDispose { Dust666.unregister(key, layer) }
    }
    this
        .onGloballyPositioned { Dust666.register(key, layer, it.boundsInRoot()) }
        .drawWithContent {
            if (Dust666.hiddenKey == key && Dust666.burst != null) return@drawWithContent
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

/** Calque plein écran qui dessine la poussière. */
@Composable
fun Dust666Overlay(modifier: Modifier = Modifier) {
    val burst = Dust666.burst ?: return
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(burst) {
        while (true) {
            withFrameNanos { now = it.coerceAtLeast(burst.startNanos) }
            if ((System.nanoTime() - burst.startNanos) / 1e9f > 1.6f) break
        }
        if (Dust666.burst === burst) { Dust666.burst = null; Dust666.hiddenKey = null }
    }
    Canvas(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        val el = (System.nanoTime() - burst.startNanos) / 1e9f
        val life = 0.9f
        val ember = Color(0xFF3A0C5C); val ash = Color(0xFF888888)
        for (i in burst.x.indices) {
            val lt = el - burst.delay[i]
            val baseX = burst.x[i] - origin.x; val baseY = burst.y[i] - origin.y
            if (lt < 0f) {
                drawRect(Color(burst.color[i]), Offset(baseX, baseY), Size(burst.size, burst.size))
                continue
            }
            if (lt > life) continue
            val a = 1f - lt / life
            val px = baseX + burst.vx[i] * lt + sin(baseY + lt * 8f) * 6f * lt
            val py = baseY + burst.vy[i] * lt - 40f * lt * lt
            val s = burst.size * (1f - lt * 0.6f)
            val c = if (lt < 0.25f) Color(burst.color[i]) else if (i % 2 == 0) ember else ash
            drawRect(c, Offset(px, py), Size(s, s), alpha = a)
        }
        now // relit à chaque image
    }
}
