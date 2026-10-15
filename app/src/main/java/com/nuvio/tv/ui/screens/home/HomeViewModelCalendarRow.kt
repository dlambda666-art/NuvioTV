package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.calendar.CalendarEpisode
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 666TV : rangée « À venir » sur l'accueil — une affiche par série, avec le prochain
 * épisode et sa date. Rafraîchie toutes les 6 heures.
 */
internal fun HomeViewModel.observeCalendarUpcomingRow() {
    viewModelScope.launch {
        runCatching { profileManager.activeProfileReady.first { it } }
        delay(4_000L)
        while (true) {
            val episodes = runCatching { calendarRepository.episodes(daysBack = 0, daysAhead = 14) }
                .getOrDefault(emptyList())
            val row = buildCalendarUpcomingRow(episodes)
            if (row != calendarUpcomingRow) {
                calendarUpcomingRow = row
                scheduleUpdateCatalogRows()
            }
            delay(6L * 60L * 60L * 1000L)
        }
    }
}

internal const val CALENDAR_ROW_ADDON_ID = "666tv.calendar"

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

internal fun buildCalendarUpcomingRow(episodes: List<CalendarEpisode>): CatalogRow? {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val firstPerSeries = episodes
        .filter { !it.hasAired() }
        .groupBy { it.seriesId }
        .mapNotNull { (_, eps) -> eps.minByOrNull { it.airInstant } }
        .sortedBy { it.airInstant }
        .take(8)
    if (firstPerSeries.isEmpty()) return null
    val items = firstPerSeries.map { ep ->
        val day = ep.airDate(zone)
        val dayText = when (day) {
            today -> "Aujourd'hui"
            today.plusDays(1) -> "Demain"
            else -> day.format(DAY_FORMAT).replaceFirstChar { it.uppercase() }
        }
        MetaPreview(
            id = ep.seriesId,
            type = ContentType.SERIES,
            name = ep.seriesName,
            poster = ep.poster,
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = "${ep.code}${ep.title?.let { " · $it" } ?: ""}",
            releaseInfo = "$dayText · ${ep.code}",
            imdbRating = null,
            genres = emptyList()
        )
    }
    return CatalogRow(
        addonId = CALENDAR_ROW_ADDON_ID,
        addonName = "666TV",
        addonBaseUrl = "",
        catalogId = "a-venir",
        catalogName = "📅 À venir",
        type = ContentType.SERIES,
        items = items,
        hasMore = false
    )
}
