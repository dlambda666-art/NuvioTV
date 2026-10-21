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
import androidx.compose.foundation.layout.offset
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
    // 666TV : éclairs qui reviennent de temps en temps pendant le chargement.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2_200)
        while (true) {
            flash.snapTo(0.45f)
            flash.animateTo(0f, tween(220))
            kotlinx.coroutines.delay(80)
            flash.snapTo(0.3f)
            flash.animateTo(0f, tween(480))
            kotlinx.coroutines.delay(2_500L + (Math.random() * 3_000).toLong())
        }
    }

    val transition = rememberInfiniteTransition(label = "splash666")
    val beat by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                1f at 0
                1.035f at 140 using FastOutSlowInEasing
                1f at 300
                1.02f at 440 using FastOutSlowInEasing
                1f at 640
            }
        ),
        label = "splashBeat"
    )
    // Les yeux du diable s'allument et s'éteignent doucement.
    val eyes by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splashEyes"
    )
    // Le pentagramme tourne lentement : c'est le « chargement ».
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "splashSpin"
    )
    // Fumée qui monte vite (0..1 en boucle).
    val smokeT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
        label = "splashSmoke"
    )
    // Les deux gouttes de sang (bouclier puis 666), tour à tour.
    val drip by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing)),
        label = "splashDrip"
    )
    // Fumée des volcans, qui monte franchement.
    val plumeT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing)),
        label = "splashPlume"
    )
    val plumes = androidx.compose.runtime.remember {
        val r = java.util.Random(13)
        // cratère (x, y en fraction de l'écran), puis 10 bouffées chacune : décalage, dérive
        listOf(0.768f to 0.35f, 0.284f to 0.39f).map { (cx, cy) ->
            Triple(cx, cy, List(10) { floatArrayOf(it / 10f + r.nextFloat() * 0.05f, (r.nextFloat() - 0.3f)) })
        }
    }
    val puffs = androidx.compose.runtime.remember {
        val r = java.util.Random(666)
        List(18) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.08f + r.nextFloat() * 0.12f, 0.6f + r.nextFloat() * 0.9f) }
    }

    Box(modifier = modifier.fillMaxSize().background(Splash666Bg)) {
        Image(
            painter = painterResource(R.drawable.splash_bg_666),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Yeux qui brûlent + fumée qui monte.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            listOf(806f / 1672f, 951f / 1672f).forEach { fx ->
                val c = Offset(w * fx, h * (284f / 941f))
                // grand halo orange, puis cœur blanc-jaune qui brûle (en lumière additive)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF8A00).copy(alpha = 0.7f * eyes), Color(0xFFFF5A00).copy(alpha = 0.25f * eyes), Color.Transparent),
                        center = c,
                        radius = w * 0.045f
                    ),
                    radius = w * 0.045f,
                    center = c,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Plus
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = eyes), Color(0xFFFFE08A).copy(alpha = 0.95f * eyes), Color(0xFFFF9A1F).copy(alpha = 0.5f * eyes), Color.Transparent),
                        center = c,
                        radius = w * 0.017f
                    ),
                    radius = w * 0.017f,
                    center = c,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Plus
                )
            }
            // Voile de fumée sur le visage du diable : on le devine plus qu'on ne le voit.
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1311).copy(alpha = 0.72f), Color(0xFF1A1311).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(w * 0.53f, h * 0.34f),
                    radius = w * 0.17f
                ),
                topLeft = Offset(w * 0.36f, h * 0.06f),
                size = Size(w * 0.34f, h * 0.56f)
            )
            // Fumée qui sort des volcans.
            plumes.forEach { (cx, cy, list) ->
                list.forEach { pf ->
                    val prog = (plumeT + pf[0]) % 1f
                    val rise = prog * h * 0.3f
                    val x = w * cx + pf[1] * prog * w * 0.06f + kotlin.math.sin((prog * 9f + pf[0] * 20f).toDouble()).toFloat() * w * 0.004f
                    val y = h * cy - rise
                    val r = w * (0.012f + prog * 0.05f)
                    val a = (if (prog < 0.15f) prog / 0.15f else 1f - (prog - 0.15f) / 0.85f) * 0.75f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1C1715).copy(alpha = a), Color(0xFF2A2220).copy(alpha = a * 0.5f), Color.Transparent),
                            center = Offset(x, y),
                            radius = r
                        ),
                        radius = r,
                        center = Offset(x, y)
                    )
                }
            }
            puffs.forEach { p ->
                val prog = (smokeT * p[3] + p[1]) % 1f
                val x = w * (p[0] + kotlin.math.sin((prog * 6.28f + p[0] * 10f).toDouble()).toFloat() * 0.02f)
                val y = h * (1.1f - prog * 1.25f)
                val r = w * p[2] * (0.7f + prog * 0.6f)
                val a = (kotlin.math.sin((prog * Math.PI).toFloat())) * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF241A17).copy(alpha = a), Color.Transparent),
                        center = Offset(x, y),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(x, y)
                )
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            // Bouclier
            Image(
                painter = painterResource(R.drawable.splash_shield_666),
                contentDescription = null,
                modifier = Modifier
                    .size(w * 0.23f)
                    .offset(x = w * 0.415f - w * 0.115f, y = h * 0.43f - w * 0.115f)
            )
            // Logo 666TV
            Image(
                painter = painterResource(R.drawable.splash_logo_666),
                contentDescription = stringResource(R.string.cd_nuvio_logo),
                modifier = Modifier
                    .size(width = w * 0.28f, height = w * 0.28f * 0.601f)
                    .offset(x = w * 0.53f, y = h * 0.42f - w * 0.28f * 0.601f / 2f)
            )
            // Pentagramme qui tourne, avec un halo sombre derrière pour le détacher de la lave.
            Box(
                modifier = Modifier
                    .size(w * 0.16f)
                    .offset(x = w * 0.5f - w * 0.08f, y = h * 0.835f - w * 0.08f)
                    .background(
                        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.6f), Color.Transparent))
                    )
            ) {
                Image(
                    painter = painterResource(R.drawable.splash_penta_666),
                    contentDescription = null,
                    // 666TV : un peu plus sombre, pour qu'il ressorte sur la lave.
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(0.42f, 0.34f, 0.34f, 1f) }
                    ),
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }
                )
            }
        }
        // Gouttes de sang : une du bouclier, puis une du 666.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            listOf(Triple(0.405f, 0.615f, 1.03f), Triple(0.585f, 0.555f, 1.03f)).forEachIndexed { i, (fx, y0, y1) ->
                val ph = (drip - i * 0.5f + 1f) % 1f * 2f   // 0..1 : sa demi-période, >1 : repos
                if (ph > 1f) return@forEachIndexed
                val x = w * fx
                val top = h * y0
                val rMax = w * (if (i == 0) 0.0058f else 0.0047f)
                val blood = Color(0xFF7D000C)
                if (ph < 0.45f) {
                    // la goutte grossit au bord
                    val g = ph / 0.45f
                    val r = rMax * (0.4f + 0.6f * g)
                    drawCircle(blood, r, Offset(x, top + r * g))
                } else if (ph < 0.97f) {
                    // elle tombe, de plus en plus vite
                    val f = (ph - 0.45f) / 0.52f
                    val y = top + (h * y1 - top) * f * f
                    // un filet de sang reste derrière elle
                    drawLine(blood.copy(alpha = 0.8f * (1f - f * 0.6f)), Offset(x, top), Offset(x, y - rMax), strokeWidth = rMax * 0.45f + 1f, cap = StrokeCap.Round)
                    drawOval(blood, Offset(x - rMax, y - rMax), Size(rMax * 2f, rMax * 2.4f))
                    drawCircle(Color(0xAAFF8C8C), rMax * 0.25f, Offset(x - rMax * 0.35f, y - rMax * 0.2f))
                } else {
                    // ploc dans la flaque
                    val f = (ph - 0.97f) / 0.03f
                    drawOval(
                        blood.copy(alpha = 1f - f),
                        Offset(x - rMax * (1f + f * 3f), h * y1 - rMax * 0.4f),
                        Size(rMax * 2f * (1f + f * 3f), rMax * 0.8f),
                        style = Stroke(width = rMax * 0.4f)
                    )
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
