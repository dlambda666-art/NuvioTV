package com.nuvio.tv.core.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 20/11 : cases de cavalier sans nom (idée de David).
 *
 * Chaque case est un plugin générique : on y colle le lien de n'importe quel addon,
 * et Frank affiche le nom que l'addon se donne lui-même (lu dans son manifeste).
 * Un cavalier tombe → on recolle un autre lien dans la case, sans compile.
 *
 * Les cases 1 à 3 reprennent les plugins existants (même id → les liens collés restent).
 * Comet666 reste à part (trackers). Le lien ne sort jamais de la Shield : seul le
 * nom de l'addon est gardé à côté (réglage « nom »).
 */
object CavalierSlots666 {
    data class Slot(val number: Int, val scraperId: String, val internal: String, val oldName: String?)

    val SLOTS = listOf(
        Slot(1, "lumio666", "Lumio666", "Lumio666"),
        Slot(2, "streamfusion666", "StreamFusion666", "StreamFusion666"),
        Slot(3, "wastream666", "Wastream666", "Wastream666"),
        Slot(4, "case4666", "Case4666", null),
        Slot(5, "case5666", "Case5666", null)
    )

    const val URL_KEY = "url"
    const val NAME_KEY = "nom"

    /** Nom interne de la case → nom de l'addon (sans « 666 »). */
    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    /** Cases sans lien (id du plugin) : endormies, jamais cherchées. */
    private val _empty = MutableStateFlow<Set<String>>(emptySet())
    val emptySlots: StateFlow<Set<String>> = _empty.asStateFlow()

    fun isSlotId(id: String): Boolean = slotOfId(id) != null

    fun slotOfId(id: String): Slot? {
        val bare = id.substringAfterLast(':')
        return SLOTS.firstOrNull { it.scraperId.equals(bare, ignoreCase = true) }
    }

    fun slotOfName(name: String): Slot? {
        val n = name.trim()
        if (n.isEmpty()) return null
        return SLOTS.firstOrNull { it.internal.equals(n, true) || "Case ${it.number}".equals(n, true) } ?: SLOTS.firstOrNull { slot ->
            val custom = _names.value[slot.internal]
            custom != null && (custom.equals(n, true) || "${custom}666".equals(n, true))
        }
    }

    /** Nom affiché partout (recherche, cartes, lecteur) : « Wastream666 », ou « Case 4 » si vide. */
    fun display(slot: Slot): String =
        _names.value[slot.internal]?.let { "${it}666" } ?: slot.oldName ?: "Case ${slot.number}"

    /** Nom court (Outils 666, mes sources) : « Wastream », ou « Case 4 ». */
    fun label(slot: Slot): String =
        _names.value[slot.internal] ?: slot.oldName?.removeSuffix("666") ?: "Case ${slot.number}"

    /** Nom de l'addon nettoyé : lettres, chiffres, espaces, . _ + - ; 18 caractères max ; sans « 666 » final. */
    fun cleanName(raw: String?): String? {
        val t = (raw ?: return null)
            .replace(Regex("[^\\p{L}\\p{N} ._+\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix("666")
            .trim()
        if (t.isEmpty()) return null
        // jamais un nom qui ressemble à un lien (une config pourrait s'y cacher)
        if (t.contains("http", ignoreCase = true) || t.contains("://")) return null
        return t.take(18).trim().ifEmpty { null }
    }

    /** Pour les tests et après un rafraîchissement. */
    fun setState(names: Map<String, String>, empty: Set<String>) {
        _names.value = names
        _empty.value = empty
    }

    fun manifestUrl(base: String): String =
        base.trim().trimEnd('/').removeSuffix("/manifest.json").trimEnd('/') + "/manifest.json"

    /** Lit le nom dans le manifeste de l'addon (le lien n'est jamais écrit dans un journal). */
    suspend fun fetchManifestName(base: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(manifestUrl(base)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optString("name").takeIf { it.isNotBlank() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Relit les cases : lien présent ? nom connu ? Si un lien n'a pas encore de nom,
     * on va le chercher dans le manifeste de l'addon et on le garde à côté du lien.
     */
    suspend fun refresh(
        read: suspend (String) -> Map<String, Any>,
        write: suspend (String, Map<String, Any>) -> Unit,
        fetchName: suspend (String) -> String? = ::fetchManifestName
    ) {
        val names = mutableMapOf<String, String>()
        val empty = mutableSetOf<String>()
        for (slot in SLOTS) {
            val settings = runCatching { read(slot.scraperId) }.getOrDefault(emptyMap())
            val url = (settings[URL_KEY] as? String)?.takeIf { it.isNotBlank() }
            if (url == null) {
                empty += slot.scraperId
                continue
            }
            var name = cleanName(settings[NAME_KEY] as? String)
            if (name == null) {
                name = cleanName(runCatching { fetchName(url) }.getOrNull())
                if (name != null) runCatching { write(slot.scraperId, settings + (NAME_KEY to name)) }
            }
            if (name != null) names[slot.internal] = name
        }
        setState(names, empty)
    }
}
