package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 666TV : passer tout seul au lien suivant.
 *
 * Quand la lecture échoue pour de bon (les nouvelles tentatives du lecteur sont épuisées
 * et le message d'erreur va s'afficher), on lance à la place le flux prêt suivant de la
 * liste, dans l'ordre de 666TV (StreamFusion, Frenchio, Lumio, Wastream, Comet).
 * Idée reprise de Nuvio Arc (ysosrs123/NuvioTV-Fork, GPL-3.0), réécrite pour 666TV.
 *
 * - Jamais deux fois le même lien pour un même film / épisode ; 6 essais au maximum.
 * - 666TVF : un lien refusé par le serveur (HTTP 401/403/404/5xx) → les autres liens
 *   de la même source sont sautés (ils seraient refusés aussi), on passe à la suivante.
 * - Seulement des liens prêts à lire (http/https), jamais de torrent pair-à-pair.
 * - La lecture reprend au même endroit (progression enregistrée avant le changement).
 * - Rien trouvé : le message d'erreur habituel s'affiche.
 */
internal object SourceFailoverRules {
    const val MAX_FAILOVERS = 6
    const val LIST_WAIT_MS = 25_000L
    const val LIST_POLL_MS = 300L

    fun failoverKey(stream: Stream): String? =
        stream.getStreamUrl()?.takeIf { it.isNotBlank() }
            ?: (stream.infoHash ?: stream.clientResolve?.infoHash)?.lowercase()
                ?.let { "$it:${stream.fileIdx ?: stream.clientResolve?.fileIdx ?: -1}" }

    fun isPlayable(stream: Stream): Boolean {
        if (stream.isExternal()) return false
        when (stream.debridCacheStatus?.state) {
            null, StreamDebridCacheState.CACHED -> Unit
            else -> return false
        }
        // Seulement des liens déjà prêts à lire (http/https) : un lien debrid encore à
        // préparer pourrait laisser le lecteur bloqué en chargement s'il échoue.
        val url = stream.getStreamUrl()?.trim() ?: return false
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    /** Premier flux jouable de la liste (déjà dans l'ordre 666TV) qui n'a pas encore été essayé. */
    fun selectNext(streams: List<Stream>, tried: Set<String>): Stream? =
        streams.firstOrNull { stream ->
            val key = failoverKey(stream) ?: return@firstOrNull false
            key !in tried && isPlayable(stream) && sourceKey(stream) !in tried
        }

    /** Clé « toute la source » (ex. Frenchio666), utilisée quand ses liens sont refusés. */
    fun sourceKey(stream: Stream): String = "source:" + displayName(stream).lowercase()

    fun sourceKey(name: String?): String? = name?.takeIf { it.isNotBlank() }?.let { "source:" + it.lowercase() }

    /** Erreur venant du serveur du lien (refus, introuvable, panne) plutôt que du fichier. */
    fun isServerRefusal(error: String): Boolean =
        Regex("HTTP\\s*(40[134]|5\\d\\d)|BAD_HTTP_STATUS", RegexOption.IGNORE_CASE).containsMatchIn(error)

    /** Nom affiché : la source (StreamFusion666…) plutôt que le titre formaté (🗣️ 🇫🇷). */
    fun displayName(stream: Stream): String =
        stream.addonName.takeIf { it.isNotBlank() } ?: stream.name.orEmpty()
}

private fun PlayerRuntimeController.currentSourceRequestKey(): String? {
    return if (contentType in listOf("series", "tv") && currentSeason != null && currentEpisode != null) {
        val type = contentType ?: return null
        val vid = currentVideoId ?: contentId ?: return null
        buildSourceRequestKey(type, vid, currentSeason, currentEpisode)
    } else {
        val vid = contentId ?: return null
        buildSourceRequestKey(contentType ?: "movie", vid, null, null)
    }
}

/** 666TV : message quand le fichier n'a aucune piste française (déclenche le lien suivant). */
internal const val NO_FRENCH_AUDIO_666 = "Aucune piste audio française dans ce fichier"

private fun TrackInfo.isFrench666(): Boolean {
    val lang = language?.lowercase()?.trim().orEmpty()
    if (lang.startsWith("fr")) return true
    val n = name.lowercase()
    return Regex("fran[cç]ais|french|\\bvf[fqi2]?\\b|truefrench").containsMatchIn(n)
}

private fun TrackInfo.knownOtherLanguage666(): Boolean {
    val lang = language?.lowercase()?.trim().orEmpty()
    return lang.isNotEmpty() && lang !in setOf("und", "unknown", "mul", "zxx", "mis") && !isFrench666()
}

/** 666TV : le lien n'a montré aucune image au bout de 35 s (chargement sans fin). */
internal const val STARTUP_TIMEOUT_666 = "Le lien ne démarre pas"
private const val STARTUP_TIMEOUT_MS_666 = 25_000L

/** À appeler une fois à la création du lecteur. */
internal fun PlayerRuntimeController.observeSourceFailover() {
    scope.launch {
        _uiState.map { it.error }.distinctUntilChanged().collect { error ->
            if (error != null) maybeFailoverToNextSource(error)
        }
    }
    // 666TV : un lien qui charge sans jamais démarrer (titre qui pulse sans fin)
    // passe au lien suivant au bout de 25 s, au lieu de rester bloqué.
    scope.launch {
        var watchdog: kotlinx.coroutines.Job? = null
        _uiState.map { it.currentStreamUrl }.distinctUntilChanged().collect { url ->
            watchdog?.cancel()
            if (url.isNullOrBlank()) return@collect
            watchdog = scope.launch {
                kotlinx.coroutines.delay(STARTUP_TIMEOUT_MS_666)
                val state = _uiState.value
                if (state.currentStreamUrl == url && !hasRenderedFirstFrame && state.error == null && !userPausedManually) {
                    Log.w(PlayerRuntimeController.TAG, "No first frame after ${STARTUP_TIMEOUT_MS_666}ms → next link")
                    _uiState.update { it.copy(error = STARTUP_TIMEOUT_666) }
                }
            }
        }
    }
    // 666TV : « VF » annoncée mais aucune piste française dans le fichier → lien suivant.
    scope.launch {
        var checkedFor: String? = null
        _uiState.map { it.currentStreamUrl to it.audioTracks }.distinctUntilChanged().collect { (url, tracks) ->
            if (url.isNullOrBlank() || tracks.isEmpty() || url == checkedFor) return@collect
            if (contentType.equals("cloud", ignoreCase = true)) return@collect
            checkedFor = url
            // 666TVF : on dit à Frank ce que contient vraiment ce fichier (VF ou pas) :
            // il apprend ainsi quels groupes de release « MULTi » ont la VF.
            val hasFrench666 = tracks.any { it.isFrench666() }
            val surelyNoFrench666 = !hasFrench666 && tracks.all { it.knownOtherLanguage666() }
            if (hasFrench666 || surelyNoFrench666) {
                val release = (currentStreamDescription ?: _uiState.value.currentStreamName).orEmpty().lineSequence().firstOrNull().orEmpty()
                if (release.isNotBlank()) reportReleaseVerdict666(release, hasFrench666)
            }
            if (tracks.none { it.isFrench666() } && tracks.all { it.knownOtherLanguage666() }) {
                Log.i(PlayerRuntimeController.TAG, "No French audio track (${tracks.joinToString { it.language.orEmpty() }}) → next link")
                _uiState.update { it.copy(error = NO_FRENCH_AUDIO_666) }
            }
        }
    }
}

private fun PlayerRuntimeController.maybeFailoverToNextSource(error: String) {
    if (contentType.equals("cloud", ignoreCase = true)) return
    if (sourceFailoverJob?.isActive == true) return
    val requestKey = currentSourceRequestKey() ?: return
    if (requestKey != sourceFailoverRequestKey) {
        sourceFailoverRequestKey = requestKey
        sourceFailoverTriedKeys.clear()
        sourceFailoverCount = 0
        sourceFailoverExhausted = false
    }
    if (sourceFailoverExhausted || sourceFailoverCount >= SourceFailoverRules.MAX_FAILOVERS) return

    // Le lien qui vient d'échouer ne sera plus proposé.
    val state = _uiState.value
    state.currentStreamUrl?.takeIf { it.isNotBlank() }?.let { sourceFailoverTriedKeys += it }
    state.currentStreamInfoHash?.lowercase()?.let {
        sourceFailoverTriedKeys += "$it:${state.currentStreamFileIdx ?: -1}"
    }
    // 666TVF : lien refusé par le serveur → toute cette source est sautée pour ce film / épisode.
    if (SourceFailoverRules.isServerRefusal(error)) {
        SourceFailoverRules.sourceKey(state.currentStreamAddonName ?: state.currentStreamName)?.let {
            Log.i(PlayerRuntimeController.TAG, "Source refused ($error) → skipping $it")
            sourceFailoverTriedKeys += it
        }
    }

    sourceFailoverJob = scope.launch {
        // On cache l'erreur pendant qu'on cherche le lien suivant.
        _uiState.update {
            it.copy(
                error = null,
                isBuffering = true,
                showLoadingOverlay = it.loadingOverlayEnabled,
                showStreamSourceIndicator = true,
                streamSourceIndicatorText = if (error == NO_FRENCH_AUDIO_666) "Pas de VF dans ce fichier : recherche du suivant…"
                    else "Lien indisponible : recherche du suivant…"
            )
        }

        if (sourceStreamsCacheRequestKey != requestKey || _uiState.value.sourceAllStreams.isEmpty()) {
            loadSourceStreams(forceRefresh = false)
        }
        var next: Stream? = null
        var waited = 0L
        while (waited <= SourceFailoverRules.LIST_WAIT_MS) {
            next = SourceFailoverRules.selectNext(_uiState.value.sourceAllStreams, sourceFailoverTriedKeys)
            if (next != null) break
            if (sourceStreamsFetchCompleted && !_uiState.value.isLoadingSourceStreams &&
                sourceStreamsCacheRequestKey == requestKey
            ) break
            delay(SourceFailoverRules.LIST_POLL_MS)
            waited += SourceFailoverRules.LIST_POLL_MS
        }

        if (next == null) {
            Log.w(PlayerRuntimeController.TAG, "Source failover: no other ready link")
            sourceFailoverExhausted = true
            _uiState.update {
                it.copy(
                    error = error,
                    isBuffering = false,
                    showLoadingOverlay = false,
                    showStreamSourceIndicator = false
                )
            }
            return@launch
        }

        sourceFailoverCount++
        SourceFailoverRules.failoverKey(next)?.let { sourceFailoverTriedKeys += it }
        val name = SourceFailoverRules.displayName(next)
        Log.i(PlayerRuntimeController.TAG, "Source failover ${sourceFailoverCount}/${SourceFailoverRules.MAX_FAILOVERS} → $name")
        switchToSourceStream(next)

        hideStreamSourceIndicatorJob?.cancel()
        _uiState.update {
            it.copy(
                showStreamSourceIndicator = true,
                streamSourceIndicatorText = if (error == NO_FRENCH_AUDIO_666) "Pas de VF → $name"
                    else if (SourceFailoverRules.isServerRefusal(error)) "Lien refusé par la source → $name"
                    else "Lien indisponible → $name"
            )
        }
        hideStreamSourceIndicatorJob = scope.launch {
            delay(4_000)
            _uiState.update { it.copy(showStreamSourceIndicator = false) }
        }
    }
}


/** 666TVF : verdict de lecture envoyé à Frank (nom de release + VF oui/non, rien d'autre). */
private fun PlayerRuntimeController.reportReleaseVerdict666(release: String, hasFrench: Boolean) {
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val body = org.json.JSONObject().put("release", release.take(300)).put("fr", hasFrench).toString()
            val conn = (java.net.URL("https://lambda666-frankenstream.hf.space/groupes/verdict").openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode
            conn.disconnect()
        }
    }
}
