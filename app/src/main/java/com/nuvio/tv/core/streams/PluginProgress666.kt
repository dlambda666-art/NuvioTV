package com.nuvio.tv.core.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FrankenStream (18/11) : l'état de chaque plugin pendant une recherche, même quand les plugins sont
 * groupés sous « Les 4 cavaliers de l'apocalypse » (David garde le regroupement). Sert à la ligne de
 * recherche en direct : « Les 4 cavaliers : Lumio666 ✓ 2 · Comet666 ✗ pas de VF · Wascustom666 … ».
 */
object PluginProgress666 {
    enum class State { RUNNING, OK, FAILED, ASLEEP }
    data class Plugin(val name: String, val group: String, val state: State, val links: Int = 0, val cause: String? = null)
    data class Search(val key: String, val plugins: List<Plugin>)

    private val _search = MutableStateFlow<Search?>(null)
    val search: StateFlow<Search?> = _search

    @Synchronized fun start(key: String, plugins: List<Pair<String, String>>) {
        _search.value = Search(key, plugins.map { (name, group) -> Plugin(name, group, State.RUNNING) })
    }

    @Synchronized private fun update(key: String, name: String, f: (Plugin) -> Plugin) {
        val s = _search.value?.takeIf { it.key == key } ?: return
        _search.value = s.copy(plugins = s.plugins.map { if (it.name == name) f(it) else it })
    }

    /** Résultat d'un plugin : liens réels et ligne de diagnostic éventuelle (url diagnostic.invalid). */
    fun done(key: String, name: String, realLinks: Int, diagTitle: String?) = update(key, name) {
        when {
            realLinks > 0 -> it.copy(state = State.OK, links = realLinks)
            diagTitle != null -> it.copy(state = State.FAILED, cause = diagTitle)
            else -> it.copy(state = State.FAILED, cause = "aucun lien")
        }
    }

    /** Fin de la recherche : ceux qui n'ont rien rendu sont « en veille » (hibernation) ou sans réponse. */
    @Synchronized fun finish(key: String, asleep: Set<String>) {
        val s = _search.value?.takeIf { it.key == key } ?: return
        _search.value = s.copy(plugins = s.plugins.map {
            if (it.state != State.RUNNING) it
            else if (it.name in asleep) it.copy(state = State.ASLEEP)
            else it.copy(state = State.FAILED, cause = "ne répond pas")
        })
    }
}
