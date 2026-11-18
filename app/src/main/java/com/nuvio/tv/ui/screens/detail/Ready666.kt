package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.core.player.StreamAutoPlaySelector
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FrankenStream (18/11, vu dans Nuvio Arc) : badge « ⚡ Prêt » à côté de Lecture.
 * Frank cherche déjà les liens pendant qu'on lit la fiche ; le badge dit quand un lien en cache
 * (TorBox, AllDebrid ✓, ou enregistrement sur le disque) est là pour CET épisode — jamais pour un
 * autre épisode (règle du piège Raw). Pendant la recherche : un discret « … ».
 */
object Ready666 {
    enum class State { SEARCHING, READY, NONE }

    private val _state = MutableStateFlow<Pair<String, State>?>(null)
    val state: StateFlow<Pair<String, State>?> = _state
    fun set(metaId: String, s: State) { _state.value = metaId to s }

    /** Règle pure (testée). */
    fun decide(searching: Boolean, streams: List<Stream>, season: Int?, episode: Int?): State {
        val right = StreamAutoPlaySelector.playableForEpisode(streams, season, episode)
        val ready = right.any { s ->
            val urls = listOfNotNull(s.url, s.externalUrl)
            urls.none { it.contains("diagnostic.invalid") } &&
                (s.debridCacheStatus?.state == StreamDebridCacheState.CACHED || urls.any { it.startsWith("file:") })
        }
        return when {
            ready -> State.READY
            searching -> State.SEARCHING
            else -> State.NONE
        }
    }
}

private val Mauve666 = Color(0xFFC08BFF)

@Composable
internal fun ReadyBadge666(metaId: String, modifier: Modifier = Modifier) {
    val current by Ready666.state.collectAsState()
    val s = current?.takeIf { it.first == metaId }?.second ?: return
    when (s) {
        Ready666.State.READY -> Text(
            text = "⚡ Prêt",
            color = com.nuvio.tv.ui.components.Font666.Green,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
                .background(Color(0x33000000), RoundedCornerShape(8.dp))
                .border(1.5.dp, com.nuvio.tv.ui.components.Font666.Green, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Ready666.State.SEARCHING -> Text(
            text = "…",
            color = Mauve666,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier.padding(horizontal = 6.dp)
        )
        Ready666.State.NONE -> Unit
    }
}
