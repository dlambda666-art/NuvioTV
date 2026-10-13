package com.nuvio.tv.ui.screens.plugin

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.transfer.ShieldTransferCrypto
import com.nuvio.tv.core.transfer.ShieldTransferNsd
import com.nuvio.tv.core.transfer.ShieldTransferServer
import com.nuvio.tv.core.transfer.ShieldTransferService
import com.nuvio.tv.core.transfer.fetchShieldBundle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 666TV : écran « Copier mes réglages vers une autre Shield ». */
sealed interface ShieldTransferState {
    data object Idle : ShieldTransferState
    data object Preparing : ShieldTransferState
    data class Sending(val pin: String, val secondsLeft: Int) : ShieldTransferState
    data object Sent : ShieldTransferState
    data class Searching(val peers: List<ShieldTransferNsd.Peer>) : ShieldTransferState
    data class EnterPin(val peer: ShieldTransferNsd.Peer, val wrongPin: Boolean = false) : ShieldTransferState
    data object Importing : ShieldTransferState
    data class Imported(val sources: Int, val settings: Int) : ShieldTransferState
    data class Failed(val message: String) : ShieldTransferState
}

@HiltViewModel
class ShieldTransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: ShieldTransferService
) : ViewModel() {

    private val _state = MutableStateFlow<ShieldTransferState>(ShieldTransferState.Idle)
    val state: StateFlow<ShieldTransferState> = _state.asStateFlow()

    private val nsd = ShieldTransferNsd(context)
    private var server: ShieldTransferServer? = null
    private var job: Job? = null

    fun startSending() {
        stopAll()
        _state.value = ShieldTransferState.Preparing
        job = viewModelScope.launch {
            val pin = ShieldTransferCrypto.newPin()
            val bundle = withContext(Dispatchers.Default) {
                ShieldTransferCrypto.encrypt(service.buildPayload(PRIVATE_SOURCES.map { it.scraperId }), pin)
            }
            val started = runCatching {
                ShieldTransferServer(bundle) {
                    viewModelScope.launch(Dispatchers.Main) { onBundleServed() }
                }.also { it.start() }
            }.getOrNull()
            if (started == null) {
                _state.value = ShieldTransferState.Failed("Impossible d'ouvrir le partage sur cette Shield")
                return@launch
            }
            server = started
            nsd.register("666TV ${Build.MODEL ?: "Shield"}")
            for (left in SEND_WINDOW_SECONDS downTo 1) {
                if (_state.value is ShieldTransferState.Sent) return@launch
                _state.value = ShieldTransferState.Sending(pin, left)
                delay(1_000)
            }
            stopServer()
            _state.value = ShieldTransferState.Failed("Temps écoulé : relance l'envoi si besoin")
        }
    }

    private var closeJob: Job? = null

    private fun onBundleServed() {
        if (_state.value is ShieldTransferState.Sent) return
        _state.value = ShieldTransferState.Sent
        // Reste ouvert 1 minute (nouvel essai si le code a été mal tapé), puis ferme tout.
        closeJob?.cancel()
        closeJob = viewModelScope.launch {
            delay(60_000)
            stopServer()
        }
    }

    fun startReceiving() {
        stopAll()
        _state.value = ShieldTransferState.Searching(emptyList())
        nsd.discover { peer ->
            _state.update { current ->
                if (current is ShieldTransferState.Searching && current.peers.none { it.host == peer.host }) {
                    current.copy(peers = current.peers + peer)
                } else {
                    current
                }
            }
        }
        job = viewModelScope.launch {
            delay(SEARCH_WINDOW_SECONDS * 1_000L)
            nsd.stopDiscovery()
            val current = _state.value
            if (current is ShieldTransferState.Searching && current.peers.isEmpty()) {
                _state.value = ShieldTransferState.Failed(
                    "Aucune Shield trouvée : lance d'abord « Envoyer » sur l'autre Shield (même réseau)"
                )
            }
        }
    }

    fun choosePeer(peer: ShieldTransferNsd.Peer) {
        nsd.stopDiscovery()
        job?.cancel()
        _state.value = ShieldTransferState.EnterPin(peer)
    }

    fun importWithPin(peer: ShieldTransferNsd.Peer, pin: String) {
        val cleanPin = pin.filter { it.isDigit() }
        if (cleanPin.length != 6) {
            _state.value = ShieldTransferState.EnterPin(peer, wrongPin = true)
            return
        }
        _state.value = ShieldTransferState.Importing
        job = viewModelScope.launch {
            val bundle = fetchShieldBundle(peer.host, peer.port)
            if (bundle == null) {
                _state.value = ShieldTransferState.Failed("L'autre Shield ne répond plus : relance l'envoi")
                return@launch
            }
            val payload = withContext(Dispatchers.Default) { ShieldTransferCrypto.decrypt(bundle, cleanPin) }
            if (payload == null) {
                _state.value = ShieldTransferState.EnterPin(peer, wrongPin = true)
                return@launch
            }
            val summary = runCatching { service.applyPayload(payload) }.getOrNull()
            _state.value = if (summary == null) {
                ShieldTransferState.Failed("Réglages illisibles")
            } else {
                ShieldTransferState.Imported(summary.sources, summary.settings)
            }
        }
    }

    fun reset() {
        stopAll()
        _state.value = ShieldTransferState.Idle
    }

    private fun stopServer() {
        nsd.unregister()
        server?.let { runCatching { it.stop() } }
        server = null
    }

    private fun stopAll() {
        closeJob?.cancel()
        closeJob = null
        job?.cancel()
        job = null
        nsd.stopDiscovery()
        stopServer()
    }

    override fun onCleared() {
        stopAll()
        super.onCleared()
    }

    private companion object {
        const val SEND_WINDOW_SECONDS = 300
        const val SEARCH_WINDOW_SECONDS = 15
    }
}
