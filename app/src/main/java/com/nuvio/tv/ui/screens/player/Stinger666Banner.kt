package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.core.tools.Stinger666

/**
 * FrankenStream (15/11) : au début du générique de fin d'un film qui cache une scène
 * (pendant ou après le générique), un bandeau prévient de ne pas couper. Une seule fois.
 */
@Composable
internal fun Stinger666Banner(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { viewModel.loadStinger666() }
    val kind by viewModel.stinger666.collectAsState()
    val timeline by viewModel.playbackTimeline.collectAsState()
    var shown by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val k = kind ?: return
    if (timeline.isLive) return
    val warnAt = Stinger666.warnAtMs(timeline.duration, viewModel.creditsStartMs666())
    LaunchedEffect(timeline.currentPosition >= (warnAt ?: Long.MAX_VALUE)) {
        if (!shown && warnAt != null && timeline.currentPosition >= warnAt && timeline.currentPosition < timeline.duration - 5_000L) {
            shown = true
            visible = true
            kotlinx.coroutines.delay(15_000L)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier.padding(top = 36.dp)
    ) {
        Text(
            text = Stinger666.warning(k),
            color = Color(0xFFF2EFE9),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color(0xE6141018), RoundedCornerShape(14.dp))
                .border(2.dp, Color(0xFF9B5CC4), RoundedCornerShape(14.dp))
                .padding(horizontal = 26.dp, vertical = 14.dp)
        )
    }
}
