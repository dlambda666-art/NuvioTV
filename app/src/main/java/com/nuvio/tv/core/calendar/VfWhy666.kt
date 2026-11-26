package com.nuvio.tv.core.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 26/11 (David : « pas de NXT ni de Raw, ça commence à m'inquiéter ») : pourquoi le cri « VF dispo » n'est pas
 * venu, épisode par épisode, cavalier par cavalier. Gardé sur la Shield et montré dans le Rapport pour Claude.
 * Seulement des noms de sorties et des raisons : jamais de lien, de clé ni de hash.
 */
object VfWhy666 {
    private const val PREFS = "666tv_vfwhy"
    private const val KEEP = 12

    data class Entry(val at: Long, val episode: String, val verdict: String, val lines: List<String>)

    /** Règle pure (testée) : une ligne courte par cavalier. */
    fun scraperLine(name: String, total: Int, rejected: Map<String, Int>, outcome: String, example: String?): String {
        val rej = rejected.entries.filter { it.value > 0 }.sortedByDescending { it.value }
            .joinToString(", ") { "${it.value} ${it.key}" }
        val ex = example?.takeIf { it.isNotBlank() }?.let { " · ex. « ${it.take(90)} »" }.orEmpty()
        return "$name : $total trouvé(s)" + (if (rej.isNotEmpty()) " · écartés : $rej" else "") + " → $outcome$ex"
    }

    fun record(context: Context, episode: String, verdict: String, lines: List<String>, now: Long = System.currentTimeMillis()) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString("log", "[]") ?: "[]")
            val kept = JSONArray()
            // une seule entrée par épisode (la dernière vérification)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("ep") != episode) kept.put(o)
            }
            kept.put(JSONObject().put("at", now).put("ep", episode).put("v", verdict).put("l", JSONArray(lines)))
            val trimmed = JSONArray()
            val from = (kept.length() - KEEP).coerceAtLeast(0)
            for (i in from until kept.length()) trimmed.put(kept.getJSONObject(i))
            prefs.edit().putString("log", trimmed.toString()).apply()
        }
    }

    fun entries(context: Context): List<Entry> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val l = o.optJSONArray("l") ?: JSONArray()
            Entry(o.optLong("at"), o.optString("ep"), o.optString("v"), (0 until l.length()).map { l.getString(it) })
        }.sortedByDescending { it.at }
    }.getOrDefault(emptyList())
}
