package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Cas piège (18/11, 20/11) : le plugin wastream666 s'affiche « Wastream666 » partout (même avec un vieux nom Wascustom), sans perdre sa place. */
class PluginName666Test {
    @Test fun `nom affiche`() {
        assertEquals("Wastream666", PluginOrder666.displayName("repo:wastream666", "Wastream666"))
        assertEquals("Wastream666", PluginOrder666.displayName("", "Wacustom666"))
        assertEquals("Lumio666", PluginOrder666.displayName("repo:lumio666", "Lumio666"))
        assertEquals("Wastream", PluginOrder666.labelOf("Wastream666"))
        assertEquals("Wastream", PluginOrder666.labelOf("Wascustom666"))
        listOf("Wascustom666", "Wacustom666").forEach { old ->
            assertFalse(PluginOrder666.displayName("x:wastream666", old).contains("Wascustom") ||
                PluginOrder666.displayName("x:wastream666", old).contains("Wacustom"))
        }
    }

    @Test fun `plus jamais Wascustom a l'ecran`() {
        for (n in listOf("Wascustom666", "Wacustom666", "Wastream666"))
            org.junit.Assert.assertFalse(PluginOrder666.displayName("", n).contains("asc", true))
        org.junit.Assert.assertTrue(PluginOrder666.DEFAULT.contains("Wastream666"))
    }

    @Test fun `meme place dans l'ordre des cavaliers`() {
        assertEquals(
            com.nuvio.tv.core.player.StreamAutoPlaySelector.plugin666Rank("Wastream666"),
            com.nuvio.tv.core.player.StreamAutoPlaySelector.plugin666Rank("Wascustom666")
        )
    }
}
