package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 11/11 piège : le profil Freddy effacé de la Shield du bas par la liste de l'autre Shield. */
class Profiles666Test {
    private fun p(id: Int, name: String) = UserProfile(id = id, name = name, avatarColorHex = "#a24bd6")

    @Test fun `un profil absent du compte est garde et renvoye`() {
        val (kept, rescued) = Profiles666.merge(listOf(p(1, "Lambda"), p(2, "Freddy")), listOf(p(1, "Lambda")))
        assertEquals(listOf("Lambda", "Freddy"), kept.map { it.name })
        assertEquals(listOf("Freddy"), rescued.map { it.name })
    }

    @Test fun `le compte fait foi pour les profils qu'il connait, rien a sauver sinon`() {
        val (kept, rescued) = Profiles666.merge(listOf(p(1, "Ancien nom")), listOf(p(1, "Lambda"), p(3, "Nouveau")))
        assertEquals(listOf("Lambda", "Nouveau"), kept.map { it.name })
        assertTrue(rescued.isEmpty())
    }

    @Test fun `compte vide - la Shield garde les siens`() {
        val (kept, rescued) = Profiles666.merge(listOf(p(1, "Lambda")), emptyList())
        assertEquals(listOf("Lambda"), kept.map { it.name })
        assertTrue(rescued.isEmpty())
    }
}
