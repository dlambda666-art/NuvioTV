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
        Morgue666.opening = false
        Morgue666.calmUntilBlur = true
        Morgue666.open = true
    }
    LaunchedEffect(focused) {
        if (!focused) {
            leaves = false
            Morgue666.opening = false
            // focus parti dans la morgue : au retour, la porte reste calme
            if (!Morgue666.open) Morgue666.calmUntilBlur = false
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
        if (wasOpen && !Morgue666.open) { delay(120); runCatching { requester.requestFocus() } }
        wasOpen = Morgue666.open
    }
    val p by animateFloatAsState(
        targetValue = if (leaves) 1f else 0f,
        animationSpec = tween(durationMillis = if (leaves) Morgue666.LEAVES_MS else 300, easing = FastOutSlowInEasing),
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
            // le nombre d'en-cours, à l'encre sur l'étiquette de la poignée
            Text(
                text = count.toString(),
                style = TextStyle(fontFamily = Creepster666Family, fontSize = (height.value * 0.11f).sp, color = Color(0xFF46201A)),
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(width * 0.535f, height * 0.69f)
                    .graphicsLayer { alpha = textAlpha }
            )
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().size(width, height * 0.42f)
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
        }
    }
}

/** Dans la morgue : toutes tes étiquettes « Continuer à regarder », sur le fond de la morgue. */
@Composable
fun Morgue666Overlay(
    items: List<ContinueWatchingItem>,
    cardWidth: Dp,
    cardHeight: Dp,
    cardStyle: ContinueWatchingCardStyle,
    cornerRadius: Dp,
    blurUnwatchedEpisodes: Boolean,
    useEpisodeThumbnails: Boolean,
    onClick: (ContinueWatchingItem) -> Unit,
    onOptions: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = Morgue666.open,
        enter = fadeIn(tween(500, easing = LinearEasing)),
        exit = fadeOut(tween(400, easing = LinearEasing)),
        modifier = modifier
    ) {
        BackHandler(enabled = Morgue666.open) { Morgue666.open = false }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
        LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) Morgue666.open = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                painterResource(R.drawable.bg_morgue_666), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 34.dp, end = 48.dp)) {
                Text(
                    "Continuer à regarder",
                    style = TextStyle(fontFamily = Creepster666Family, fontSize = 40.sp, color = Color(0xFF78E66E))
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cardWidth + 8.dp),
                    // le focus ne sort pas de la morgue (retour = touche Retour)
                    modifier = Modifier.fillMaxSize()
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup(),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(items, key = { _, it -> it.contentId() + ":" + it.season() + ":" + it.episode() }) { index, item ->
                        var focused by remember { mutableStateOf(false) }
                        com.nuvio.tv.ui.components.ContinueWatchingCard(
                            item = item,
                            onClick = { onClick(item) },
                            onLongPress = { onOptions(item) },
                            cardWidth = cardWidth,
                            imageHeight = cardHeight,
                            blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                            useEpisodeThumbnails = useEpisodeThumbnails,
                            cardStyle = cardStyle,
                            cornerRadius = cornerRadius,
                            isFocused = focused,
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                        )
                    }
                }
            }
        }
    }
}
