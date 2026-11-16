package com.nuvio.tv.core.streams

/**
 * FrankenStream (16/11, idée prise dans Cauldron) : un seul exemplaire de chaque fichier entre nos plugins.
 *
 * Avant : deux plugins qui renvoyaient le même fichier n'étaient fusionnés que si le texte de la release
 * était identique. Maintenant, le même torrent (même empreinte / hash) est aussi reconnu, même quand un
 * plugin affiche le nom du fichier et l'autre le nom du torrent.
 *
 * Quelle copie on garde : celle en cache TorBox, sinon « AllDebrid ✓ », sinon celle du plugin le mieux
 * placé ; un « AllDebrid ? » ne remplace jamais rien et n'est gardé que s'il est seul.
 */
class PluginDedup666 {
    /** clé (release ou hash) → score de la copie gardée (plus petit = meilleur). */
    private val kept = HashMap<String, Int>()

    data class Verdict(val keep: Boolean, val displaced: Set<String>)

    /**
     * [keys] : les clés du flux (release+qualité, et « h:hash »). [score] : voir [score].
     * [guess] : lien « AllDebrid ? » (jamais devant une copie sûre).
     */
    fun admit(keys: List<String>, score: Int, guess: Boolean): Verdict {
        if (keys.isEmpty()) return Verdict(true, emptySet())
        val known = keys.mapNotNull { k -> kept[k]?.let { k to it } }
        if (guess) {
            if (known.isNotEmpty()) return Verdict(false, emptySet())
            keys.forEach { kept[it] = GUESS_SCORE }
            return Verdict(true, emptySet())
        }
        val best = known.minOfOrNull { it.second }
        if (best != null && best <= score) return Verdict(false, emptySet())
        // meilleure copie : elle prend toutes ses clés, les copies d'avant sont retirées
        val displaced = known.map { it.first }.toSet()
        keys.forEach { kept[it] = score }
        return Verdict(true, displaced)
    }

    companion object {
        const val GUESS_SCORE = 10_000

        /** Score d'une copie : en cache TorBox (0) < AllDebrid ✓ (100) < le reste (200), puis l'ordre des plugins. */
        fun score(cachedTorBox: Boolean, allDebridReady: Boolean, pluginRank: Int): Int =
            when {
                cachedTorBox -> 0
                allDebridReady -> 100
                else -> 200
            } + pluginRank.coerceIn(0, 99)

        /** Clé « empreinte » d'un torrent (null s'il n'en a pas). */
        fun hashKey(hash: String?): String? =
            hash?.trim()?.lowercase()?.takeIf { it.length >= 32 }?.let { "h:$it" }
    }
}
