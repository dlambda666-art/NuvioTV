package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * LambdaTV : rangée « Top 10 » façon Netflix.
 * Toute rangée dont le titre contient « Top 10 » affiche un grand chiffre
 * détouré derrière chacune de ses 10 premières affiches.
 */
private val TOP_TEN_TITLE_REGEX = Regex("""(?i)\btop\s*10\b""")

internal const val TOP_TEN_MAX_RANK = 10

internal fun isTopTenRowTitle(title: String?): Boolean =
    title != null && TOP_TEN_TITLE_REGEX.containsMatchIn(title)

/** Part du chiffre laissée visible avant que l'affiche ne le recouvre. */
private const val RANK_VISIBLE_FRACTION = 0.8f

// 666TV : chiffres couleur sang, contour rouge vif et halo.
private val RankFill = Brush.verticalGradient(
    listOf(Color(0xFFA10614), Color(0xFF5A000A))
)
private val RankOutline = Brush.verticalGradient(
    listOf(Color(0xFFFF3A45), Color(0xFFC3121F))
)
private val RankGlow = Color(0x8CC3121F)

private fun rankFontHeight(cardHeight: Dp): Dp = cardHeight * 0.88f

/** Décalage horizontal de l'affiche pour laisser voir le chiffre. */
internal fun topTenRankOffset(rank: Int, cardHeight: Dp): Dp {
    val digitWidth = rankFontHeight(cardHeight) * 0.56f
    val digits = if (rank >= 10) 1.75f else 1f
    return digitWidth * digits * RANK_VISIBLE_FRACTION
}

/**
 * Enveloppe une affiche : le chiffre est dessiné en dessous, l'affiche
 * par-dessus, décalée vers la droite. La base du chiffre est alignée
 * sur le bas de l'affiche (même avec les titres affichés sous l'affiche).
 */
@Composable
internal fun TopTenRankedItem(
    rank: Int,
    cardHeight: Dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val fontSize = with(density) { rankFontHeight(cardHeight).toSp() }
    val layout = remember(rank, fontSize, measurer) {
        measurer.measure(
            text = rank.toString(),
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.08).em
            ),
            softWrap = false,
            maxLines = 1
        )
    }
    val strokePx = with(density) { 3.dp.toPx() }
    val offset = topTenRankOffset(rank, cardHeight)
    val numberWidth = with(density) { layout.size.width.toDp() }

    Box {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(width = numberWidth, height = cardHeight)
        ) {
            // Base du chiffre posée sur le bas de l'affiche.
            val topLeft = Offset(0f, size.height - layout.firstBaseline)
            // Halo : contour épais et transparent sous le chiffre.
            drawText(
                textLayoutResult = layout,
                color = RankGlow,
                topLeft = topLeft,
                drawStyle = Stroke(width = strokePx * 5f)
            )
            drawText(textLayoutResult = layout, brush = RankFill, topLeft = topLeft)
            drawText(
                textLayoutResult = layout,
                brush = RankOutline,
                topLeft = topLeft,
                drawStyle = Stroke(width = strokePx)
            )
        }
        Box(modifier = Modifier.padding(start = offset)) {
            content()
        }
    }
}
