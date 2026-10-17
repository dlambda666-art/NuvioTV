package com.nuvio.tv.core.calendar

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Un gala UFC numéroté (UFC 331…), avec sa date. */
data class UfcEvent(val name: String, val start: Instant)

/**
 * 666TV : les prochains UFC numérotés pour le calendrier.
 * Source : le calendrier public UFC d'ESPN (pas de clé). On ne garde que les galas
 * numérotés (« UFC 3xx »), pas les Fight Night.
 */
@Singleton
class UfcScheduleRepository @Inject constructor() {

    @Volatile private var cache: Pair<Long, List<UfcEvent>>? = null

    suspend fun upcomingNumbered(limit: Int = 3): List<UfcEvent> = withContext(Dispatchers.IO) {
        cache?.let { (at, events) ->
            if (System.currentTimeMillis() - at < CACHE_MS) return@withContext events.upcoming(limit)
        }
        val year = Year.now(ZoneOffset.UTC).value
        val all = LinkedHashMap<String, UfcEvent>()
        for (y in listOf(year, year + 1)) {
            fetchYear(y).forEach { all.putIfAbsent(it.name.lowercase(), it) }
            if (all.values.upcoming(limit).size >= limit) break
        }
        val events = all.values.sortedBy { it.start }
        if (events.isNotEmpty()) cache = System.currentTimeMillis() to events
        events.upcoming(limit)
    }

    private fun Collection<UfcEvent>.upcoming(limit: Int): List<UfcEvent> {
        // Encore affiché le jour même et le lendemain (la carte principale finit la nuit).
        val from = Instant.now().minusSeconds(30L * 3600)
        return filter { !it.start.isBefore(from) }.sortedBy { it.start }.take(limit)
    }

    private fun fetchYear(year: Int): List<UfcEvent> = runCatching {
        val text = httpGet("$ESPN_SCOREBOARD?dates=$year") ?: return@runCatching emptyList()
        val root = JSONObject(text)
        val found = mutableListOf<UfcEvent>()
        // 1. Événements détaillés (heure plus précise quand ESPN la donne)
        root.optJSONArray("events")?.let { events ->
            for (i in 0 until events.length()) {
                val ev = events.optJSONObject(i) ?: continue
                val name = ev.optString("name").ifBlank { ev.optString("shortName") }.trim()
                val start = parseDate(ev.optString("date")) ?: continue
                if (NUMBERED.containsMatchIn(name)) found += UfcEvent(cleanName(name), start)
            }
        }
        // 2. Calendrier de la saison : [{label, startDate}, …]
        root.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("calendar")?.let { cal ->
            for (i in 0 until cal.length()) {
                val entry = cal.optJSONObject(i) ?: continue
                val name = entry.optString("label").trim()
                val start = parseDate(entry.optString("startDate")) ?: continue
                if (NUMBERED.containsMatchIn(name)) found += UfcEvent(cleanName(name), start)
            }
        }
        found
    }.onFailure { Log.w(TAG, "UFC schedule failed for $year: ${it.message}") }.getOrDefault(emptyList())

    private fun cleanName(name: String): String = name.replace(Regex("\\s+"), " ")

    private fun parseDate(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(raw, ESPN_DATE).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "666TV")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "Ufc666"
        const val ESPN_SCOREBOARD = "https://site.api.espn.com/apis/site/v2/sports/mma/ufc/scoreboard"
        const val CACHE_MS = 6L * 60 * 60 * 1000
        val NUMBERED = Regex("^UFC\\s*\\d{3}\\b", RegexOption.IGNORE_CASE)
        // ESPN écrit souvent « 2026-11-01T03:00Z » (sans secondes).
        val ESPN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX")
    }
}

