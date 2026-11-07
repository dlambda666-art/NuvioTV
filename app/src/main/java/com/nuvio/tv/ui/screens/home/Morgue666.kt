package com.nuvio.tv.ui.screens.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.ui.theme.Creepster666Family
import kotlinx.coroutines.delay

/**
 * 666TVF (07/11) : la morgue. Sous « Reprendre en VF », la rangée de petites affiches est remplacée
 * par UNE tuile (la porte de la morgue) : on voit la boucherie. Arrêté sur la tuile, les battants
 * s'ouvrent, la boucherie passe en morgue (même minutage que les portes du cimetière) et on entre :
 * tes étiquettes « Continuer à regarder » s'affichent en grille sur le fond de la morgue.
 * Trois pièces : boucherie, morgue, cimetière.
 */
object Morgue666 {
    private const val PREFS = "666tv_morgue"

    fun enabled(context: Context): Boolean =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true) }.getOrDefault(true)

    fun setEnabled(context: Context, on: Boolean) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply() }
    }

    /** Vrai pendant que les battants s'ouvrent : la boucherie glisse vers la morgue. */
    var opening by mutableStateOf(false)

    /** Vrai quand on est dans la morgue (grille des étiquettes). */
    var open by mutableStateOf(false)

    /** Au retour de la morgue, la tuile garde le focus sans se rouvrir tant qu'on ne la quitte pas. */
    var calmUntilBlur = false

    /** Position des battants dans l'image de la porte (fractions de la tuile 16:9). */
    const val DOOR_LEFT = 0.27f
    const val DOOR_RIGHT = 0.73f
    const val DOOR_TOP = 0.06f
    const val DOOR_BOTTOM = 0.975f
    /** Ouverture des battants (dans le temps du fondu de 4 s, comme la dalle du cimetière). */
    const val LEAVES_MS = 1_800

    /** Texte de l'étiquette de la tuile (pur, testé). */
    fun countLabel(n: Int): String = "$n en cours"

    fun lastLabel(item: ContinueWatchingItem?): String? = when (item) {
        null -> null
        is ContinueWatchingItem.InProgress -> item.progress.name +
            (if (item.progress.season != null && item.progress.episode != null) " · S%02dE%02d".format(item.progress.season, item.progress.episode) else "")
        is ContinueWatchingItem.NextUp -> item.info.name + " · S%02dE%02d".format(item.info.season, item.info.episode)
    }
}

/** La tuile-porte, à la place de la rangée « Continuer à regarder ». */
@Composable
fun MorgueTile666(
    count: Int,
    last: String?,
    width: Dp,
    height: Dp,
    requester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var leaves by remember { mutableStateOf(false) }
    val latestOnFocused by rememberUpdatedState(onFocused)
    val enter = {
        // la morgue reste au fond pendant qu'on est dedans (plus de retour éclair à la boucherie)
        Morgue666.opening = true
        Morgue666.calmUntilBlur = true
        Morgue666.open = true
    }
    LaunchedEffect(focused) {
        if (!focused) {
            // focus parti DANS la morgue : on ne touche à rien (portes ouvertes, fond morgue)
            if (Morgue666.open) return@LaunchedEffect
            leaves = false
            Morgue666.opening = false
            Morgue666.calmUntilBlur = false
            return@LaunchedEffect
        }
        latestOnFocused()
        if (Morgue666.calmUntilBlur) return@LaunchedEffect
        delay(Door666.LIFT_MS)
        leaves = true
        Morgue666.opening = true                 // le fond passe à la morgue pendant que les battants s'ouvrent
        if (!Door666.autoEnter) return@LaunchedEffect
        delay(Door666.OPEN_MS - Door666.LIFT_MS)
        enter()
    }
    // retour de la morgue : le focus revient sur la porte (fermée, sans se rouvrir)
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Morgue666.open) {
        if (wasOpen && !Morgue666.open) {
            leaves = false
            delay(120); runCatching { requester.requestFocus() }
        }
        wasOpen = Morgue666.open
    }
    val p by animateFloatAsState(
        targetValue = if (leaves) 1f else 0f,
        animationSpec = tween(durationMillis = if (leaves) Morgue666.LEAVES_MS else 900, easing = FastOutSlowInEasing),
        label = "morgueLeaves666"
    )
    val glow by animateFloatAsState(if (focused) 1f else 0f, label = "morgueGlow666")
    val door: ImageBitmap = ImageBitmap.imageResource(R.drawable.tile_morgue_666)
    val inside: ImageBitmap = ImageBitmap.imageResource(R.drawable.bg_morgue_666)
    val density = LocalDensity.current
    val green = Color(0xFF78E66E)
    Box(
        modifier = modifier
            .size(width, height)
            .graphicsLayer { val s = 1f + 0.04f * glow; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(14.dp))
            .border(if (focused) 3.dp else 0.dp, if (focused) green else Color.Transparent, RoundedCornerShape(14.dp))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) { enter(); true } else ok
            }
            .focusable()
    ) {
        Image(painterResource(R.drawable.tile_morgue_666), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (p > 0.001f) {
            val dl = width * Morgue666.DOOR_LEFT
            val dt = height * Morgue666.DOOR_TOP
            val dw = width * (Morgue666.DOOR_RIGHT - Morgue666.DOOR_LEFT)
            val dh = height * (Morgue666.DOOR_BOTTOM - Morgue666.DOOR_TOP)
            // derrière les battants : la morgue
            Box(
                Modifier.offset(dl, dt).size(dw, dh).drawBehind {
                    val iw = inside.width; val ih = inside.height
                    val sw = (iw * 0.34f).toInt(); val sh = (sw * size.height / size.width).toInt().coerceAtMost(ih)
                    drawImage(
                        inside,
                        srcOffset = IntOffset((iw - sw) / 2, (ih - sh) / 2),
                        srcSize = IntSize(sw, sh),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                    drawRect(Color(0xFF78FF8C).copy(alpha = 0.22f * (1f - p)))
                }
            )
            // les deux battants, ouverts en 3D sur leurs gonds
            for (left in listOf(true, false)) {
                Box(
                    Modifier
                        .offset(if (left) dl else dl + dw / 2, dt)
                        .size(dw / 2, dh)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
                            rotationY = (if (left) -1f else 1f) * 100f * p
                            cameraDistance = 6f * density.density
                        }
                        .drawBehind {
                            val bw = door.width; val bh = door.height
                            val sx = (bw * (Morgue666.DOOR_LEFT + (if (left) 0f else (Morgue666.DOOR_RIGHT - Morgue666.DOOR_LEFT) / 2f))).toInt()
                            val sy = (bh * Morgue666.DOOR_TOP).toInt()
                            val sw = (bw * (Morgue666.DOOR_RIGHT - Morgue666.DOOR_LEFT) / 2f).toInt()
                            val sh = (bh * (Morgue666.DOOR_BOTTOM - Morgue666.DOOR_TOP)).toInt()
                            drawImage(door, IntOffset(sx, sy), IntSize(sw, sh), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                            drawRect(Color.Black.copy(alpha = 0.5f * p))
                        }
                )
            }
        }
        val textAlpha = 1f - (p * 2f).coerceAtMost(1f)
        if (textAlpha > 0f) {
            Box(
                Modifier.align(Alignment.BottomStart).size(width * 0.5f, height * 0.42f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .graphicsLayer { alpha = textAlpha }
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp, end = 14.dp)
                    .graphicsLayer { alpha = textAlpha }
            ) {
                Text(Morgue666.countLabel(count), style = TextStyle(fontFamily = Creepster666Family, fontSize = 24.sp, color = green))
                if (last != null) {
                    Text(
                        "Dernier : $last",
                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFEBE6F0), fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // le nombre d'en-cours, à l'encre sur l'étiquette de la poignée
            Text(
                text = count.toString(),
                style = TextStyle(fontFamily = Creepster666Family, fontSize = (height.value * 0.12f).sp, color = Color(0xFF3A1712),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(width * 0.524f, height * 0.68f)
                    .size(width * 0.06f, height * 0.14f)
                    .graphicsLayer { alpha = textAlpha }
            )
        }
    }
}

/** Portes des 8 tiroirs dans l'image du mur (fractions de l'écran 16:9), 4 colonnes × 2 rangées. */
internal object Drawers666 {
    val COLS = listOf(0.1161f to 0.2803f, 0.3188f to 0.4815f, 0.5220f to 0.6847f, 0.7257f to 0.8884f)
    val ROWS = listOf(0.2242f to 0.5004f, 0.5336f to 0.8072f)
    const val PER_WALL = 8

    /** Règle pure (testée) : nombre de murs pour n en-cours. */
    fun walls(n: Int): Int = if (n <= 0) 1 else (n + PER_WALL - 1) / PER_WALL

    /** Règle pure (testée) : SxxEyy de l'étiquette, rien pour un film. */
    fun code(season: Int?, episode: Int?): String =
        if (season != null && episode != null && season > 0) "S%02dE%02d".format(season, episode) else ""
}

private data class Drawer666(val backdrop: String?, val logo: String?, val poster: String?, val code: String, val progress: Float)

private fun ContinueWatchingItem.drawer666(): Drawer666 = when (this) {
    is ContinueWatchingItem.InProgress -> Drawer666(
        progress.backdrop, progress.logo, progress.poster,
        Drawers666.code(progress.season, progress.episode), progress.progressPercentage
    )
    is ContinueWatchingItem.NextUp -> Drawer666(info.backdrop, info.logo, info.poster, Drawers666.code(info.season, info.episode), 0f)
}

/**
 * Dans la morgue : le mur de tiroirs. Chaque porte porte l'image de fond de la série + son logo
 * (sinon l'affiche), le SxxEyy dans le porte-étiquette et la progression verte. 8 par mur ; flèche
 * bas / haut pour changer de mur. Appui long = options ; Retour = on ressort.
 */
@Composable
fun Morgue666Overlay(
    items: List<ContinueWatchingItem>,
    @Suppress("UNUSED_PARAMETER") cardWidth: Dp,
    @Suppress("UNUSED_PARAMETER") cardHeight: Dp,
    @Suppress("UNUSED_PARAMETER") cardStyle: ContinueWatchingCardStyle,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp,
    @Suppress("UNUSED_PARAMETER") blurUnwatchedEpisodes: Boolean,
    @Suppress("UNUSED_PARAMETER") useEpisodeThumbnails: Boolean,
    onClick: (ContinueWatchingItem) -> Unit,
    onOptions: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = Morgue666.open,
        enter = fadeIn(tween(700, easing = LinearEasing)),
        exit = fadeOut(tween(600, easing = LinearEasing)),
        modifier = modifier
    ) {
        BackHandler(enabled = Morgue666.open) { Morgue666.open = false; Morgue666.opening = false }
        LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) { Morgue666.open = false; Morgue666.opening = false } }
        var wall by remember { mutableStateOf(0) }
        val walls = Drawers666.walls(items.size)
        if (wall >= walls) wall = walls - 1
        val requesters = remember { List(Drawers666.PER_WALL) { FocusRequester() } }
        var focusSlot by remember { mutableStateOf(0) }
        LaunchedEffect(wall) { delay(80); runCatching { requesters[focusSlot.coerceAtMost(((items.size - wall * Drawers666.PER_WALL) - 1).coerceAtLeast(0))].requestFocus() } }
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
        ) {
            val sw = maxWidth; val sh = maxHeight
            Image(painterResource(R.drawable.bg_morgue_tiroirs_666), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Text(
                "Continuer à regarder",
                style = TextStyle(fontFamily = Creepster666Family, fontSize = 34.sp, color = Color(0xFF78E66E)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
            )
            for (slot in 0 until Drawers666.PER_WALL) {
                val index = wall * Drawers666.PER_WALL + slot
                val item = items.getOrNull(index) ?: continue
                val col = slot % 4; val row = slot / 4
                val (x0, x1) = Drawers666.COLS[col]; val (y0, y1) = Drawers666.ROWS[row]
                DrawerDoor666(
                    drawer = item.drawer666(),
                    requester = requesters[slot],
                    modifier = Modifier.offset(sw * x0, sh * y0).size(sw * (x1 - x0), sh * (y1 - y0)),
                    onFocused = { focusSlot = slot },
                    onClick = { onClick(item) },
                    onLongPress = { onOptions(item) },
                    onDownEdge = if (row == 1 && wall < walls - 1) ({ focusSlot = col; wall += 1 }) else null,
                    onUpEdge = if (row == 0 && wall > 0) ({ focusSlot = 4 + col; wall -= 1 }) else null
                )
            }
            if (walls > 1) {
                Text(
                    "Mur ${wall + 1}/$walls  ·  ▼ ▲ pour changer de mur",
                    style = TextStyle(fontSize = 14.sp, color = Color(0xFFC8C3BE)),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerDoor666(
    drawer: Drawer666,
    requester: FocusRequester,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDownEdge: (() -> Unit)?,
    onUpEdge: (() -> Unit)?
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "drawer666")
    val green = Color(0xFF78E66E)
    val useLogo = !drawer.backdrop.isNullOrBlank() && !drawer.logo.isNullOrBlank()
    val image = if (useLogo) drawer.backdrop else (drawer.poster ?: drawer.backdrop)
    Box(
        modifier
            .graphicsLayer {
                // le tiroir sélectionné s'entrouvre vers toi
                val s = 1f + 0.05f * glow
                scaleX = s; scaleY = s
                shadowElevation = 18f * glow
            }
            .border(if (focused) 3.dp else 0.dp, if (focused) green else Color.Transparent, RoundedCornerShape(8.dp))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    ok && e.type == KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount >= 4 && !longFired) { longFired = true; onLongPress() }
                        true
                    }
                    ok && e.type == KeyEventType.KeyUp -> { if (!longFired) onClick(); longFired = false; true }
                    e.key == Key.DirectionDown && e.type == KeyEventType.KeyDown && onDownEdge != null -> { onDownEdge(); true }
                    e.key == Key.DirectionUp && e.type == KeyEventType.KeyDown && onUpEdge != null -> { onUpEdge(); true }
                    else -> false
                }
            }
            .focusable()
            .padding(6.dp)
    ) {
        // l'image collée sur la porte (un peu de métal sale reste visible au travers)
        coil3.compose.AsyncImage(
            model = image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).graphicsLayer { alpha = 0.9f }
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        if (useLogo) {
            coil3.compose.AsyncImage(
                model = drawer.logo, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 20.dp)
                    .fillMaxWidth().height(46.dp)
            )
        }
        // porte-étiquette : carte papier avec le SxxEyy
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                .background(Color(0xFF46423F), RoundedCornerShape(2.dp))
                .border(2.dp, Color(0xFF242020), RoundedCornerShape(2.dp))
                .padding(4.dp)
                .background(Color(0xFFDECEAA))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Text(
                drawer.code.ifBlank { "      " },
                style = TextStyle(fontSize = 15.sp, color = Color(0xFF281912), fontWeight = FontWeight.SemiBold)
            )
        }
        // progression
        if (drawer.progress > 0f) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 7.dp).fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha = 0.8f)))
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 7.dp)
                    .fillMaxWidth(drawer.progress.coerceIn(0.02f, 1f) * 0.94f).height(5.dp).background(green)
            )
        }
    }
}
