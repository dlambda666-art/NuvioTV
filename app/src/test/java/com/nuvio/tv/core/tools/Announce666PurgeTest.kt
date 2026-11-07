package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cas pièges : la liste des annonces ne grossit plus sans fin (07/11). */
class Announce666PurgeTest {
    private val day = 86_400_000L

    @Test fun `annonces de plus de 7 jours effacees toutes seules`() {
        val now = 100 * day
        val list = listOf(
            Announce666.Entry(now - 1 * day, "NXT", "calendrier"),
            Announce666.Entry(now - 6 * day, "Film", "guetteur"),
            Announce666.Entry(now - 8 * day, "Vieux", "guetteur")
        )
        assertEquals(listOf("NXT", "Film"), Announce666.keepRecent(list, now).map { it.text })
    }
}
