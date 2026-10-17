package com.nuvio.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R

private val Splash666Bg = Color(0xFF0C0F0E)
private val Splash666Green = Color(0xFF19C88E)
private val Splash666Blood = Color(0xFFC3121F)
private val Splash666BloodDark = Color(0xFF8A0010)

/** Taille du logo en pixels : la même que celle de l'écran de lancement Android, pour un enchaînement sans saut. */
private const val SPLASH_WORDMARK_WIDTH_PX = 540
private const val SPLASH_WORDMARK_HEIGHT_PX = 201

/**
 * 666TV : écran d'ouverture animé. Le logo bat comme un cœur, un halo rouge pulse derrière,
 * une goutte de sang tombe du bouclier et un anneau rouge tourne en dessous.
 */
@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val logoWidth = with(density) { SPLASH_WORDMARK_WIDTH_PX.toDp() }
    val logoHeight = with(density) { SPLASH_WORDMARK_HEIGHT_PX.toDp() }

    val transition = rememberInfiniteTransition(label = "splash666")
    val beat by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                1f at 0
                1.045f at 140 using FastOutSlowInEasing
                1f at 300
                1.03f at 440 using FastOutSlowInEasing
                1f at 640
            }
        ),
        label = "splashBeat"
    )
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splashGlow"
    )
    val drop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "splashDrop"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "splashSpin"
    )

    Box(modifier = modifier.fillMaxSize().background(Splash666Bg)) {
        if (!profileBackgroundUrl.isNullOrBlank()) {
            val imageData: Any = if (profileBackgroundUrl.startsWith("file:")) {
                java.io.File(java.net.URI(profileBackgroundUrl))
            } else {
                profileBackgroundUrl
            }
            val request = ImageRequest.Builder(LocalContext.current)
                .data(imageData)
                .crossfade(false)
            if (backgroundCacheKey != null) {
                request.memoryCacheKey(backgroundCacheKey)
                    .diskCacheKey(backgroundCacheKey)
                    .placeholderMemoryCacheKey(backgroundCacheKey)
            }
            AsyncImage(
                model = request.build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Lueur verte 666 dans le coin bas droit, halo rouge qui pulse derrière le logo.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Splash666Green.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = size.maxDimension * 0.6f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Splash666Blood.copy(alpha = glow * 0.55f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.55f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Espace symétrique à l'anneau, pour que le logo reste pile au centre comme au lancement.
            Spacer(modifier = Modifier.height(72.dp))
            Box(
                modifier = Modifier
                    .size(width = logoWidth, height = logoHeight)
                    .graphicsLayer {
                        scaleX = beat
                        scaleY = beat
                    }
            ) {
                Image(
                    painter = painterResource(R.drawable.splash_wordmark_666),
                    contentDescription = stringResource(R.string.cd_nuvio_logo),
                    modifier = Modifier.fillMaxSize()
                )
                // Goutte de sang qui tombe sous le bouclier.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val x = size.width * 0.118f
                    val startY = size.height * 0.86f
                    val fall = size.height * 0.55f
                    val y = startY + fall * drop * drop
                    val r = size.height * 0.03f
                    val alpha = (1f - drop).coerceIn(0f, 1f)
                    drawOval(
                        color = Splash666BloodDark.copy(alpha = alpha),
                        topLeft = Offset(x - r, y - r * 1.3f),
                        size = Size(r * 2f, r * 2.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Canvas(modifier = Modifier.size(40.dp)) {
                val stroke = 4.dp.toPx()
                drawCircle(color = Splash666Blood.copy(alpha = 0.18f), style = Stroke(width = stroke))
                drawArc(
                    color = Splash666Blood,
                    startAngle = spin,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}
