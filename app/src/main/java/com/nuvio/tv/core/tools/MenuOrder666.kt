package com.nuvio.tv.core.tools

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 666TV : ordre des onglets du menu de gauche, choisi dans Outils 666
 * (appui long sur OK, flèches haut/bas, OK pour poser).
 */
object MenuOrder666 {
    private const val PREFS = "666tv_menu"

    /** Onglets connus (route → nom affiché dans Outils 666). */
    val ALL: List<Pair<String, String>> = listOf(
        "home" to "Accueil",
        "discover" to "Découvrir",
        "search" to "Recherche",
        "library" to "Bibliothèque",
        "calendar" to "Calendrier",
        "live_tv" to "TV en direct",
        "my_apps" to "Mes applis",
        "meteo_666" to "Météo",
        "roulette_666" to "Roulette",
        "records_666" to "Enregistrements",
        "settings" to "Paramètres"
    )

    var order by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("order", null)
        order = raw?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun save(context: Context, routes: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("order", routes.joinToString("|")).apply()
        order = routes
    }

    /** Ordre complet affiché dans Outils 666 (ordre choisi, puis les autres). */
    fun fullOrder(): List<String> = order.filter { r -> ALL.any { it.first == r } } +
        ALL.map { it.first }.filter { it !in order }

    fun labelOf(route: String): String = ALL.firstOrNull { it.first == route }?.second ?: route

    /** Trie les éléments du menu selon l'ordre choisi (les inconnus gardent leur place relative à la fin). */
    fun <T> sort(items: List<T>, routeOf: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val full = fullOrder()
        return items.sortedBy { item -> full.indexOf(routeOf(item)).let { if (it < 0) Int.MAX_VALUE else it } }
    }
}
