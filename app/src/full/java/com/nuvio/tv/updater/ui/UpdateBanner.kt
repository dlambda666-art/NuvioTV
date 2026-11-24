@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.updater.ui

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.ThemeColors
import com.nuvio.tv.ui.theme.accentBrush
import com.nuvio.tv.updater.UpdateUiState
import com.nuvio.tv.updater.model.AppUpdate

@Composable
internal fun UpdateBanner(
    state: UpdateUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val targetProgress = (if (state.isDownloading) state.downloadProgress ?: 0f else 0f)
        .coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 180),
        label = "updateBannerProgress"
    )
    // 24/11 (David) : MOTHER, l'ordinateur du Nostromo (Alien) : écran noir, texte vert phosphore qui se tape
    val containerColor = Mother666.Black
    val isWhiteTheme = NuvioTheme.currentTheme == AppTheme.WHITE
    val progressAlpha = if (isWhiteTheme) 0.18f else 1f
    val dividerColor = Mother666.Green.copy(alpha = 0.55f)
    val subtitle = when {
        state.errorMessage != null -> state.errorMessage
        state.isDownloading && state.downloadProgress != null -> stringResource(
            R.string.update_downloading_progress,
            (state.downloadProgress * 100).toInt().coerceIn(0, 100)
        )
        state.isDownloading -> stringResource(R.string.update_preparing_download)
        state.downloadedApkPath != null -> stringResource(R.string.update_download_complete)
        else -> stringResource(R.string.update_available)
    }
    val updateLabel = listOfNotNull(
        update.tag,
        update.assetSizeBytes?.let { Formatter.formatShortFileSize(context, it) }
    ).joinToString(separator = " • ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(containerColor)
                // lignes de balayage du vieux moniteur
                val step = 3.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawRect(Mother666.Green.copy(alpha = 0.05f), topLeft = Offset(0f, y), size = Size(size.width, 1f))
                    y += step
                }
                if (progress > 0f) {
                    drawRect(
                        // 666TVF : vert au départ, mauve à l'arrivée (28/10)
                        // 24/11 : MOTHER : la barre de chargement en vert phosphore
                        color = Mother666.Green.copy(alpha = 0.28f),
                        size = Size(width = size.width * progress, height = size.height)
                    )
                }
                drawRect(
                    color = dividerColor,
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(width = size.width, height = 1.dp.toPx())
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 32.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "MOTHER",
                color = Mother666.Green,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 6.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val line1 = Mother666.line(updateLabel)
                val typed = Mother666.rememberTyped(line1)
                val cursorOn = Mother666.rememberBlink()
                Text(
                    text = typed + if (cursorOn) "█" else " ",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mother666.Green,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "> " + subtitle.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = if (state.errorMessage != null) NuvioTheme.colors.Error else Mother666.Green.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            UpdateBannerIconButton(
                icon = Icons.Default.Info,
                contentDescription = stringResource(R.string.update_release_notes),
                onClick = onShowReleaseNotes,
                enabled = update.notes.isNotBlank()
            )

            Button(
                onClick = {
                    when {
                        state.isDownloading -> Unit
                        state.downloadedApkPath != null -> onInstall()
                        else -> onDownload()
                    }
                },
                enabled = state.isDownloading ||
                    state.downloadedApkPath != null ||
                    state.isUpdateAvailable,
                colors = ButtonDefaults.colors(
                    containerColor = Mother666.Green.copy(alpha = 0.18f),
                    focusedContainerColor = Mother666.Green,
                    contentColor = Mother666.Green,
                    focusedContentColor = Mother666.Black
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    text = when {
                        state.isDownloading -> stringResource(R.string.update_downloading_ellipsis)
                        state.downloadedApkPath != null -> stringResource(R.string.update_install)
                        state.errorMessage != null -> stringResource(R.string.action_retry)
                        else -> stringResource(R.string.update_download)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!state.isDownloading) {
                UpdateBannerIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.update_close),
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun UpdateBannerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.OnSecondary
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                shape = CircleShape
            )
        ),
        shape = IconButtonDefaults.shape(shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}


/** 24/11 : MOTHER (Alien) pour la bannière de mise à jour. */
internal object Mother666 {
    val Green = androidx.compose.ui.graphics.Color(0xFF39FF7A)
    val Black = androidx.compose.ui.graphics.Color(0xFF030A05)

    /** Règle pure (testée) : la ligne tapée par MOTHER, en capitales. */
    fun line(updateLabel: String): String =
        ("MISE À JOUR " + updateLabel.trim().removePrefix("v").removePrefix("V") + " DISPONIBLE").uppercase()

    /** Règle pure (testée) : combien de lettres sont tapées après t ms (35 ms par lettre). */
    fun typedCount(textLength: Int, tMs: Long): Int = (tMs / 35L).toInt().coerceIn(0, textLength)

    @Composable
    fun rememberTyped(text: String): String {
        var t by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableLongStateOf(0L) }
        androidx.compose.runtime.LaunchedEffect(text) {
            val start = System.currentTimeMillis()
            while (t < text.length * 35L) {
                kotlinx.coroutines.delay(35)
                t = System.currentTimeMillis() - start
            }
        }
        return text.take(typedCount(text.length, t))
    }

    @Composable
    fun rememberBlink(): Boolean {
        var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(530); on = !on } }
        return on
    }
}
