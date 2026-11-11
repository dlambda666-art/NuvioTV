package com.nuvio.tv.core.tools

import android.content.Context

/**
 * 666TVF (07/11) : fichiers « menteurs ». Un torrent annoncé en VF mais dont le fichier n'a aucune
 * piste française (American Horror Story S13E01) est retenu par son empreinte : il n'est plus jamais
 * proposé ni lancé en auto (son équipe de release est aussi signalée à Frank). Gardé 180 jours.
 */
object NoFrench666 {
    private const val PREFS = "666tv_nofrench"
    const val KEEP_DAYS = 180
    const val MAX = 500

    @Volatile private var cache: Set<String>? = null

    private fun norm(hash: String?): String? = hash?.trim()?.lowercase()?.takeIf { Regex("^[0-9a-f]{40}$").matches(it) }

    /** Règle pure (testée) : liste « hash|jour » nettoyée (périmés retirés, MAX gardés, plus récents d'abord). */
    fun pruned(entries: List<String>, today: Long): List<String> =
        entries.filter { e -> (e.substringAfter('|').toLongOrNull() ?: 0L) > today - KEEP_DAYS }
            .distinctBy { it.substringBefore('|') }
            .take(MAX)

    private fun today() = System.currentTimeMillis() / 86_400_000L

    private fun load(context: Context): List<String> = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "").orEmpty()
            .split(',').filter { it.contains('|') }
    }.getOrDefault(emptyList())

    fun hashes(context: Context): Set<String> =
        cache ?: pruned(load(context), today()).map { it.substringBefore('|') }.toSet().also { cache = it }

    /** Empreintes déjà chargées (sans contexte, pour le choix de la lecture auto). */
    fun cachedHashes(): Set<String> = cache ?: emptySet()

    fun isLiar(context: Context, hash: String?): Boolean = norm(hash)?.let { it in hashes(context) } == true

    fun remember(context: Context, hash: String?) {
        val h = norm(hash) ?: return
        val list = pruned(listOf("$h|${today()}") + load(context), today())
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", list.joinToString(",")).apply() }
        cache = list.map { it.substringBefore('|') }.toSet()
    }

    /** 11/11 : liste « empreinte|jour » pour l'autre Shield / la clé. */
    fun exportList(context: Context): List<String> = pruned(load(context), today())

    /** Fusionne les fichiers menteurs repérés par l'autre Shield. Renvoie le nombre de nouveaux. */
    fun importList(context: Context, entries: List<String>): Int {
        val mine = load(context)
        val known = mine.map { it.substringBefore('|') }.toSet()
        val fresh = entries.filter { e -> norm(e.substringBefore('|')) != null && e.substringBefore('|') !in known }
        if (fresh.isEmpty()) return 0
        val list = pruned(mine + fresh, today())
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", list.joinToString(",")).apply() }
        cache = list.map { it.substringBefore('|') }.toSet()
        return fresh.size
    }

    /** Pour les tests. */
    internal fun setCacheForTest(hashes: Set<String>?) { cache = hashes }
}
