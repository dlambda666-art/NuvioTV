package com.nuvio.tv.ui.screens.stream

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus

/**
 * FrankenStream (18/11, idée vue chez ToothPowerful, r/NuvioForks) : la recherche en direct, source par
 * source, sous les puces de l'écran des sources :
 * « Lumio666 ✓ 8 liens · StreamFusion666 ✓ 5 · Comet666 … en cours · Wascustom666 ✗ adresse introuvable »
 * Vert ✓, mauve « en cours », rouge seulement pour ✗.
 */
object SearchProgress666 {
    enum class State { RUNNING, OK, FAILED, GROUP, ASLEEP }
    data class Item(val name: String, val state: State, val text: String)

    private const val DIAG = "diagnostic.invalid"

    /** Nom affiché (Wastream666 → Wascustom666 : seul le nom change). */
    fun displayName(name: String): String = com.nuvio.tv.core.tools.PluginOrder666.displayName("", name)

    /** Cause courte tirée de la ligne de diagnostic du plugin (« Aucune réponse : Matrix → délai dépassé … »). */
    fun shortCause(diagTitle: String?): String {
        val first = diagTitle?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return "ne répond pas"
        val afterArrow = first.substringAfterLast("→ ", first).substringAfter("Aucune réponse : ").trim()
        val core = afterArrow.substringBefore(" (").trim().ifEmpty { "ne répond pas" }
        return if (core.length > 40) core.take(39) + "…" else core
    }

    /**
     * Règle pure (testée) : une ligne par source, chacune indépendante (un plugin qui ne répond pas
     * n'empêche jamais d'afficher les autres).
     */
    fun items(
        chips: List<SourceChipItem>,
        addonStreams: List<AddonStreams>,
        plugins: List<com.nuvio.tv.core.streams.PluginProgress666.Plugin> = emptyList()
    ): List<Item> {
        val byName = addonStreams.groupBy { displayName(it.addonName) }
        // 18/11 : plugins groupés (« Les 4 cavaliers… ») → le détail de chaque plugin dans la ligne
        val grouped = plugins.filter { it.group != it.name }.groupBy { it.group }
        return chips.flatMap { chip ->
            val members = grouped[chip.name]
            if (members.isNullOrEmpty()) listOf(chipItem(chip, byName))
            else listOf(Item(chip.name + " :", State.GROUP, "")) + members.map { pluginItem(it) }
        }
    }

    /** Règle pure (testée) : l'état d'un plugin du groupe. */
    fun pluginItem(p: com.nuvio.tv.core.streams.PluginProgress666.Plugin): Item {
        val name = displayName(p.name)
        return when (p.state) {
            com.nuvio.tv.core.streams.PluginProgress666.State.RUNNING -> Item(name, State.RUNNING, "…")
            com.nuvio.tv.core.streams.PluginProgress666.State.OK -> Item(name, State.OK, "✓ ${p.links}")
            com.nuvio.tv.core.streams.PluginProgress666.State.ASLEEP -> Item(name, State.ASLEEP, "💤")
            com.nuvio.tv.core.streams.PluginProgress666.State.FAILED -> Item(name, State.FAILED, "✗ " + causeOf(p.cause))
        }
    }

    /** Cause courte : « Thepiratebay : 1 trouvé · 1 pas de VF » → « pas de VF ». */
    private fun causeOf(raw: String?): String {
        val r = raw?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return when {
            r.isEmpty() -> "ne répond pas"
            r.contains("pas de VF", true) -> "pas de VF"
            r == "aucun lien" || r == "ne répond pas" -> r
            else -> shortCause(r)
        }
    }

    private fun chipItem(chip: SourceChipItem, byName: Map<String, List<AddonStreams>>): Item {
        return run {
            val streams = byName[displayName(chip.name)].orEmpty().flatMap { it.streams }
            val real = streams.count { s -> listOfNotNull(s.url, s.externalUrl).none { it.contains(DIAG) } }
            val diag = streams.firstOrNull { s -> listOfNotNull(s.url, s.externalUrl).any { it.contains(DIAG) } }
            val name = displayName(chip.name)
            when {
                real > 0 -> Item(name, State.OK, if (real == 1) "✓ 1 lien" else "✓ $real liens")
                chip.status == SourceChipStatus.LOADING -> Item(name, State.RUNNING, "… en cours")
                diag != null -> Item(name, State.FAILED, "✗ " + shortCause(diag.title ?: diag.name))
                chip.status == SourceChipStatus.ERROR -> Item(name, State.FAILED, "✗ ne répond pas")
                else -> Item(name, State.FAILED, "✗ aucun lien")
            }
        }
    }
}

private val Mauve666 = Color(0xFFC08BFF)
private val Red666 = Color(0xFFFF5A5A)

@Composable
internal fun SearchProgress666Line(items: List<SearchProgress666.Item>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val text = buildAnnotatedString {
        items.forEachIndexed { i, it ->
            if (i > 0) withStyle(SpanStyle(color = Color(0x99FFFFFF))) {
                append(if (items[i - 1].state == SearchProgress666.State.GROUP) " " else "  ·  ")
            }
            withStyle(SpanStyle(color = Color.White)) { append(if (it.text.isEmpty()) it.name else it.name + " ") }
            val c = when (it.state) {
                SearchProgress666.State.OK -> com.nuvio.tv.ui.components.Font666.Green
                SearchProgress666.State.RUNNING -> Mauve666
                SearchProgress666.State.FAILED -> Red666
                SearchProgress666.State.GROUP -> Color.White
                SearchProgress666.State.ASLEEP -> Color(0x99FFFFFF)
            }
            withStyle(SpanStyle(color = c)) { append(it.text) }
        }
    }
    Row(modifier = modifier.padding(top = 6.dp)) {
        Text(text = text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
