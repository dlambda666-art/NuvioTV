package com.nuvio.tv.core.tools

import android.util.Log
import com.nuvio.tv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 666TV : « Pour toi, Lambda ». Envoie à Frank tes derniers visionnages (films et
 * séries regardés pour de vrai) et la liste de ce que tu as déjà vu. Frank en tire
 * les lignes de recommandations VF. Au démarrage, puis toutes les 3 heures.
 * Rien d'autre n'est envoyé (pas de nom de profil, pas de position de lecture).
 */
object PourToi666 {
    private const val TAG = "PourToi666"
    private const val FRANK = "https://lambda666-frankenstream.hf.space/pour-toi/sync"
    private const val EVERY_MS = 3 * 60 * 60 * 1000L

    suspend fun loop(repo: WatchProgressRepository) {
        delay(20_000L) // laisse l'appli démarrer tranquillement
        while (true) {
            runCatching { syncOnce(repo) }.onFailure { Log.w(TAG, "sync: ${it.message}") }
            delay(EVERY_MS)
        }
    }

    private suspend fun syncOnce(repo: WatchProgressRepository) {
        val progress = withTimeoutOrNull(15_000L) { repo.allProgress.first() } ?: emptyList()
        val watchedItems = withTimeoutOrNull(15_000L) { repo.watchedItems.first() } ?: emptyList()

        val seeds = LinkedHashMap<String, Triple<String, String, Long>>() // id -> (id, type, at)
        fun add(id: String, rawType: String, at: Long) {
            if (!id.startsWith("tt")) return
            val type = if (rawType == "series" || rawType == "tv") "series" else "movie"
            val prev = seeds[id]
            if (prev == null || at > prev.third) seeds[id] = Triple(id, type, at)
        }
        progress.forEach { p ->
            val percent = p.progressPercent
                ?: if (p.duration > 0) p.position * 100f / p.duration else 0f
            // Regardé pour de vrai : au moins 20 % (ou un épisode d'une série).
            if (percent >= 20f || p.season != null) add(p.contentId.substringBefore(':'), p.contentType, p.lastWatched)
        }
        watchedItems.forEach { w -> add(w.contentId.substringBefore(':'), w.contentType, w.watchedAt) }

        val watched = (progress.map { it.contentId.substringBefore(':') } +
            watchedItems.map { it.contentId.substringBefore(':') })
            .filter { it.startsWith("tt") }.distinct().take(2000)
        if (seeds.isEmpty()) return

        val body = JSONObject()
            .put("seeds", JSONArray().apply {
                seeds.values.sortedByDescending { it.third }.take(60).forEach {
                    put(JSONObject().put("id", it.first).put("type", it.second).put("at", it.third))
                }
            })
            .put("watched", JSONArray(watched))
            .toString()

        withContext(Dispatchers.IO) {
            val c = URL(FRANK).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                Log.i(TAG, "sync -> ${c.responseCode}")
            } finally {
                c.disconnect()
            }
        }
    }
}
