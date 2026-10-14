package com.nuvio.tv.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.calendar.CalendarEpisode
import com.nuvio.tv.core.calendar.CalendarRepository
import com.nuvio.tv.core.calendar.VfState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class CalendarUiState(
    val isLoading: Boolean = true,
    val recent: List<CalendarEpisode> = emptyList(),
    val upcoming: List<CalendarEpisode> = emptyList(),
    val vf: Map<String, VfState> = emptyMap()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    val bells: StateFlow<Set<String>> = repository.bells
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var loadJob: Job? = null

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    fun toggleBell(seriesId: String) {
        viewModelScope.launch { repository.toggleBell(seriesId) }
    }

    private fun load(force: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val episodes = runCatching { repository.episodes(force = force) }.getOrDefault(emptyList())
            val now = Instant.now()
            val recent = episodes.filter { !it.airInstant.isAfter(now) }.sortedByDescending { it.airInstant }
            val upcoming = episodes.filter { it.airInstant.isAfter(now) }
            val known = runCatching { repository.knownVf() }.getOrDefault(emptySet())
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    recent = recent,
                    upcoming = upcoming,
                    vf = recent.associate { ep ->
                        ep.key to if (ep.key in known) VfState.AVAILABLE else (state.vf[ep.key] ?: VfState.CHECKING)
                    }
                )
            }
            // Vérification VF des épisodes sortis (du plus récent au plus ancien).
            recent.filter { _state.value.vf[it.key] != VfState.AVAILABLE }.forEach { episode ->
                launch {
                    val result = repository.checkVf(episode)
                    _state.update { it.copy(vf = it.vf + (episode.key to result)) }
                }
            }
        }
    }
}
