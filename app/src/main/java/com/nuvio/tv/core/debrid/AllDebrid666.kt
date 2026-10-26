package com.nuvio.tv.core.debrid

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AllDebrid666"
private const val API = "https://api.alldebrid.com"
private const val AGENT = "666TVF"

/**
 * 666TVF : AllDebrid en roue de secours.
 * Quand TorBox ne répond pas (panne, maintenance) ou n'a pas le fichier en cache,
 * le torrent choisi est relancé sur AllDebrid. La clé reste sur cet appareil
 * (préférences locales), elle n'est jamais affichée ni envoyée ailleurs qu'à AllDebrid.
 */
@Singleton
class AllDebrid666 @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences("666tv_alldebrid", Context.MODE_PRIVATE) }
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        _apiKey.value = runCatching { prefs.getString("apikey", "").orEmpty() }.getOrDefault("")
    }

    val isConfigured: Boolean get() = _apiKey.value.isNotBlank()

    fun saveKey(value: String) {
        val key = value.trim()
        prefs.edit().putString("apikey", key).apply()
        _apiKey.value = key
    }

    /** Vrai si la clé est acceptée par AllDebrid. */
    suspend fun validate(key: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { get("/v4/user", key, emptyMap())?.optString("status") == "success" }.getOrDefault(false)
    }

    /** Lien lisible pour ce torrent, ou NotCached / Stale / Error. */
    suspend fun resolve(
        magnet: String,
        fileIdx: Int?,
        season: Int?,
        episode: Int?
    ): DirectDebridResolveResult = withContext(Dispatchers.IO) {
        val key = _apiKey.value.takeIf { it.isNotBlank() } ?: return@withContext DirectDebridResolveResult.MissingApiKey
        try {
            val upload = post("/v4/magnet/upload", key, mapOf("magnets[]" to magnet))
                ?: return@withContext DirectDebridResolveResult.Error
            if (upload.optString("status") != "success") {
                Log.w(TAG, "upload refusé : ${upload.optJSONObject("error")?.optString("code")}")
                return@withContext DirectDebridResolveResult.Error
            }
            val m = upload.optJSONObject("data")?.optJSONArray("magnets")?.optJSONObject(0)
                ?: return@withContext DirectDebridResolveResult.Error
            if (m.has("error")) return@withContext DirectDebridResolveResult.NotCached
            val id = m.optInt("id", -1).takeIf { it > 0 } ?: return@withContext DirectDebridResolveResult.Error
            if (!m.optBoolean("ready", false)) {
                // Pas en cache chez AllDebrid : on ne lance pas de téléchargement, on nettoie.
                runCatching { get("/v4/magnet/delete", key, mapOf("id" to id.toString())) }
                return@withContext DirectDebridResolveResult.NotCached
            }
            val filesJson = get("/v4/magnet/files", key, mapOf("id[]" to id.toString()))
            val tree = filesJson?.optJSONObject("data")?.optJSONArray("magnets")?.optJSONObject(0)?.optJSONArray("files")
                ?: return@withContext DirectDebridResolveResult.Stale
            val files = flatten(tree)
            val file = selectFile(files, fileIdx, season, episode) ?: return@withContext DirectDebridResolveResult.Stale
            val unlock = get("/v4/link/unlock", key, mapOf("link" to file.link))
            val data = unlock?.takeIf { it.optString("status") == "success" }?.optJSONObject("data")
                ?: return@withContext DirectDebridResolveResult.Stale
            val url = data.optString("link").takeIf { it.startsWith("http") }
                ?: return@withContext DirectDebridResolveResult.Stale
            DirectDebridResolveResult.Success(
                url = url,
                filename = data.optString("filename").ifBlank { file.name },
                videoSize = data.optLong("filesize", file.size).takeIf { it > 0 }
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "échec : ${error::class.simpleName}")
            DirectDebridResolveResult.Error
        }
    }

    internal data class AdFile(val name: String, val size: Long, val link: String)

    private fun flatten(array: JSONArray, out: MutableList<AdFile> = mutableListOf()): List<AdFile> {
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val children = node.optJSONArray("e")
            if (children != null) flatten(children, out)
            else {
                val link = node.optString("l")
                if (link.isNotBlank()) out += AdFile(node.optString("n"), node.optLong("s", 0L), link)
            }
        }
        return out
    }

    private fun selectFile(files: List<AdFile>, fileIdx: Int?, season: Int?, episode: Int?): AdFile? {
        val playable = files.filter { it.name.lowercase().hasDebridVideoExtension() && !it.name.contains("sample", ignoreCase = true) }
        if (playable.isEmpty()) return null
        val patterns = buildDebridEpisodePatterns(season, episode)
        if (patterns.isNotEmpty()) {
            playable.firstOrNull { f -> patterns.any { f.name.lowercase().contains(it) } }?.let { return it }
            // séries anthologie (Monstre S4E5 rangé en S01E05)
            if (season != null && season > 1 && episode != null) {
                val anthology = "s01e" + episode.toString().padStart(2, '0')
                playable.firstOrNull { it.name.lowercase().contains(anthology) }?.let { return it }
            }
        }
        fileIdx?.let { idx -> files.getOrNull(idx)?.takeIf { it in playable }?.let { return it } }
        return playable.maxByOrNull { it.size }
    }

    private fun get(path: String, key: String, params: Map<String, String>): JSONObject? {
        val url = "$API$path".toHttpUrl().newBuilder().addQueryParameter("agent", AGENT).apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return call(Request.Builder().url(url).header("Authorization", "Bearer $key").get().build())
    }

    private fun post(path: String, key: String, form: Map<String, String>): JSONObject? {
        val url = "$API$path".toHttpUrl().newBuilder().addQueryParameter("agent", AGENT).build()
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return call(Request.Builder().url(url).header("Authorization", "Bearer $key").post(body).build())
    }

    private fun call(request: Request): JSONObject? =
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            runCatching { JSONObject(text) }.getOrNull()
        }
}
