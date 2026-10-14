package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.RemoteButtonBinding
import com.nuvio.tv.data.local.RemoteButtonJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 666TV : exécute l'action choisie pour une touche de la télécommande
 * (Paramètres → Lecture → Boutons de la télécommande).
 */
internal fun PlayerRuntimeController.runRemoteButton(binding: RemoteButtonBinding) {
    val state = _uiState.value
    when (binding.job) {
        RemoteButtonJob.NOTHING -> return
        RemoteButtonJob.PLAY_PAUSE -> onEvent(PlayerEvent.OnPlayPause)
        RemoteButtonJob.SKIP_FORWARD -> {
            onEvent(PlayerEvent.OnSeekBy(binding.skipMs))
            flashRemoteIndicator("⏩ +${RemoteButtonBinding.formatSkip(binding.skipMs)}")
        }
        RemoteButtonJob.SKIP_BACK -> {
            onEvent(PlayerEvent.OnSeekBy(-binding.skipMs))
            flashRemoteIndicator("⏪ −${RemoteButtonBinding.formatSkip(binding.skipMs)}")
        }
        RemoteButtonJob.SUBTITLES_TOGGLE -> {
            val tracks = state.subtitleTracks
            val selected = tracks.firstOrNull { it.isSelected }
                ?: tracks.firstOrNull { it.index == state.selectedSubtitleTrackIndex }
            if (selected != null || state.selectedSubtitleTrackIndex >= 0) {
                onEvent(PlayerEvent.OnDisableSubtitles)
                flashRemoteIndicator("Sous-titres : non")
            } else {
                val pick = tracks.firstOrNull { it.language?.startsWith("fr", ignoreCase = true) == true && !it.isForced }
                    ?: tracks.firstOrNull { it.language?.startsWith("fr", ignoreCase = true) == true }
                    ?: tracks.firstOrNull()
                if (pick != null) {
                    onEvent(PlayerEvent.OnSelectSubtitleTrack(pick.index))
                    flashRemoteIndicator("Sous-titres : ${pick.name}")
                } else {
                    flashRemoteIndicator("Pas de sous-titres dans ce fichier")
                }
            }
        }
        RemoteButtonJob.SUBTITLES_MENU -> onEvent(PlayerEvent.OnShowSubtitleOverlay)
        RemoteButtonJob.NEXT_AUDIO -> {
            val tracks = state.audioTracks
            if (tracks.size < 2) {
                flashRemoteIndicator("Une seule piste audio")
            } else {
                val current = tracks.indexOfFirst { it.isSelected }
                    .takeIf { it >= 0 }
                    ?: tracks.indexOfFirst { it.index == state.selectedAudioTrackIndex }
                val next = tracks[(current + 1).mod(tracks.size)]
                onEvent(PlayerEvent.OnSelectAudioTrack(next.index))
                flashRemoteIndicator("🔊 ${next.name}")
            }
        }
        RemoteButtonJob.AUDIO_MENU -> onEvent(PlayerEvent.OnShowAudioOverlay)
        RemoteButtonJob.SOURCES -> onEvent(PlayerEvent.OnShowSourcesPanel)
        RemoteButtonJob.EPISODES -> onEvent(PlayerEvent.OnShowEpisodesPanel)
        RemoteButtonJob.SKIP_INTRO_OR_NEXT -> when {
            state.activeSkipInterval != null -> onEvent(PlayerEvent.OnSkipIntro)
            state.nextEpisode != null -> onEvent(PlayerEvent.OnPlayNextEpisode)
            else -> flashRemoteIndicator("Rien à passer ici")
        }
        RemoteButtonJob.NEXT_EPISODE -> if (state.nextEpisode != null) {
            onEvent(PlayerEvent.OnPlayNextEpisode)
        } else {
            flashRemoteIndicator("Pas d'épisode suivant")
        }
        RemoteButtonJob.RESTART -> {
            onEvent(PlayerEvent.OnSeekTo(0L))
            flashRemoteIndicator("⏮ Retour au début")
        }
        RemoteButtonJob.SPEED -> onEvent(PlayerEvent.OnShowSpeedDialog)
        RemoteButtonJob.ASPECT -> onEvent(PlayerEvent.OnToggleAspectRatio)
        RemoteButtonJob.STATS -> onEvent(PlayerEvent.OnTogglePlayerStatsHud)
        RemoteButtonJob.STREAM_INFO -> onEvent(PlayerEvent.OnShowStreamInfo)
    }
}

private fun PlayerRuntimeController.flashRemoteIndicator(text: String) {
    hideStreamSourceIndicatorJob?.cancel()
    _uiState.update { it.copy(showStreamSourceIndicator = true, streamSourceIndicatorText = text) }
    hideStreamSourceIndicatorJob = scope.launch {
        delay(1_800)
        _uiState.update { it.copy(showStreamSourceIndicator = false) }
    }
}
