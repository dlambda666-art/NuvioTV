package com.nuvio.tv.ui.components

import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource

/**
 * 666TV : partout où l'appli charge, le pentagramme vert de l'écran d'ouverture tourne.
 * (`color` est gardé pour la compatibilité, le pentagramme a ses propres couleurs.)
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") color: Color = NuvioTheme.colors.TextSecondary
) {
    val spin by rememberInfiniteTransition(label = "penta666").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "penta666Spin"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.splash_penta_666),
            contentDescription = null,
            modifier = Modifier
                .sizeIn(maxWidth = NuvioTheme.spacing.xxxl, maxHeight = NuvioTheme.spacing.xxxl)
                .fillMaxSize()
                .aspectRatio(1f)
                .graphicsLayer { rotationZ = spin }
        )
    }
}
