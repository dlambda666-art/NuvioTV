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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
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

/**
 * 23/11 : la paillasse refaite par David (Gemini) : 4 boîtes de Petri (rubans vierges), le gant et le fil chauffé.
 * Coordonnées dans l'image d'origine (2576 × 998, haut de la scène en y = 440).
 */
private data class DishSpot(val l: Float, val t: Float, val r: Float, val b: Float, val tapeX: Float, val tapeY: Float)
private val DISHES = listOf(
    DishSpot(198f, 795f, 682f, 1015f, 360f, 935f),
    DishSpot(636f, 818f, 1138f, 1068f, 835f, 975f),
    DishSpot(1128f, 845f, 1645f, 1125f, 1362f, 1032f),
    DishSpot(1686f, 866f, 2292f, 1198f, 1985f, 1052f)
)
private const val SCENE_W = 2576f
private const val SCENE_H = 998f
private const val SCENE_TOP = 440f

private val Infected = Color(0xFFC3121F)
private val Human = Color(0xFF4CC36B)
private val Mauve = Color(0xFF9B30D9)
private val Ink = Color(0xFF1A1414)

@Composable
fun BloodTest666Row(dishes: List<Health666>, running: Boolean, modifier: Modifier = Modifier) {
    if (dishes.isEmpty() && !running) return
    val list = if (dishes.isEmpty()) List(4) { Health666("…", "", null) } else dishes
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        Text("Le test sanguin · Avant-poste 31", color = Mauve, fontSize = 15.sp)
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.padding(top = 8.dp).width(760.dp).aspectRatio(1600f / 619f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        ) {
            val wDp = maxWidth.value
            val hDp = maxHeight.value
            fun x(px: Float) = (px / SCENE_W * wDp).dp
            fun y(px: Float) = ((px - SCENE_TOP) / SCENE_H * hDp).dp
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.sang_banc_666),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
            list.take(DISHES.size).forEachIndexed { i, row ->
                val d = DISHES[i]
                if (row.ok == false) Splash666(Modifier.offset(x(d.l + 0.056f * (d.r - d.l)), y(d.t - 0.5f * (d.b - d.t))).width(x(0.973f * (d.r - d.l))))
                // le nom du cavalier, écrit à la main sur le ruban
                androidx.compose.foundation.layout.Box(
                    Modifier.offset(x(d.tapeX - 170f), y(d.tapeY - 40f)).width(x(340f)).height(y(SCENE_TOP + 80f))
                        .graphicsLayer { rotationZ = -2.5f },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        BloodTest666.shortName(row.name), color = Ink, fontSize = (wDp * 0.019f).sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.layout.Box(
                    Modifier.offset(x(d.tapeX - 170f), y(d.b + 4f)).width(x(340f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        BloodTest666.verdict(row.ok),
                        color = when (row.ok) { true -> Human; false -> Infected; null -> Color(0xFFC8C3BE) },
                        fontSize = (wDp * (if (row.ok == false) 0.024f else 0.017f)).sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(1f, 2f), 6f))
                    )
                }
            }
        }
        // plus de 4 cavaliers : les suivants en texte
        list.drop(DISHES.size).forEach { row ->
            Text(
                "${BloodTest666.shortName(row.name)} : ${BloodTest666.verdict(row.ok)}",
                color = when (row.ok) { true -> Human; false -> Infected; null -> Color(0xFF8F8A85) },
                fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Le sang qui bondit hors de la boîte (il retombe et rebondit, comme dans le film). */
@Composable
private fun Splash666(modifier: Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - start) / 1000f }
    }
    val cycle = t % 2.4f
    val jump = if (cycle < 0.9f) sin(cycle / 0.9f * Math.PI.toFloat()) else 0f
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.sang_eclabousse_666),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
        modifier = modifier.graphicsLayer {
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            scaleY = 0.55f + 0.45f * jump
            scaleX = 0.9f + 0.1f * jump
            alpha = 0.35f + 0.65f * jump
        }
    )
}
