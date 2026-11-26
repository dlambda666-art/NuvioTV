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
        set(v) { field = v; visible.value = v }
    /** 25/11 : même chose en état Compose : tant que le cinéma est là, l'accueil n'affiche pas son pentacle de chargement. */
    val visible = androidx.compose.runtime.mutableStateOf(false)
    /** À l'arrivée sur l'accueil : le curseur va sur le bouton mauve (une fois par ouverture). */
    @Volatile var focusHeroOnce: Boolean = true
}
/**
 * 23/11 (réparation 3) : LA bobine du cinéma, une seule pour toute l'appli. Démarrer quand elle tourne déjà
 * ne fait rien ; s'arrêter se fait toujours en fondu (jamais net).
 */
object Bobine666 {
    const val VOLUME = 0.7f
    // 25/11 (David : « garde la bobine qui s'arrête, et lance MH quand les portes sont ouvertes ; pas de fin de
    // bobine sur le son de MH ») : la bobine s'éteint en douceur PENDANT l'ouverture des portes (4,5 s), Machine
    // Head ne démarre qu'une fois les portes ouvertes (DOORS_MS) : jamais les deux en même temps.
    // 25/11 (David : « MH une seconde plus tôt ») : Machine Head part 1 s avant la fin de l'ouverture,
    // la bobine est éteinte pile à ce moment-là (jamais sa fin sur Machine Head)
    const val CROSSFADE_MS = 3_900L
    /** Durée de l'ouverture : fondu vers la porte 0,7 s + 0,4 s + battants / avancée 3,8 s. */
    const val DOORS_MS = 700L + 400L + 1_100L + 2_700L
    /** Départ de Machine Head (depuis le début de la fermeture du cinéma). */
    const val MH_AT_MS = DOORS_MS - 1_000L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var player: android.media.MediaPlayer? = null
    private var vol = VOLUME

    /** Règle pure (testée) : volume du fondu de sortie à l'instant t (part du volume courant, descend jusqu'à 0). */
    fun fadeLevel(from: Float, tMs: Long, durationMs: Long): Float {
        if (durationMs <= 0 || tMs >= durationMs) return 0f
        if (tMs <= 0) return from
        // 24/11 (David : « encore trop sec ») : avant (cube) la bobine avait presque disparu en 1,5 s.
        // Maintenant courbe en cloche : elle reste présente au début, se mêle à Machine Head au milieu,
        // et s'éteint tout doucement (aucune cassure ni au début ni à la fin).
        val u = tMs.toFloat() / durationMs
        return (from * 0.5f * (1f + kotlin.math.cos(Math.PI.toFloat() * u))).coerceIn(0f, from)
    }

    fun start(context: android.content.Context) {
        if (player != null) return
        player = runCatching {
            android.media.MediaPlayer.create(context.applicationContext, R.raw.projecteur_666)?.apply {
                isLooping = false   // 75 s d'une traite
                setVolume(VOLUME, VOLUME)
                setOnCompletionListener { mp -> if (player === mp) player = null; runCatching { mp.release() } }
                start()
            }
        }.getOrNull()
        vol = VOLUME
    }

    fun fadeOut(durationMs: Long) {
        val mp = player ?: return
        player = null
        val from = vol
        val t0 = android.os.SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val t = android.os.SystemClock.uptimeMillis() - t0
                val v = fadeLevel(from, t, durationMs)
                runCatching { mp.setVolume(v, v) }
                if (v <= 0f) { runCatching { mp.stop() }; runCatching { mp.release() } }
                else handler.postDelayed(this, 40L)
            }
        }
        handler.post(step)
    }
}

private val Blood666 = Color(0xFF4E1077)

// Positions sur l'image de la salle (16:9), en fraction de la largeur / hauteur.
// 14/11 : FrankenStream — Frank ado à la place du diable, le nom (vert « Franken », mauve « Stream ») à droite
private const val LOGO_X = 0.5250f
private const val LOGO_Y = 0.3444f
private const val LOGO_W = 0.1979f
private const val LOGO_RATIO = 1011f / 2000f
private const val FRANK_X = 0.3344f
private const val FRANK_Y = 0.3083f
private const val FRANK_W = 0.1823f
private const val FRANK_RATIO = 855f / 1100f
private val BloodGreen666 = Color(0xFF288C14)
// Gouttes : (x, haut) en fraction de l'écran, vert sous FRANKEN, mauve sous STREAM, décalage dans la boucle
private data class Drip666(val fx: Float, val fy: Float, val green: Boolean, val offset: Float)
private val DRIPS = listOf(
    Drip666(0.5724f, 0.4289f, true, 0f), Drip666(0.5476f, 0.4263f, true, 0.55f),
    Drip666(0.6543f, 0.4928f, false, 0.4f), Drip666(0.7012f, 0.4911f, false, 0.75f), Drip666(0.6226f, 0.4898f, false, 0.2f)
)
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
    LaunchedEffect(Unit) { runCatching { com.nuvio.tv.ui.screens.home.AbattoirSound666.preload(context) } }
    DisposableEffect(Unit) {
        Splash666.showing = true
        Splash666.focusHeroOnce = true
        onDispose { Splash666.showing = false }
    }

    // --- Son : la bobine du projecteur tourne tant que la salle est à l'écran ---
    // 23/11 (réparation 3) : une seule bobine pour tout Frank (Bobine666) : jamais deux à la fois, jamais
    // coupée net ; à l'ouverture des portes elle s'efface pendant que Machine Head monte (fondu croisé).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        Bobine666.start(context)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) Bobine666.fadeOut(300)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Bobine666.fadeOut(800)
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
    // 13/11 : les flaques sous les gouttes grandissent doucement pendant l'ouverture (comme dans l'abattoir)
    val pool = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pool.animateTo(1f, tween(12_000, easing = LinearEasing)) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        // 25/11 (David) : la bobine s'éteint pendant l'ouverture ; Machine Head attend que les portes soient ouvertes
        Bobine666.fadeOut(Bobine666.CROSSFADE_MS)
        toDoor.animateTo(1f, tween(700))
        kotlinx.coroutines.delay(400)
        // 12/11 : les portes s'ouvrent et on avance sans s'arrêter, jusqu'à ce que les montants sortent
        // de l'écran : on est dans l'abattoir. Plus de fondu (rien ne reste à effacer).
        kotlinx.coroutines.coroutineScope {
            // 24/11 (David : « léger sursaut à l'ouverture ») : démarrage et arrivée tout en douceur
            launch { open.animateTo(1f, tween(3_400, easing = androidx.compose.animation.core.CubicBezierEasing(0.42f, 0f, 0.3f, 1f))) }
            kotlinx.coroutines.delay(1_100)
            // Machine Head : 1 s avant la fin de l'ouverture (bobine éteinte à ce moment-là)
            launch {
                kotlinx.coroutines.delay(Bobine666.MH_AT_MS - 700L - 400L - 1_100L)
                runCatching { com.nuvio.tv.ui.screens.home.AbattoirSound666.onDoorsOpening(context) }
            }
            push.animateTo(1f, tween(2_700, easing = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.55f, 1f)))
        }
        finalFade.snapTo(1f)
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
                // Frank ado projeté sur l'écran (à la place du diable)
                Image(
                    painter = painterResource(R.drawable.frank_ado_666),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = w * FRANK_W, height = w * FRANK_W * FRANK_RATIO)
                        .offset(x = w * FRANK_X, y = h * FRANK_Y)
                )
                // Le nom FrankenStream projeté sur l'écran
                Image(
                    painter = painterResource(R.drawable.splash_logo_666),
                    contentDescription = stringResource(R.string.cd_nuvio_logo),
                    modifier = Modifier
                        .size(width = w * LOGO_W, height = w * LOGO_W * LOGO_RATIO)
                        .offset(x = w * LOGO_X, y = h * LOGO_Y)
                )
                // 26/11 (David) : son bouclier viking Lambda666 qui tourne : c'est le « chargement » (avant : pentagramme)
                Image(
                    painter = painterResource(R.drawable.bouclier_666),
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
                // Sang vert qui goutte de FRANKEN, mauve de STREAM ; chaque goutte a sa flaque de la même couleur
                DRIPS.forEach { d ->
                    val fx = d.fx; val top = h * d.fy; val offset = d.offset
                    val blood = if (d.green) BloodGreen666 else Blood666
                    val ph = (drip + offset) % 1f
                    val x = w * fx
                    val bottom = h * 0.60f
                    val r = w * 0.0024f
                    // La flaque : elle grandit avec le temps et gonfle un peu à chaque goutte qui tombe
                    val hit = if (ph >= 0.95f) 1f + 0.12f * kotlin.math.sin(((ph - 0.95f) / 0.05f) * Math.PI.toFloat()) else 1f
                    val pr = r * (1.0f + 3.0f * pool.value) * hit
                    // forme irrégulière : trois taches qui se chevauchent, sang sombre, petit reflet
                    val k = (fx * 97f) % 1f
                    val pool666 = if (d.green) Color(0xFF12460A) else Color(0xFF34094F)
                    drawOval(pool666.copy(alpha = 0.6f), Offset(x - pr * (1.15f + 0.3f * k), bottom - pr * 0.24f), Size(pr * 1.3f, pr * 0.48f))
                    drawOval(pool666.copy(alpha = 0.6f), Offset(x - pr * 0.1f, bottom - pr * 0.26f), Size(pr * (1.1f + 0.4f * k), pr * 0.42f))
                    drawOval(pool666.copy(alpha = 0.9f), Offset(x - pr * 0.8f, bottom - pr * 0.25f), Size(pr * 1.6f, pr * 0.46f))
                    drawOval((if (d.green) Color(0xFF78FF5A) else Color(0xFF9B5CC4)).copy(alpha = 0.25f), Offset(x - pr * 0.45f, bottom - pr * 0.18f), Size(pr * 0.4f, pr * 0.1f))
                    when {
                        ph < 0.45f -> {
                            val g = ph / 0.45f
                            drawCircle(blood, r * (0.4f + 0.6f * g), Offset(x, top + r * g))
                        }
                        ph < 0.95f -> {
                            val f = (ph - 0.45f) / 0.5f
                            val y = top + (bottom - top) * f * f
                            drawLine(blood.copy(alpha = 0.8f * (1f - f * 0.6f)), Offset(x, top), Offset(x, y - r), strokeWidth = r * 0.6f + 1f, cap = StrokeCap.Round)
                            drawOval(blood, Offset(x - r, y - r), Size(r * 2f, r * 2.4f))
                        }
                        else -> {
                            val f = (ph - 0.95f) / 0.05f
                            drawOval(blood.copy(alpha = 1f - f), Offset(x - r * (1f + f * 3f), bottom - r * 0.4f),
                                Size(r * 2f * (1f + f * 3f), r * 0.8f), style = Stroke(width = r * 0.4f))
                        }
                    }
                }
            }
            // Le projecteur qui vacille
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flicker)))
        }

        // ---------------- La porte ----------------
        // 25/11 (David : « sursaut à l'ouverture ») : les images de la porte étaient chargées au moment même où le
        // fondu vers la porte commençait (à-coup). Elles sont maintenant prêtes dès le début du cinéma
        // (invisibles tant que toDoor = 0) : le passage à la porte est fluide.
        run {
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
                                cameraDistance = 12f * density.density   // 24/11 : moins de déformation en tournant (sursaut)
                                alpha = ((90f - angle) / 20f).coerceIn(0f, 1f)   // 24/11 : s'efface en douceur au lieu de disparaître d'un coup
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
                                cameraDistance = 12f * density.density   // 24/11 : moins de déformation en tournant (sursaut)
                                alpha = ((90f - angle) / 20f).coerceIn(0f, 1f)   // 24/11 : s'efface en douceur au lieu de disparaître d'un coup
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
