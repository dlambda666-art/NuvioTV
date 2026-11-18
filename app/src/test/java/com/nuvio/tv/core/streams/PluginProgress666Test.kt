package com.nuvio.tv.core.streams

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cas piège (18/11) : un plugin qui ne répond pas n'empêche pas les autres d'afficher leur état. */
class PluginProgress666Test {
    @Test fun `etats independants`() {
        val P = PluginProgress666
        P.start("k", listOf("Lumio666" to "G", "Comet666" to "G", "Wascustom666" to "G", "StreamFusion666" to "G"))
        P.done("k", "Lumio666", 3, null)
        P.done("k", "Comet666", 0, "Thepiratebay : 1 trouvé · 1 pas de VF")
        P.done("autre", "Wascustom666", 5, null) // autre recherche : ignoré
        P.finish("k", asleep = setOf("StreamFusion666"))
        val s = P.search.value!!.plugins.associate { it.name to it.state }
        assertEquals(P.State.OK, s["Lumio666"])
        assertEquals(P.State.FAILED, s["Comet666"])
        assertEquals(P.State.FAILED, s["Wascustom666"])
        assertEquals(P.State.ASLEEP, s["StreamFusion666"])
    }
}
