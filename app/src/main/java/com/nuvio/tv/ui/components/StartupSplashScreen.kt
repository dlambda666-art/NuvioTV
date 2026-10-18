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
    val context = LocalContext.current
    // 666TV : un éclair (sans claquement) et le tonnerre qui roule, comme un jumpscare.
    val flash = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    // Le tonnerre gronde tant que l'écran d'ouverture est affiché, puis s'éteint en douceur.
    // Il s'arrête aussi si 666TV passe en arrière-plan, et au plus tard après 15 s.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var player: android.media.MediaPlayer? = runCatching {
            android.media.MediaPlayer.create(context, R.raw.thunder_666)?.apply {
                isLooping = true
                setVolume(0.85f, 0.85f)
                start()
            }
        }.getOrNull()
        fun fadeOut() {
            val mp = player ?: return
            player = null
            var step = 0
            val fade = object : Runnable {
                override fun run() {
                    step++
                    val v = (0.85f * (1f - step / 10f)).coerceAtLeast(0f)
                    runCatching { mp.setVolume(v, v) }
                    if (step < 10) handler.postDelayed(this, 60) else runCatching { mp.stop(); mp.release() }
                }
            }
            handler.post(fade)
        }
        val cap = Runnable { fadeOut() }
        handler.postDelayed(cap, 15_000L)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) fadeOut()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            handler.removeCallbacks(cap)
            lifecycleOwner.lifecycle.removeObserver(observer)
            fadeOut()
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(90)
        flash.snapTo(0.6f)
        flash.animateTo(0f, tween(180))
        kotlinx.coroutines.delay(70)
        flash.snapTo(0.4f)
        flash.animateTo(0f, tween(420))
    }
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
    // 0..1 : goutte de gauche, 1..2 : goutte de droite (à tour de rôle).
    val drop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "splashDrop"
    )
    val penta by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "splashPenta"
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
            Spacer(modifier = Modifier.height(96.dp))
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
                // Gouttes de sang : une coulure à gauche, puis une à droite.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = drop < 1f
                    val p = if (left) drop else drop - 1f
                    val x = size.width * (if (left) 0.119f else 0.204f)
                    val startY = size.height * (if (left) 0.89f else 0.80f)
                    val fall = size.height * 0.55f
                    val y = startY + fall * p * p
                    val r = size.height * 0.03f
                    val alpha = (1f - p).coerceIn(0f, 1f)
                    drawOval(
                        color = Splash666BloodDark.copy(alpha = alpha),
                        topLeft = Offset(x - r, y - r * 1.3f),
                        size = Size(r * 2f, r * 2.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            // Pentagramme inversé qui se trace tout seul, puis s'efface.
            Canvas(modifier = Modifier.size(64.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension * 0.40f
                val pts = (0..5).map { i ->
                    val a = Math.PI + i * 144.0 * Math.PI / 180.0
                    Offset(cx + (r * Math.sin(a)).toFloat(), cy - (r * Math.cos(a)).toFloat())
                }
                val frac = (penta / 0.75f).coerceAtMost(1f)
                val alpha = if (penta < 0.85f) 1f else (1f - (penta - 0.85f) / 0.15f).coerceIn(0f, 1f)
                drawCircle(
                    color = Splash666Blood.copy(alpha = 0.35f),
                    radius = r + 5.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                val seg = (0 until 5).map { (pts[it + 1] - pts[it]).getDistance() }
                var remaining = seg.sum() * frac
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x, pts[0].y) }
                var tip = pts[0]
                for (k in 0 until 5) {
                    if (remaining <= 0f) break
                    val a = pts[k]
                    val b = pts[k + 1]
                    val t = (remaining / seg[k]).coerceAtMost(1f)
                    tip = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    path.lineTo(tip.x, tip.y)
                    remaining -= seg[k]
                }
                // Halo puis trait.
                drawPath(
                    path,
                    color = Splash666Blood.copy(alpha = 0.35f * alpha),
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
                drawPath(
                    path,
                    color = Splash666Blood.copy(alpha = alpha),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
                if (frac < 1f) {
                    drawCircle(color = Color(0xFFFF3A45), radius = 3.5.dp.toPx(), center = tip)
                }
            }
        }
        if (flash.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFDDE6FF).copy(alpha = flash.value))
            )
        }
    }
}
