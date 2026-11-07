package com.nuvio.tv.core.debrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Cas pièges (07/11) : les clés ne sont prêtées qu'à Comet666, jamais aux autres cavaliers. */
class PluginKeys666Test {
    @Test fun `seul Comet666 recoit les cles`() {
        PluginKeys666.torbox = "tb"; PluginKeys666.alldebrid = "ad"
        val comet = PluginKeys666.withAutoKeys("comet666", mapOf("yggreborn" to "x"))
        assertEquals("tb", comet["torbox_auto"]); assertEquals("ad", comet["alldebrid_auto"]); assertEquals("x", comet["yggreborn"])
        val other = PluginKeys666.withAutoKeys("streamfusion666", emptyMap())
        assertFalse(other.containsKey("torbox_auto") || other.containsKey("alldebrid_auto"))
        PluginKeys666.torbox = ""; PluginKeys666.alldebrid = ""
        assertEquals(emptyMap<String, Any>(), PluginKeys666.withAutoKeys("comet666", emptyMap()))
    }
}
