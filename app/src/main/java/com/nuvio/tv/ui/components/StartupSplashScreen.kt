package com.nuvio.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R

private val Splash666Bg = Color(0xFF0C0F0E)

/** 666TVF (10/11) : l'ouverture est à l'écran (la morgue ne s'ouvre pas, le néon se tait dessous). */
object Splash666 {
    @Volatile var showing: Boolean = false
    /** À l'arrivée sur l'accueil : le curseur va sur le bouton mauve (une fois par ouverture). */
    @Volatile var focusHeroOnce: Boolean = true
}
private val Blood666 = Color(0xFF4E1077)

// Positions sur l'image de la salle (16:9), en fraction de la largeur / hauteur.
private val EYES = listOf(0.4042f to 0.4542f, 0.4507f to 0.4542f)
private const val LOGO_X = 0.5047f
private const val LOGO_Y = 0.3483f
private const val LOGO_W = 0.2174f
private const val LOGO_RATIO = 543f / 996f
// Portes (image de la porte, 16:9) : bord gauche, milieu, bord droit, haut, bas.
private const val DOOR_X0 = 0.2485f
private const val DOOR_XM = 0.505f
private const val DOOR_X1 = 0.7625f
private const val DOOR_Y0 = 0.1227f
private const val DOOR_Y1 = 0.929f

/**
 * 666TVF (10/11) : l'ouverture « cinéma ». Une salle abandonnée aux sièges mauves ; sur l'écran, le
 * diable projeté (yeux jaunes qui pulsent) et le logo 666TVF qui goutte du sang mauve ; la lumière du
 * projecteur vacille, la bobine cliquette ; le pentagramme tourne pendant le chargement.
 * Quand l'appli est prête ([closing]) : fondu vers la porte, qui s'ouvre lentement sur la boucherie
 * pendant que la bobine baisse doucement, puis [onClosed].
 */
@Composable
fun StartupSplashScreen(
    profileColorHex: String?,
    profileBackgroundUrl: String?,
    backgroundCacheKey: String? = null,
    skipGradient: Boolean = false,
    brandWordmarkRes: Int? = null,
    modifier: Modifier = Modifier,
    closing: Boolean = false,
    onClosed: () -> Unit = {}
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val onClosedNow by rememberUpdatedState(onClosed)
    DisposableEffect(Unit) {
        Splash666.showing = true
        Splash666.focusHeroOnce = true
        onDispose { Splash666.showing = false }
    }

    // --- Son : la bobine du projecteur tourne tant que la salle est à l'écran ---
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val projector = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    // volume réel de la bobine : le fondu final part de là (11/11 : il repartait de 0.7 → bref bruit à l'arrivée)
    val projectorVol = remember { floatArrayOf(0.7f) }
    DisposableEffect(lifecycleOwner) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        projector[0] = runCatching {
            android.media.MediaPlayer.create(context, R.raw.projecteur_666)?.apply {
                isLooping = false   // 75 s d'une traite : pas de reprise audible
                setVolume(0.7f, 0.7f)
                start()
            }
        }.getOrNull()
        fun fadeOut() {
            val mp = projector[0] ?: return
            projector[0] = null
            val from = projectorVol[0]
            if (from <= 0.01f) { runCatching { mp.stop(); mp.release() }; return }
            var step = 0
            val fade = object : Runnable {
                override fun run() {
                    step++
                    val v = (from * (1f - step / 10f)).coerceAtLeast(0f)
                    runCatching { mp.setVolume(v, v) }
                    if (step < 10) handler.postDelayed(this, 60) else runCatching { mp.stop(); mp.release() }
                }
            }
            handler.post(fade)
        }
        val cap = Runnable { fadeOut() }
        handler.postDelayed(cap, 75_000L)
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

    // --- La lumière du projecteur vacille (avec de temps en temps une baisse, vieille bobine) ---
    var flicker by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val r = java.util.Random(6)
        var v = 0f
        while (true) {
            v = v * 0.7f + 0.3f * (r.nextFloat() * 0.06f)
            flicker = if (r.nextFloat() < 0.025f) 0.16f else v
            kotlinx.coroutines.delay(80)
        }
    }

    // --- Fermeture : fondu vers la porte, ouverture lente, fondu sur la boucherie ---
    val toDoor = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    val finalFade = remember { Animatable(0f) }
    val push = remember { Animatable(0f) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        // la bobine baisse doucement jusqu'à l'ouverture des portes et au fondu (pas de grincement)
        val fade = launch {
            val steps = 43
            repeat(steps) { i ->
                val v = 0.7f * (1f - (i + 1).toFloat() / steps)
                projectorVol[0] = v
                projector[0]?.let { mp -> runCatching { mp.setVolume(v, v) } }
                kotlinx.coroutines.delay(100)
            }
        }
        toDoor.animateTo(1f, tween(700))
        kotlinx.coroutines.delay(400)
        // 12/11 : les portes s'ouvrent et on avance sans s'arrêter, jusqu'à ce que les montants sortent
        // de l'écran : on est dans l'abattoir. Plus de fondu (rien ne reste à effacer).
        kotlinx.coroutines.coroutineScope {
            launch { open.animateTo(1f, tween(3_200, easing = FastOutSlowInEasing)) }
            kotlinx.coroutines.delay(1_100)
            push.animateTo(1f, tween(2_500, easing = androidx.compose.animation.core.CubicBezierEasing(0.5f, 0f, 0.9f, 0.6f)))
        }
        finalFade.snapTo(1f)
        fade.join()
        onClosedNow()
    }

    val transition = rememberInfiniteTransition(label = "cinema666")
    val eyes by transition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eyes"
    )
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "spin"
    )
    val drip by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing)),
        label = "drip"
    )

    // Tout s'efface à la fin de l'ouverture des portes : l'accueil (la boucherie) est dessous.
    val fadeOut = finalFade.value
    // 11/11 : une fois la porte affichée, plus de fond plein : l'embrasure laisse voir le VRAI accueil
    Box(modifier = modifier.fillMaxSize().graphicsLayer { alpha = 1f - fadeOut }
        .background(if (toDoor.value < 1f) Splash666Bg else Color.Transparent)) {
        // ---------------- La salle ----------------
        if (toDoor.value < 1f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - toDoor.value }) {
            Image(
                painter = painterResource(R.drawable.cinema_bg_666),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                val h = maxHeight
                // Logo 666TVF projeté sur l'écran (sans bouclier)
                Image(
                    painter = painterResource(R.drawable.splash_logo_666),
                    contentDescription = stringResource(R.string.cd_nuvio_logo),
                    modifier = Modifier
                        .size(width = w * LOGO_W, height = w * LOGO_W * LOGO_RATIO)
                        .offset(x = w * LOGO_X, y = h * LOGO_Y)
                )
                // Pentagramme qui tourne : c'est le « chargement »
                Image(
                    painter = painterResource(R.drawable.splash_penta_666),
                    contentDescription = null,
                    modifier = Modifier
                        .size(w * 0.16f)
                        .offset(x = w * 0.5f - w * 0.08f, y = h * 0.835f - w * 0.08f)
                        .graphicsLayer { rotationZ = spin }
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Yeux jaunes du diable qui pulsent
                EYES.forEach { (fx, fy) ->
                    val c = Offset(w * fx, h * fy)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFC93C).copy(alpha = 0.55f * eyes), Color(0xFFFF9F1A).copy(alpha = 0.2f * eyes), Color.Transparent),
                            center = c, radius = w * 0.012f
                        ),
                        radius = w * 0.012f, center = c, blendMode = BlendMode.Plus
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.8f * eyes), Color(0xFFFFE58A).copy(alpha = 0.7f * eyes), Color.Transparent),
                            center = c, radius = w * 0.0042f
                        ),
                        radius = w * 0.0042f, center = c, blendMode = BlendMode.Plus
                    )
                }
                // Sang mauve qui goutte du 666 et du menton du diable
                val logoBottom = h * LOGO_Y + w * LOGO_W * LOGO_RATIO * 0.9f
                val sources = listOf(
                    Triple(LOGO_X + LOGO_W * 0.13f, logoBottom, 0f), Triple(LOGO_X + LOGO_W * 0.29f, logoBottom, 0.4f),
                    Triple(LOGO_X + LOGO_W * 0.45f, logoBottom, 0.75f), Triple(0.4292f, h * 0.566f, 0.2f)
                )
                sources.forEach { (fx, top, offset) ->
                    val ph = (drip + offset) % 1f
                    val x = w * fx
                    val bottom = h * 0.60f
                    val r = w * 0.0024f
                    when {
                        ph < 0.45f -> {
                            val g = ph / 0.45f
                            drawCircle(Blood666, r * (0.4f + 0.6f * g), Offset(x, top + r * g))
                        }
                        ph < 0.95f -> {
                            val f = (ph - 0.45f) / 0.5f
                            val y = top + (bottom - top) * f * f
                            drawLine(Blood666.copy(alpha = 0.8f * (1f - f * 0.6f)), Offset(x, top), Offset(x, y - r), strokeWidth = r * 0.6f + 1f, cap = StrokeCap.Round)
                            drawOval(Blood666, Offset(x - r, y - r), Size(r * 2f, r * 2.4f))
                        }
                        else -> {
                            val f = (ph - 0.95f) / 0.05f
                            drawOval(Blood666.copy(alpha = 1f - f), Offset(x - r * (1f + f * 3f), bottom - r * 0.4f),
                                Size(r * 2f * (1f + f * 3f), r * 0.8f), style = Stroke(width = r * 0.4f))
                        }
                    }
                }
            }
            // Le projecteur qui vacille
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flicker)))
        }

        // ---------------- La porte ----------------
        if (toDoor.value > 0f) {
            // ×2,05 autour du centre de l'embrasure : les montants sont alors hors de l'écran
            val zoom = 1f + push.value * 1.05f
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = toDoor.value; scaleX = zoom; scaleY = zoom
                transformOrigin = TransformOrigin((DOOR_X0 + DOOR_X1) / 2f, (DOOR_Y0 + DOOR_Y1) / 2f)
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen   // pour percer l'embrasure
            }) {
                Image(
                    painter = painterResource(R.drawable.porte_666),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = maxWidth
                    val h = maxHeight
                    val leafW = w * (DOOR_XM - DOOR_X0)
                    val leafWR = w * (DOOR_X1 - DOOR_XM)
                    val leafH = h * (DOOR_Y1 - DOOR_Y0)
                    // Derrière les portes : l'embrasure est percée, on voit l'accueil lui-même (une seule image, pas de saut)
                    if (open.value > 0f) Canvas(
                        Modifier
                            .size(width = w * (DOOR_X1 - DOOR_X0), height = leafH)
                            .offset(x = w * DOOR_X0, y = h * DOOR_Y0)
                    ) { drawRect(Color.Black, blendMode = androidx.compose.ui.graphics.BlendMode.Clear) }
                    val angle = 100f * open.value
                    // les battants s'assombrissent en tournant
                    val shade = 1f - 0.45f * open.value
                    val leafShade = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(shade, shade, shade, 1f) }
                    )
                    Image(
                        painter = painterResource(R.drawable.porte_g_666),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        colorFilter = leafShade,
                        modifier = Modifier
                            .size(width = leafW, height = leafH)
                            .offset(x = w * DOOR_X0, y = h * DOOR_Y0)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                rotationY = angle   // poussée : le battant part vers l'intérieur
                                cameraDistance = 6f * density.density
                                alpha = if (angle > 89f) 0f else 1f
                            }
                    )
                    Image(
                        painter = painterResource(R.drawable.porte_d_666),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        colorFilter = leafShade,
                        modifier = Modifier
                            .size(width = leafWR, height = leafH)
                            .offset(x = w * DOOR_XM, y = h * DOOR_Y0)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                rotationY = -angle
                                cameraDistance = 6f * density.density
                                alpha = if (angle > 89f) 0f else 1f
                            }
                    )
                }
                // Lueur mauve derrière les hublots
                Canvas(Modifier.fillMaxSize()) {
                    if (open.value > 0.05f) return@Canvas
                    listOf(0.375f, 0.632f).forEach { fx ->
                        val c = Offset(size.width * fx, size.height * 0.378f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF9B30D9).copy(alpha = 0.35f * eyes), Color.Transparent),
                                center = c, radius = size.width * 0.06f
                            ),
                            radius = size.width * 0.06f, center = c, blendMode = BlendMode.Plus
                        )
                    }
                }
            }
        }
    }
}
