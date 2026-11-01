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
    val followedCount: Int = 0,
    val recent: List<CalendarEpisode> = emptyList(),
    val upcoming: List<CalendarEpisode> = emptyList(),
    val vf: Map<String, VfState> = emptyMap(),
    val ufc: List<com.nuvio.tv.core.calendar.UfcEvent> = emptyList()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val ufcRepository: com.nuvio.tv.core.calendar.UfcScheduleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    val bells: StateFlow<Set<String>> = repository.bells
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var loadJob: Job? = null

    private var loadedAt = 0L

    init {
        load(force = false)
        // 666TVF (01/11) : la cloche a trouvé une VF → le calendrier passe tout de suite en « VF dispo »
        // (avant, il gardait « pas encore » tant qu'on n'appuyait pas sur Actualiser : WWE du 01/11).
        viewModelScope.launch {
            repository.knownVfFlow().collect { known ->
                _state.update { s -> s.copy(vf = applyKnownVf666(s.vf, known)) }
            }
        }
    }

    /** L'écran revient au premier plan : on revérifie ce qui n'avait pas encore de VF (pas plus d'une fois par 5 min). */
    fun onShown() {
        if (System.currentTimeMillis() - loadedAt < 5 * 60_000L) return
        repository.clearVfNegativeCache()
        load(force = false)
    }

    fun refresh() {
        repository.clearVfNegativeCache()
        load(force = true)
    }

    fun toggleBell(seriesId: String) {
        viewModelScope.launch { repository.toggleBell(seriesId) }
    }

    private fun load(force: Boolean) {
        loadedAt = System.currentTimeMillis()
        loadJob?.cancel()
        viewModelScope.launch {
            val ufc = runCatching { ufcRepository.upcomingNumbered() }.getOrDefault(emptyList())
            _state.update { it.copy(ufc = ufc) }
        }
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            var episodes = runCatching { repository.episodes(force = force) }.getOrDefault(emptyList())
            if (episodes.isEmpty()) {
                // Au démarrage, bibliothèque / historique peuvent arriver un peu après : 2e essai.
                kotlinx.coroutines.delay(8_000)
                episodes = runCatching { repository.episodes(force = true) }.getOrDefault(emptyList())
            }
            val now = Instant.now()
            val recent = episodes.filter { !it.airInstant.isAfter(now) }.sortedByDescending { it.airInstant }
            val upcoming = episodes.filter { it.airInstant.isAfter(now) }
            val known = runCatching { repository.knownVf() }.getOrDefault(emptySet())
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    followedCount = repository.lastFollowedCount,
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

/** Règle pure (testée) : les épisodes affichés dont la VF est connue passent en « dispo » ; rien d'autre ne bouge. */
internal fun applyKnownVf666(vf: Map<String, VfState>, known: Set<String>): Map<String, VfState> =
    vf.mapValues { (key, state) -> if (key in known) VfState.AVAILABLE else state }
