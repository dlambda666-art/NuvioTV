package com.nuvio.tv.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.core.calendar.CalendarEpisode
import com.nuvio.tv.core.calendar.VfState
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 666TV : page « Calendrier » (sorties de tes séries, avec la VF). */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    onOpenEpisode: (seriesId: String, addonBaseUrl: String?, season: Int, episode: Int) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val bells by viewModel.bells.collectAsState()
    val firstFocus = remember { FocusRequester() }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) runCatching { firstFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calendrier",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NuvioTheme.colors.TextPrimary
                    )
                    Text(
                        text = "Tes séries (bibliothèque et en cours) : 7 derniers jours et 30 prochains. " +
                            "🔔 = être prévenu quand un nouvel épisode est dispo en VF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
                SmallButton(
                    text = if (state.isLoading) "Chargement…" else "↻ Actualiser",
                    onClick = viewModel::refresh,
                    modifier = if (state.recent.isEmpty() && state.upcoming.isEmpty()) {
                        Modifier.focusRequester(firstFocus)
                    } else Modifier
                )
            }
        }

        // 666TV : prochains UFC numérotés.
        if (state.ufc.isNotEmpty()) {
            item(key = "ufc_title") { SectionTitle("🥊 Prochains UFC numérotés") }
            items(state.ufc, key = { "ufc_" + it.name }) { ev ->
                val local = ev.start.atZone(zone)
                Text(
                    text = "${ev.name}  —  ${UFC_DAY_FORMAT.format(local).replaceFirstChar { it.uppercase() }}" +
                        if (local.hour == 0 && local.minute == 0) "" else ", ${TIME_FORMAT.format(local)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.colors.TextPrimary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        if (!state.isLoading && state.recent.isEmpty() && state.upcoming.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = if (state.followedCount == 0) {
                        "Aucune série suivie trouvée. Ajoute des séries à ta bibliothèque ou commence-en une : " +
                            "leurs prochains épisodes apparaîtront ici."
                    } else {
                        "${state.followedCount} série(s) suivie(s), mais aucun épisode sorti ces 7 derniers jours " +
                            "ni annoncé pour les 30 prochains."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.colors.TextSecondary,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }

        if (state.recent.isNotEmpty()) {
            item(key = "recent_title") { SectionTitle("Sortis récemment") }
            items(state.recent, key = { "r_" + it.key }) { ep ->
                val isFirst = ep == state.recent.first()
                EpisodeRow(
                    episode = ep,
                    dateText = dayLabel(ep, today, zone) + timeSuffix(ep, zone),
                    vf = state.vf[ep.key] ?: VfState.CHECKING,
                    bellOn = ep.seriesId in bells,
                    onOpen = { onOpenEpisode(ep.seriesId, ep.addonBaseUrl, ep.season, ep.episode) },
                    onBell = { viewModel.toggleBell(ep.seriesId) },
                    modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
                )
            }
        }

        if (state.upcoming.isNotEmpty()) {
            item(key = "upcoming_title") { SectionTitle("À venir") }
            val byDay = state.upcoming.groupBy { it.airDate(zone) }
            byDay.forEach { (day, episodes) ->
                item(key = "day_$day") {
                    Text(
                        text = dayLabel(day, today),
                        style = MaterialTheme.typography.titleSmall,
                        color = NuvioTheme.colors.Secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(episodes, key = { "u_" + it.key }) { ep ->
                    val isFirst = state.recent.isEmpty() && ep == state.upcoming.first()
                    EpisodeRow(
                        episode = ep,
                        dateText = dayLabel(day, today) + timeSuffix(ep, zone),
                        vf = null,
                        bellOn = ep.seriesId in bells,
                        onOpen = { onOpenEpisode(ep.seriesId, ep.addonBaseUrl, ep.season, ep.episode) },
                        onBell = { viewModel.toggleBell(ep.seriesId) },
                        modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = NuvioTheme.colors.TextPrimary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun EpisodeRow(
    episode: CalendarEpisode,
    dateText: String,
    vf: VfState?,
    bellOn: Boolean,
    onOpen: () -> Unit,
    onBell: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(NuvioTheme.radii.md)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        Surface(
            onClick = onOpen,
            modifier = modifier.weight(1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = shape
                )
            ),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = episode.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 54.dp, height = 80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.seriesName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTheme.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(episode.code, episode.title).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary
                    )
                }
                if (vf != null) VfChip(vf)
            }
        }
        SmallButton(text = if (bellOn) "🔔" else "🔕", onClick = onBell)
    }
}

@Composable
private fun VfChip(state: VfState) {
    val (text, color) = when (state) {
        VfState.AVAILABLE -> "🇫🇷 VF dispo" to Color(0xFF2E7D32)
        VfState.NOT_YET -> "VF pas encore" to Color(0xFF6D4C41)
        VfState.CHECKING -> "VF ? …" to Color(0xFF424242)
        VfState.NO_CHECKER -> "Plugins 666 désactivés" to Color(0xFF424242)
        VfState.UNKNOWN -> return
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = com.nuvio.tv.ui.components.Font666.Green)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.BackgroundElevated
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border), shape = shape),
            focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
        ),
        shape = ClickableSurfaceDefaults.shape(shape)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val UFC_DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Aujourd'hui"
    today.plusDays(1) -> "Demain"
    today.minusDays(1) -> "Hier"
    else -> day.format(DAY_FORMAT).replaceFirstChar { it.uppercase() }
}

private fun dayLabel(ep: CalendarEpisode, today: LocalDate, zone: ZoneId): String =
    dayLabel(ep.airDate(zone), today)

/** Heure seulement si la fiche donne une vraie heure (pas juste une date). */
private fun timeSuffix(ep: CalendarEpisode, zone: ZoneId): String {
    val utc = ep.airInstant.atOffset(ZoneOffset.UTC)
    if (utc.hour == 0 && utc.minute == 0) return ""
    return " · " + ep.airInstant.atZone(zone).format(TIME_FORMAT)
}

/** 666TV : bandeau « nouvel épisode en VF » (séries avec la cloche), quelques secondes. */
@Composable
fun CalendarVfBanner(episodes: List<CalendarEpisode>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NuvioTheme.radii.md)
    Column(
        modifier = modifier
            .width(420.dp)
            .clip(shape)
            .background(NuvioTheme.colors.BackgroundElevated.copy(alpha = 0.96f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (episodes.all { it.seriesId.startsWith("alerte:") }) "⚠️ Plugin en panne"
            else if (episodes.all { it.seriesId.startsWith("sport:") }) "🥊 C'est l'heure du combat" else "🔔 Nouveau en VF",
            style = MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextPrimary
        )
        episodes.take(4).forEach { ep ->
            Text(
                text = if (ep.seriesId.startsWith("alerte:")) "⚠️ ${ep.seriesName} · ${ep.title.orEmpty()}"
                else if (ep.seriesId.startsWith("sport:")) "🥊 ${ep.seriesName} · ${ep.title.orEmpty()}"
                else "🇫🇷 ${ep.seriesName} · ${ep.code}",
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (episodes.size > 4) {
            Text(
                text = "+ ${episodes.size - 4} autre(s) — voir le Calendrier",
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
        } else {
            Text(
                text = "Détails dans le Calendrier",
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
        }
    }
}
