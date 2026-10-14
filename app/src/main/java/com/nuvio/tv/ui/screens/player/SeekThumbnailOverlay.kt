package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 666TV : vignette au-dessus de la barre de progression pendant qu'on avance ou recule.
 * L'image est demandée dès qu'on vise une nouvelle position (une à la fois).
 */
@Composable
internal fun SeekThumbnailOverlay(
    viewModel: PlayerViewModel,
    previewPositionMs: Long?,
    controlsVisible: Boolean
) {
    val timeline by viewModel.playbackTimeline.collectAsState()
    var shown by remember { mutableStateOf<Pair<Long, ImageBitmap>?>(null) }
    val position = previewPositionMs ?: return
    if (timeline.isLive || timeline.duration <= 0L) return

    val bucket = position / 10_000L
    val latestPosition = rememberUpdatedState(position)
    // Une image à la fois : pendant qu'elle arrive, on ne garde que la dernière position visée.
    LaunchedEffect(Unit) {
        snapshotFlow { latestPosition.value / 10_000L }
            .distinctUntilChanged()
            .conflate()
            .collect { wanted ->
                val bitmap = viewModel.seekThumbnail(wanted * 10_000L + 5_000L) ?: return@collect
                shown = wanted to bitmap.asImageBitmap()
            }
    }
    val (shownBucket, image) = shown ?: return
    val isCurrent = shownBucket == bucket

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val thumbWidth = 256.dp
        val thumbHeight = 144.dp
        val sidePadding = 48.dp
        val fraction = (position.toFloat() / timeline.duration.toFloat()).coerceIn(0f, 1f)
        val available = (maxWidth - sidePadding * 2 - thumbWidth).coerceAtLeast(0.dp)
        val shape = RoundedCornerShape(10.dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = sidePadding + available * fraction)
                .padding(bottom = if (controlsVisible) 160.dp else 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(thumbWidth, thumbHeight)
                    .clip(shape)
                    .background(Color.Black)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), shape)
                    .alpha(if (isCurrent) 1f else 0.45f)
            )
            Text(
                text = formatThumbTime(position),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

private fun formatThumbTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
