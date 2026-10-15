package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * 666TV : « Précédemment » — rappel de la saison passée, épisode par épisode,
 * avec les résumés de la fiche (en français quand ils existent).
 */
@Composable
internal fun SeasonRecapDialog(
    seriesName: String,
    season: Int,
    episodes: List<Video>,
    onDismiss: () -> Unit
) {
    val withText = episodes
        .filter { it.season == season && (it.episode ?: 0) > 0 }
        .sortedBy { it.episode }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    NuvioDialog(
        onDismiss = onDismiss,
        title = "Précédemment dans $seriesName",
        subtitle = "Rappel de la saison $season · Retour pour fermer",
        width = 760.dp
    ) {
        if (withText.none { !it.overview.isNullOrBlank() }) {
            Text(
                text = "Pas de résumé disponible pour les épisodes de cette saison.",
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary,
                modifier = Modifier.focusRequester(firstFocus).focusable()
            )
            return@NuvioDialog
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            items(withText, key = { "${it.season}:${it.episode}" }) { video ->
                val isFirst = video == withText.first()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .then(if (isFirst) Modifier.focusRequester(firstFocus) else Modifier)
                        .focusable()
                ) {
                    val title = video.title.takeIf { it.isNotBlank() && !it.equals("Episode ${video.episode}", true) }
                    Text(
                        text = "Épisode ${video.episode}" + (title?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTheme.colors.Secondary
                    )
                    Text(
                        text = video.overview?.takeIf { it.isNotBlank() } ?: "Pas de résumé pour cet épisode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
