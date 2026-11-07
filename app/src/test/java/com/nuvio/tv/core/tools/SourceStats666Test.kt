package com.nuvio.tv.core.tools

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cas pièges : bilan des sources de Comet666 (07/11). */
class SourceStats666Test {
    private fun s(title: String, group: String = "666|comet666|x") = Stream(
        name = "Comet666", title = title, description = null, url = null, ytId = null, infoHash = "a".repeat(40),
        fileIdx = null, externalUrl = null, behaviorHints = StreamBehaviorHints(null, group, null, null),
        addonName = "Comet666", addonLogo = null
    )

    @Test fun `source lue sur la 3e ligne`() {
        assertEquals("YggReborn", SourceStats666.sourceOf(s("Rel.FRENCH.1080p\n🇫🇷 VF\n2.1 GB · 👤 12 · YggReborn")))
        assertEquals("TorBox", SourceStats666.sourceOf(s("Rel\n🇫🇷 VF\n2.1 GB · TorBox ⚡")))
        assertEquals("StremThru", SourceStats666.sourceOf(s("Rel\n🇫🇷 VF\nStremThru")))
    }

    @Test fun `pas de source - taille seule, autre plugin, pas de 3e ligne`() {
        assertNull(SourceStats666.sourceOf(s("Rel\n🇫🇷 VF\n2.1 GB")))
        assertNull(SourceStats666.sourceOf(s("Rel\n🇫🇷 VF\n2.1 GB · YggReborn", group = "666|lumio666|x")))
        assertNull(SourceStats666.sourceOf(s("Rel\n🇫🇷 VF")))
    }

    @Test fun `seuls les 7 derniers jours comptent, les lances d'abord`() {
        val today = 20_000L
        val lines = SourceStats666.summarize(mapOf(
            "20000|YggReborn|shown" to 10, "19995|YggReborn|shown" to 5, "19990|YggReborn|shown" to 99,
            "20000|TorBox|shown" to 3, "19999|TorBox|played" to 2, "bad" to 1
        ), today)
        assertEquals(listOf(SourceStats666.Line("TorBox", 3, 2), SourceStats666.Line("YggReborn", 15, 0)), lines)
    }
}
