package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.core.plugin.PluginManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 666TVF : chaque plugin passe le test « Matrix » directement sur la Shield
 * (c'est elle qui a les liens de config, ils n'en sortent jamais).
 * Sert à l'État 666 et à la vérification quotidienne : une config qui répond
 * mais ne renvoie plus rien (Wastream, 26/10) est repérée et signalée.
 */
@Singleton
class PluginHealth666 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginManager: PluginManager,
    private val localDebridService: com.nuvio.tv.core.debrid.LocalDebridService,
    private val debridSettingsDataStore: com.nuvio.tv.data.local.DebridSettingsDataStore
) {
    data class Result(val id: String, val name: String, val links: Int, val detail: String) {
        val ok: Boolean get() = links > 0
    }

    private val prefs by lazy { context.getSharedPreferences("666tv_plugin_health", Context.MODE_PRIVATE) }

    /** Teste tous les plugins actifs, l'un après l'autre (les plugins ne tournent pas en parallèle). */
    suspend fun checkAll(): List<Result> {
        val scrapers = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
        val results = scrapers.map { scraper ->
            val test = withTimeoutOrNull(70_000) { runCatching { pluginManager.testScraper(scraper.id) }.getOrNull() }
            val streams = test?.getOrNull()?.first.orEmpty()
            val real = streams.filter { !it.url.contains("diagnostic.invalid") }
            val firstDiag = streams.firstOrNull { it.url.contains("diagnostic.invalid") }?.title?.lineSequence()?.firstOrNull()
            // 666TVF : la liste ne suffit pas — le 1er lien doit aussi s'ouvrir (Frenchio en 403, 26/10).
            val refused = real.firstOrNull { it.url.startsWith("http", ignoreCase = true) }?.let { probe(it.url, it.headers) }
                ?.takeIf { it == -1 || it >= 400 }
            // 666TVF : torrents (Comet666) : il faut qu'au moins un soit en cache chez le débrideur,
            // sinon rien ne se lance (vérifié comme pour le calendrier).
            if (real.isNotEmpty() && real.none { it.url.startsWith("http", ignoreCase = true) }) {
                val hashes = real.mapNotNull { r ->
                    r.infoHash?.lowercase() ?: Regex("btih:([0-9a-fA-F]{40})").find(r.url)?.groupValues?.get(1)?.lowercase()
                }.distinct()
                val cached = cachedCount(hashes)
                if (cached == 0) {
                    return@map Result(scraper.id, scraper.name, 0, "${real.size} torrent(s), aucun en cache chez le débrideur")
                }
                if (cached != null) {
                    return@map Result(scraper.id, scraper.name, cached, "$cached/${hashes.size} en cache pour Matrix")
                }
            }
            if (refused != null) {
                return@map Result(scraper.id, scraper.name, 0,
                    if (refused == -1) "${real.size} lien(s), mais le 1er ne répond pas" else "${real.size} lien(s), mais refusés (HTTP $refused)")
            }
            Result(
                id = scraper.id,
                name = scraper.name,
                links = real.size,
                detail = when {
                    test == null -> "Pas de réponse en 70 s"
                    real.isNotEmpty() -> "${real.size} lien(s) pour Matrix"
                    firstDiag != null -> firstDiag
                    else -> "Aucun lien pour Matrix"
                }
            )
        }
        save(results)
        return results
    }

    /** Nombre de torrents en cache chez le débrideur actif ; null si on ne peut pas vérifier. */
    private suspend fun cachedCount(hashes: List<String>): Int? {
        if (hashes.isEmpty()) return null
        val account = runCatching { debridSettingsDataStore.settings.first().activeResolverCredential }.getOrNull() ?: return null
        return runCatching { localDebridService.checkCached(account, hashes.take(40))?.size }.getOrNull()
    }

    /** Code HTTP de l'ouverture du lien (2 premiers octets seulement), -1 si pas de réponse. */
    private suspend fun probe(url: String, headers: Map<String, String>?): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("Range", "bytes=0-1")
                headers?.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try { conn.responseCode } finally { conn.disconnect() }
        }.getOrDefault(-1)
    }

    fun lastRunAt(): Long = prefs.getLong("at", 0L)

    fun lastResults(): List<Result> = runCatching {
        val json = JSONObject(prefs.getString("results", "{}") ?: "{}")
        json.keys().asSequence().map { id ->
            val o = json.getJSONObject(id)
            Result(id, o.optString("name"), o.optInt("links"), o.optString("detail"))
        }.toList()
    }.getOrDefault(emptyList())

    /**
     * Vérification quotidienne : renvoie les plugins tombés à 0 lien qui n'ont pas
     * encore été signalés (une seule alerte par panne ; elle se réarme quand ça remarche).
     */
    suspend fun dailyCheckNewlyBroken(): List<Result> {
        if (System.currentTimeMillis() - lastRunAt() < 24L * 3600 * 1000) return emptyList()
        val results = checkAll()
        if (results.isEmpty()) return emptyList()
        val alerted = prefs.getStringSet("alerted", emptySet()).orEmpty().toMutableSet()
        val broken = results.filter { !it.ok }
        alerted.retainAll(broken.map { it.id }.toSet())          // réparé → réarmé
        val fresh = broken.filter { it.id !in alerted }
        alerted += fresh.map { it.id }
        prefs.edit().putStringSet("alerted", alerted).apply()
        return fresh
    }

    private fun save(results: List<Result>) {
        val json = JSONObject()
        results.forEach { r ->
            json.put(r.id, JSONObject().put("name", r.name).put("links", r.links).put("detail", r.detail))
        }
        prefs.edit().putString("results", json.toString()).putLong("at", System.currentTimeMillis()).apply()
    }
}
