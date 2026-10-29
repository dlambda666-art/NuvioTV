package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Réorganisation de l'accueil (29/10) : ce qui part, ce qui reste, l'ordre des portes. */
class HomeReorg666Test {
    private fun f(id: String, title: String, cover: String? = null, vararg cats: String) = CollectionFolder(
        id = id, title = title, coverImageUrl = cover,
        sources = cats.map { AddonCatalogCollectionSource("frank", "movie", it) }
    )
    private val names = mapOf(
        "so-slasher" to "Slasher - Film", "so-scariest" to "Scariest Ever - Film", "so-zombie" to "Zombie - Film",
        "so-haunted" to "Haunted House - Film", "so-witch" to "Witchcraft - Film", "fs-horreur-classiques" to "Classiques"
    )

    @Test
    fun `home is reorganised and nothing else moves`() {
        val home = listOf(
            Collection("c1", "Streaming", folders = listOf(
                f("n", "Netflix", "x/streaming-netflix.png"), f("s", "Shudder", "x/streaming-shudder.png"), f("cp", "Canal+", "sf00sWZ2")
            )),
            Collection("c2", "Pour toi", folders = listOf(
                f("vf", "En VF cette semaine", "g0hvcfM4"), f("pt", "Pour toi"), f("fi", "Films", "FKYdvQR5"),
                f("se", "Séries", "FKYdvQR5"), f("cs", "Prochainement", "cLGKrgcd")
            )),
            Collection("c3", "Genres", folders = listOf(
                f("h", "Horreur", "PrRXbjxT", "so-slasher", "so-scariest", "so-zombie", "so-haunted", "so-witch", "fs-horreur-classiques")
            )),
            Collection("c4", "Top 10", folders = listOf(f("t", "Top 10 films")))
        )
        var n = 0
        val p = HomeReorg666.plan(home, { names[it.catalogId] }, null) { "id${n++}" }
        val rows = p.collections
        assertEquals(listOf("En VF cette semaine", "Pour toi", "Shudder", "Prochainement"), rows[0].folders.map { it.title })
        val doors = rows[1].folders + rows[2].folders
        assertEquals(listOf("Horreur", "Classiques", "Slasher", "Les plus effrayants", "Zombies", "Maisons hantées", "Sorcellerie"), doors.map { it.title })
        assertTrue(doors.all { it.coverImageUrl == HomeReorg666.PORTE_COVER })
        // Top 10 intact, Streaming et Genres vidés et retirés, Netflix / Canal+ / Films / Séries partis
        assertEquals(listOf("Pour toi", "Horreur", "Horreur ·", "Top 10"), rows.map { it.title })
        val titles = rows.flatMap { it.folders }.map { it.title }
        assertTrue(titles.none { it in setOf("Netflix", "Canal+", "Films", "Séries") })
        assertTrue(p.report.any { it.startsWith("Pas trouvé : Vampires") })
        // Deuxième passage : rien ne bouge
        val again = HomeReorg666.plan(rows, { names[it.catalogId] }, null) { "z${n++}" }
        assertEquals(rows, again.collections)
    }
}
