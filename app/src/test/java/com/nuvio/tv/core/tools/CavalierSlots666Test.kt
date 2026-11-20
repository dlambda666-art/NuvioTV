package com.nuvio.tv.core.tools

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cas pièges (20/11) : cases de cavalier sans nom. */
class CavalierSlots666Test {
    @Before fun reset() = CavalierSlots666.setState(emptyMap(), emptySet())
    @After fun clean() = CavalierSlots666.setState(emptyMap(), emptySet())

    private fun store(vararg pairs: Pair<String, Map<String, Any>>) = mutableMapOf(*pairs)

    @Test fun `les liens deja colles restent dans leur case`() {
        // mêmes id qu'avant : Lumio → case 1, StreamFusion → case 2, Wastream → case 3
        assertEquals(listOf("lumio666", "streamfusion666", "wastream666", "case4666", "case5666"),
            CavalierSlots666.SLOTS.map { it.scraperId })
    }

    @Test fun `le nom affiche est celui de l'addon`() = runBlocking {
        val s = store(
            "lumio666" to mapOf("url" to "https://a.test/CONFIG"),
            "case4666" to mapOf("url" to "https://b.test/CONFIG")
        )
        CavalierSlots666.refresh(
            read = { s[it] ?: emptyMap() },
            write = { id, v -> s[id] = v },
            fetchName = { if (it.contains("b.test")) "Torrentio FR 🇫🇷" else "Lumio" }
        )
        assertEquals("Torrentio FR666", PluginOrder666.displayName("repo:case4666", "Case4666"))
        assertEquals("Torrentio FR", PluginOrder666.labelOf("Case4666"))
        assertEquals("Lumio666", PluginOrder666.displayName("repo:lumio666", "Lumio666"))
        // le nom est gardé à côté du lien (et partira avec « Envoyer mes réglages »)
        assertEquals("Torrentio FR", s["case4666"]?.get("nom"))
        // classement : le nom affiché retombe sur sa case
        assertEquals("Case4666", PluginOrder666.canonical("Torrentio FR666"))
    }

    @Test fun `une case vide dort et s'appelle Case N`() = runBlocking {
        val s = store("lumio666" to mapOf("url" to "https://a.test/x"))
        CavalierSlots666.refresh(read = { s[it] ?: emptyMap() }, write = { id, v -> s[id] = v }, fetchName = { null })
        assertTrue("case4666" in CavalierSlots666.emptySlots.value)
        assertTrue("wastream666" in CavalierSlots666.emptySlots.value)
        assertFalse("lumio666" in CavalierSlots666.emptySlots.value)
        assertEquals("Case 4", PluginOrder666.displayName("repo:case4666", "Case4666"))
        // addon injoignable : on garde l'ancien nom, jamais vide
        assertEquals("Lumio666", PluginOrder666.displayName("repo:lumio666", "Lumio666"))
    }

    @Test fun `jamais un lien ni une cle a la place du nom`() {
        assertNull(CavalierSlots666.cleanName("https://x.test/eyJrZXkiOiJzZWNyZXQifQ/manifest.json"))
        assertNull(CavalierSlots666.cleanName("   "))
        assertEquals("Wastream", CavalierSlots666.cleanName("Wastream666"))
        assertTrue(CavalierSlots666.cleanName("Un nom vraiment beaucoup trop long pour la TV")!!.length <= 18)
    }

    @Test fun `vieux noms Wascustom toujours ranges en case 3`() {
        assertEquals("Wastream666", PluginOrder666.canonical("Wascustom666"))
        assertEquals(CavalierSlots666.SLOTS[2], CavalierSlots666.slotOfName(PluginOrder666.canonical("Wacustom666")))
    }
}
