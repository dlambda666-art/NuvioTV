package com.nuvio.tv.core.streams

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cas piège (18/11) : un plugin qui ne répond pas n'empêche pas les autres d'afficher leur état. */
class PluginProgress666Test {
    @Test fun `etats independants`() {
        PluginProgress666.start("k", listOf("Lumio666" to "G", "Comet666" to "G", "Wascustom666" to "G", "StreamFusion666" to "G"))
        PluginProgress666.done("k", "Lumio666", 3, null)
        PluginProgress666.done("k", "Comet666", 0, "Thepiratebay : 1 trouvé · 1 pas de VF")
        PluginProgress666.done("autre", "Wascustom666", 5, null) // autre recherche : ignoré
        PluginProgress666.finish("k", asleep = setOf("StreamFusion666"))
        val s = PluginProgress666.search.value!!.plugins.associate { it.name to it.state }
        assertEquals(PluginProgress666.State.OK, s["Lumio666"])
        assertEquals(PluginProgress666.State.FAILED, s["Comet666"])
        assertEquals(PluginProgress666.State.FAILED, s["Wascustom666"])
        assertEquals(PluginProgress666.State.ASLEEP, s["StreamFusion666"])
    }
}
