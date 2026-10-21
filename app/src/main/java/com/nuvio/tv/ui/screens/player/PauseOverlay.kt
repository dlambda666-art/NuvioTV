package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.domain.model.MetaCastMember
import android.text.format.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.localizeEpisodeTitle

@Composable
fun PauseOverlay(
    visible: Boolean,
    onClose: () -> Unit,
    title: String,
    logo: String?,
    episodeTitle: String?,
    season: Int?,
    episode: Int?,
    year: String?,
    type: String?,
    description: String?,
    cast: List<MetaCastMember>,
    modifier: Modifier = Modifier,
    showClock: Boolean = true
) {
    var selectedCastMember by remember { mutableStateOf<MetaCastMember?>(null) }

    // 666TV : léger voile rouge sang sur les bords pendant la pause.
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, Color(0x99460006)),
                    center = center,
                    radius = size.maxDimension * 0.62f
                )
            )
        }
    }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onClose,
        modifier = modifier,
        captureKeys = false,
        dismissOnBackgroundClick = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = NuvioTheme.spacing.huge,
            end = NuvioTheme.spacing.huge,
            top = 40.dp,
            bottom = 120.dp
        ),
        topEndContent = {
            if (showClock) {
                PauseOverlayClock()
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (selectedCastMember != null) {
                CastDetailView(
                    member = selectedCastMember!!,
                    onBack = { selectedCastMember = null }
                )
            } else {
                PauseMetadataView(
                    title = title,
                    logo = logo,
                    episodeTitle = episodeTitle,
                    season = season,
                    episode = episode,
                    year = year,
                    type = type,
                    description = description,
                    cast = cast,
                    onCastSelected = { selectedCastMember = it }
                )
            }
        }
    }
}

@Composable
private fun PauseOverlayClock(modifier: Modifier = Modifier) {
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }

    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            nowMillis = current
            val delayMs = (60_000L - (current % 60_000L)).coerceAtLeast(1_000L)
            delay(delayMs)
        }
    }

    Text(
        text = formatter.format(Date(nowMillis)),
        style = com.nuvio.tv.ui.components.Font666.horror(MaterialTheme.typography.headlineSmall).copy(fontSize = 40.sp),
        color = com.nuvio.tv.ui.components.Font666.Green,
        modifier = modifier
    )
}

@Composable
private fun PauseMetadataView(
    title: String,
    logo: String?,
    episodeTitle: String?,
    season: Int?,
    episode: Int?,
    year: String?,
    type: String?,
    description: String?,
    cast: List<MetaCastMember>,
    onCastSelected: (MetaCastMember) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.pause_you_are_watching),
                style = com.nuvio.tv.ui.components.Font666.horror(MaterialTheme.typography.titleLarge),
                color = com.nuvio.tv.ui.components.Font666.Green
            )

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

            if (!logo.isNullOrBlank()) {
                var logoFailed by remember(logo) { mutableStateOf(false) }
                if (!logoFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(logo)
                            .memoryCacheKey(logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.height(96.dp),
                        onError = { logoFailed = true }
                    )
                } else {
                    Text(
                        text = title,
                        style = com.nuvio.tv.ui.components.Font666.horror(MaterialTheme.typography.headlineLarge, 1.6f),
                        color = com.nuvio.tv.ui.components.Font666.Green,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = title,
                    style = com.nuvio.tv.ui.components.Font666.horror(MaterialTheme.typography.headlineLarge, 1.6f),
                    color = com.nuvio.tv.ui.components.Font666.Green,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!year.isNullOrBlank()) {
                val episodeLabel = if (type in listOf("series", "tv") && season != null && episode != null) {
                    " • " + stringResource(R.string.season_episode_format, season, episode)
                } else {
                    ""
                }

                Text(
                    text = "$year$episodeLabel",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.colors.TextSecondary,
                    modifier = Modifier.padding(top = NuvioTheme.spacing.sm)
                )
            }

            if (!episodeTitle.isNullOrBlank()) {
                val context = LocalContext.current
                Text(
                    text = episodeTitle.localizeEpisodeTitle(context),
                    style = MaterialTheme.typography.titleLarge,
                    color = com.nuvio.tv.ui.components.Font666.Green,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = NuvioTheme.spacing.md)
                )
            }

            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = description.contentTextDirection()
                    ),
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = NuvioTheme.spacing.lg)
                )
            }

            if (cast.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.pause_cast_label),
                    style = com.nuvio.tv.ui.components.Font666.horror(MaterialTheme.typography.titleMedium),
                    color = com.nuvio.tv.ui.components.Font666.Green
                )

                Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    content = {
                        items(cast.take(8)) { member ->
                            CastChip(member = member, onClick = { onCastSelected(member) })
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CastChip(
    member: MetaCastMember,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f)
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.md))
    ) {
        Text(
            text = member.name,
            style = MaterialTheme.typography.bodyMedium,
            color = com.nuvio.tv.ui.components.Font666.Green,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CastDetailView(
    member: MetaCastMember,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = NuvioTheme.colors.TextSecondary,
                modifier = Modifier.size(NuvioTheme.spacing.xl)
            )
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
            Text(
                text = stringResource(R.string.pause_back_to_details),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary,
                modifier = Modifier.clickable(onClick = onBack)
            )
        }

        Row(
            verticalAlignment = Alignment.Top
        ) {
            if (!member.photo.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(member.photo)
                        .crossfade(true)
                        .build(),
                    contentDescription = member.name,
                    modifier = Modifier
                        .size(width = 160.dp, height = 240.dp)
                        .clip(RoundedCornerShape(NuvioTheme.radii.xl)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(28.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = com.nuvio.tv.ui.components.Font666.Green,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!member.character.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.pause_as_character, member.character ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.colors.TextSecondary,
                        modifier = Modifier.padding(top = NuvioTheme.spacing.sm),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
