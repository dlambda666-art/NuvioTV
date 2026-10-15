package com.nuvio.tv.core.tmdb

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 666TV : bio d'un acteur en français, prise sur Wikipédia quand TMDB n'en a pas.
 * Wikidata (identifiant donné par TMDB) → titre de l'article français → introduction en texte.
 * Le bon article est garanti par Wikidata : pas de risque d'homonyme.
 */
internal object FrenchWikipediaBio {
    private const val TAG = "FrenchWikipediaBio"
    private const val USER_AGENT = "666TV/1.0 (Android TV; fiche acteur)"
    private const val MAX_CHARS = 2_500

    /** Introduction de l'article Wikipédia FR, ou null. À appeler hors du thread principal. */
    fun fetch(wikidataId: String): String? = runCatching {
        val entityUrl = "https://www.wikidata.org/wiki/Special:EntityData/$wikidataId.json"
        val entity = JSONObject(get(entityUrl) ?: return null)
        val title = entity.optJSONObject("entities")
            ?.optJSONObject(wikidataId)
            ?.optJSONObject("sitelinks")
            ?.optJSONObject("frwiki")
            ?.optString("title")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val apiUrl = "https://fr.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1" +
            "&explaintext=1&redirects=1&format=json&formatversion=2&titles=" +
            URLEncoder.encode(title, "UTF-8")
        val pages = JSONObject(get(apiUrl) ?: return null)
            .optJSONObject("query")
            ?.optJSONArray("pages")
            ?: return null
        val extract = (0 until pages.length())
            .mapNotNull { pages.optJSONObject(it)?.optString("extract") }
            .firstOrNull { it.isNotBlank() }
            ?: return null
        clean(extract)
    }.onFailure { Log.d(TAG, "Wikipédia FR indisponible pour $wikidataId : ${it.message}") }.getOrNull()

    private fun clean(text: String): String? {
        val cleaned = text
            .replace(Regex("\\s*\\([^()]*(?:Écouter|prononciation|API)[^()]*\\)"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (cleaned.length < 40) return null
        if (cleaned.length <= MAX_CHARS) return cleaned
        val cut = cleaned.lastIndexOf(". ", MAX_CHARS).takeIf { it > MAX_CHARS / 2 } ?: MAX_CHARS
        return cleaned.substring(0, cut + 1).trim()
    }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
