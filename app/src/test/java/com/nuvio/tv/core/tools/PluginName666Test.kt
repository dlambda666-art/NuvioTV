package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Cas piège (18/11) : le plugin wastream666 s'affiche « Wascustom666 » partout, sans perdre sa place. */
class PluginName666Test {
    @Test fun `nom affiche`() {
        assertEquals("Wascustom666", PluginOrder666.displayName("repo:wastream666", "Wastream666"))
        assertEquals("Wascustom666", PluginOrder666.displayName("", "Wacustom666"))
        assertEquals("Lumio666", PluginOrder666.displayName("repo:lumio666", "Lumio666"))
        assertEquals("Wascustom", PluginOrder666.labelOf("Wastream666"))
        assertEquals("Wascustom", PluginOrder666.labelOf("Wascustom666"))
        listOf("Wastream666", "Wacustom666").forEach { old ->
            assertFalse(PluginOrder666.displayName("x:wastream666", old).contains("Wastream") ||
                PluginOrder666.displayName("x:wastream666", old).contains("Wacustom"))
        }
    }

    @Test fun `meme place dans l'ordre des cavaliers`() {
        assertEquals(
            com.nuvio.tv.core.player.StreamAutoPlaySelector.plugin666Rank("Wastream666"),
            com.nuvio.tv.core.player.StreamAutoPlaySelector.plugin666Rank("Wascustom666")
        )
    }
}
