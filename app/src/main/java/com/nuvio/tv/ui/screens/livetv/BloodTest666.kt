package com.nuvio.tv.ui.screens.livetv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.sin

/**
 * 23/11 : le test sanguin de The Thing (film préféré de David), pour le test des cavaliers.
 * Chaque cavalier est une boîte de Petri ; le fil chauffé passe de l'une à l'autre pendant le test.
 * Celui qui ne renvoie rien bondit en hurlant : « INFECTÉ » (rouge = échec, comme partout).
 */
object BloodTest666 {
    /** Règle pure (testée) : verdict affiché sous la boîte. null = test en cours. */
    fun verdict(ok: Boolean?): String = when (ok) {
        true -> "humain"
        false -> "INFECTÉ"
        null -> "…"
    }

    /** Règle pure (testée) : nom court du cavalier sous la boîte (sans « Plugin · », 12 caractères max). */
    fun shortName(row: String): String {
        val n = row.removePrefix("Plugin · ").removeSuffix("666").trim()
        return if (n.length > 12) n.take(11).trimEnd() + "…" else n
    }
}

private val Dish = Color(0xFFD8D4CC)
private val BloodOk = Color(0xFF5A0B0B)
private val Wire = Color(0xFFFFB347)
private val Infected = Color(0xFFC3121F)
private val Human = Color(0xFF4CC36B)
private val Mauve = Color(0xFF9B30D9)

@Composable
fun BloodTest666Row(dishes: List<Health666>, running: Boolean, modifier: Modifier = Modifier) {
    if (dishes.isEmpty() && !running) return
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - start) / 1000f }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Text(
            "Le test sanguin · Avant-poste 31",
            color = Mauve, fontSize = 15.sp
        )
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            val list = if (dishes.isEmpty()) List(3) { Health666("…", "", null) } else dishes
            val active = if (running) ((t / 1.6f).toInt() % list.size) else -1
            list.forEachIndexed { i, row -> Dish666(row, wireOn = i == active, t = t) }
        }
    }
}

@Composable
private fun Dish666(row: Health666, wireOn: Boolean, t: Float) {
    val jump = remember { Animatable(0f) }
    LaunchedEffect(row.ok) {
        if (row.ok == false) {
            // il bondit hors de la boîte, comme dans le film
            jump.snapTo(0f)
            jump.animateTo(1f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessMedium))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Canvas(Modifier.size(84.dp, 70.dp)) {
            val c = Offset(size.width / 2f, size.height * 0.62f)
            val r = size.width * 0.38f
            // la boîte (verre) et le sang
            drawOval(Color.Black.copy(alpha = 0.35f), topLeft = Offset(c.x - r, c.y - r * 0.42f + 4f), size = androidx.compose.ui.geometry.Size(2 * r, r * 0.84f))
            val blood = if (row.ok == false) Infected else BloodOk
            val rise = if (row.ok == false) jump.value * r * 0.9f else 0f
            val wob = if (row.ok == false) sin(t * 18f) * 3f * (1f - (jump.value - 1f).coerceIn(-1f, 0f)) else 0f
            drawOval(
                Brush.radialGradient(listOf(blood.copy(alpha = 0.95f), blood.copy(alpha = 0.7f)), center = c, radius = r),
                topLeft = Offset(c.x - r * 0.82f + wob, c.y - r * 0.32f - rise),
                size = androidx.compose.ui.geometry.Size(r * 1.64f, r * 0.64f + rise * 0.6f)
            )
            drawOval(Dish, topLeft = Offset(c.x - r, c.y - r * 0.42f), size = androidx.compose.ui.geometry.Size(2 * r, r * 0.84f), style = Stroke(2.5f))
            // le fil chauffé (orange vif quand il touche)
            if (wireOn) {
                val tip = Offset(c.x + r * 0.15f, c.y - r * 0.05f)
                drawLine(Color(0xFF8F8A85), Offset(size.width, 0f), tip, strokeWidth = 3f, cap = StrokeCap.Round)
                val glow = 0.6f + 0.4f * sin(t * 20f)
                drawCircle(Wire.copy(alpha = glow), radius = 7f, center = tip)
                drawCircle(Wire.copy(alpha = 0.25f * glow), radius = 16f, center = tip)
                // fumée
                for (k in 0 until 3) {
                    val p = ((t * 0.9f + k / 3f) % 1f)
                    drawCircle(Color(0xFFBDB6AE).copy(alpha = 0.35f * (1f - p)), radius = 4f + 8f * p, center = Offset(tip.x + sin(t * 3f + k) * 6f, tip.y - 30f * p))
                }
            }
        }
        Text(
            BloodTest666.shortName(row.name),
            color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        Text(
            BloodTest666.verdict(row.ok),
            color = when (row.ok) { true -> Human; false -> Infected; null -> Color(0xFF8F8A85) },
            fontSize = if (row.ok == false) 13.sp else 12.sp,
            modifier = Modifier.height(18.dp).graphicsLayer {
                if (row.ok == false) { val s = 1f + 0.12f * sin(t * 9f).coerceAtLeast(0f); scaleX = s; scaleY = s }
            }
        )
    }
}
