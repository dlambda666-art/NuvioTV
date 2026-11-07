package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.domain.model.Stream

/**
 * 666TVF (07/11) : bilan des sources de Comet666 sur 7 jours (affiché dans Outils 666).
 * Pour chaque source (YggReborn, C411, TorBox, StremThru…) : liens VF proposés et liens lancés.
 * Sert à savoir ce qui sert vraiment, et quand StreamFusion peut être coupé pour de bon.
 */
object SourceStats666 {
    private const val PREFS = "666tv_source_stats"
    const val DAYS = 7

    data class Line(val source: String, val shown: Int, val played: Int)

    /** Source d'un lien Comet666 (3e ligne : « 2.1 GB · 👤 12 · YggReborn »), ou null. */
    fun sourceOf(stream: Stream): String? {
        if (stream.behaviorHints?.bingeGroup?.split('|')?.getOrNull(1) != "comet666") return null
        val line3 = stream.title?.lines()?.getOrNull(2)?.trim().orEmpty()
        if (line3.isBlank()) return null
        val last = line3.split(" · ").last().trim().split(' ').first().trim()
        return last.takeIf { it.isNotBlank() && it.first().isLetter() && !it.contains("GB") && !it.contains("MB") }
    }

    private fun today() = System.currentTimeMillis() / 86_400_000L

    private fun bump(context: Context, counts: Map<String, Int>, kind: String) {
        if (counts.isEmpty()) return
        runCatching {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val day = today()
            val e = p.edit()
            counts.forEach { (src, n) ->
                val k = "$day|$src|$kind"
                e.putInt(k, p.getInt(k, 0) + n)
            }
            // ménage : plus vieux que 8 jours
            p.all.keys.filter { (it.substringBefore('|').toLongOrNull() ?: day) < day - DAYS }.forEach { e.remove(it) }
            e.apply()
        }
    }

    fun recordShown(context: Context, streams: List<Stream>) =
        bump(context, streams.mapNotNull { sourceOf(it) }.groupingBy { it }.eachCount(), "shown")

    fun recordPlayed(context: Context, stream: Stream) {
        val src = sourceOf(stream) ?: return
        bump(context, mapOf(src to 1), "played")
    }

    /** Règle pure (testée) : additionne les clés « jour|source|type » des 7 derniers jours. */
    fun summarize(entries: Map<String, Int>, today: Long): List<Line> {
        val shown = HashMap<String, Int>(); val played = HashMap<String, Int>()
        entries.forEach { (k, v) ->
            val parts = k.split('|')
            if (parts.size != 3) return@forEach
            val day = parts[0].toLongOrNull() ?: return@forEach
            if (day <= today - DAYS || day > today) return@forEach
            val target = if (parts[2] == "played") played else shown
            target[parts[1]] = (target[parts[1]] ?: 0) + v
        }
        return (shown.keys + played.keys).map { Line(it, shown[it] ?: 0, played[it] ?: 0) }
            .sortedWith(compareByDescending<Line> { it.played }.thenByDescending { it.shown }.thenBy { it.source })
    }

    fun summary(context: Context): List<Line> = runCatching {
        val all = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
        summarize(all, today())
    }.getOrDefault(emptyList())
}
