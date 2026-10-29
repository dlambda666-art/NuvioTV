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
 * 666TVF : le guetteur des sorties. Une fois par jour, la Shield demande à Frank les films
 * récents qu'il croit encore sans VF, et les fait vérifier par les cavaliers. Une release VF
 * sûre trouvée → Frank est prévenu (FrankVf666) et le film arrive dans « En VF cette semaine »
 * sans qu'on l'ait ouvert (Affection, 28/10). Jamais pendant un film : il s'arrête dès qu'on lance.
 */
@Singleton
class VfWatcher666 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginManager: PluginManager
) {
    private val prefs by lazy { context.getSharedPreferences("666tv_guetteur", Context.MODE_PRIVATE) }

    /** Renvoie les titres trouvés en VF (vide si pas encore l'heure, en pause ou rien de neuf). */
    suspend fun dailyRun(busy: () -> Boolean): List<String> {
        if (System.currentTimeMillis() - prefs.getLong("at", 0L) < 24L * 3600 * 1000) return emptyList()
        val raw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Http666.get("https://lambda666-frankenstream.hf.space/vf/candidates/movie", 60_000)
        } ?: return emptyList()
        // imdbId → titre (pour nommer les films trouvés dans « Dernières annonces »)
        val names = LinkedHashMap<String, String>()
        runCatching {
            val arr = JSONObject(raw).optJSONArray("candidates") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("imdbId").takeIf { it.startsWith("tt") } ?: continue
                val year = o.optString("year").takeIf { it.isNotBlank() && it != "null" }
                names[id] = o.optString("name").ifBlank { id } + (year?.let { " ($it)" } ?: "")
            }
        }
        val list = names.keys.toList()
        val order = PluginOrder666.order.map { it.lowercase() }
        val cavaliers = runCatching { pluginManager.enabledScrapers.first() }.getOrDefault(emptyList())
            .filter { s -> s.id.substringAfterLast(':').lowercase() in order && s.supportsType("movie") }
            .sortedBy { s -> order.indexOf(s.id.substringAfterLast(':').lowercase()) }
        if (cavaliers.isEmpty()) return emptyList()
        // Reprise : les films déjà vérifiés aujourd'hui ne sont pas refaits (pause puis reprise).
        val day = java.time.LocalDate.now().toString()
        val done = if (prefs.getString("day", null) == day) prefs.getStringSet("done", emptySet()).orEmpty().toMutableSet() else mutableSetOf()
        val found = mutableListOf<String>()
        for (id in list) {
            if (id in done) continue
            // fiche / liste de flux ouverte ou film lancé : pause, reprise au prochain passage (20 min)
            if (busy()) { save(day, done); return emptyList() }
            val vf = cavaliers.any { scraper ->
                if (busy()) return@any false
                val res = withTimeoutOrNull(30_000) {
                    runCatching { pluginManager.executeScraper(scraper, id, "movie", null, null) }.getOrNull()
                }.orEmpty()
                res.any { r -> FrankVf666.isSureVfRelease(listOfNotNull(r.name, r.title).joinToString(" ")) }
            }
            if (vf) { FrankVf666.confirm(context, id, "movie", "guetteur"); found += names[id] ?: id; done += id }
            if (busy()) { save(day, done); return found }   // interrompu : les films trouvés sont quand même nommés
            done += id
        }
        save(day, done)
        prefs.edit().putLong("at", System.currentTimeMillis()).putInt("last_found", found.size).apply()
        return found
    }

    private fun save(day: String, done: Set<String>) {
        prefs.edit().putString("day", day).putStringSet("done", done).apply()
    }
}
