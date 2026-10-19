package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.core.tools.Clock666
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val shadow666 = Shadow(Color.Black, Offset(0f, 2f), 8f)

/** 666TV : heure + météo, en haut à droite de l'accueil. */
@Composable
fun HomeClock666(modifier: Modifier = Modifier) {
    if (!Clock666.enabled) return
    var now by remember { mutableStateOf(Date()) }
    var weather by remember { mutableStateOf<Clock666.Weather?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(20_000L)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            weather = withContext(Dispatchers.IO) { Clock666.weather() } ?: weather
            delay(30 * 60_000L)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.FRANCE).format(now) }
    val day = remember(now) { SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(now) }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            weather?.let {
                Text(
                    "${it.icon} ${it.tempC}°",
                    style = TextStyle(fontSize = 20.sp, color = Color(0xFFE6E0DA), shadow = shadow666),
                    modifier = Modifier.padding(end = 14.dp)
                )
            }
            Text(time, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CC36B), shadow = shadow666))
        }
        Text(day, style = TextStyle(fontSize = 13.sp, color = Color(0xFFC8C3BE), shadow = shadow666))
    }
}

/**
 * 666TV : au réveil de la Shield, un bouton « Reprendre » sur le dernier film / épisode,
 * pendant 20 secondes. OK le relance, les flèches vers le bas le font disparaître.
 */
@Composable
fun ResumeOnWake666(
    items: List<ContinueWatchingItem>,
    onResume: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val wake by com.nuvio.tv.core.wake.WakeLaunch666.wakeNonce.collectAsState()
    val seenWake = remember { mutableIntStateOf(wake) }
    var target by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    LaunchedEffect(wake) {
        if (wake == seenWake.intValue) return@LaunchedEffect
        seenWake.intValue = wake
        val item = items.firstOrNull() ?: return@LaunchedEffect
        target = item
        delay(20_000L)
        target = null
    }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    AnimatedVisibility(visible = target != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val item = target ?: return@AnimatedVisibility
        LaunchedEffect(item) { delay(300); runCatching { focus.requestFocus() } }
        val label = when (item) {
            is ContinueWatchingItem.InProgress -> {
                val p = item.progress
                val ep = if (p.season != null && p.episode != null) " · S%02dE%02d".format(p.season, p.episode) else ""
                p.name + ep
            }
            is ContinueWatchingItem.NextUp -> {
                val i = item.info
                val ep = if (i.season != null && i.episode != null) " · S%02dE%02d".format(i.season, i.episode) else ""
                i.name + ep
            }
        }
        Surface(
            onClick = { target = null; onResume(item) },
            modifier = Modifier
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xCC1A0003),
                focusedContainerColor = Color(0xFFA10614),
                contentColor = Color.White,
                focusedContentColor = Color.White
            )
        ) {
            Text(
                "▶  Reprendre : $label",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp)
            )
        }
    }
}
