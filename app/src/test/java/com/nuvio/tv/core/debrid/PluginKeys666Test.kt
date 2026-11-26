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

    // 26/11 : la date de diffusion de Frank prêtée à Comet666 (NXT : numérotation TMDB différente)
    @org.junit.Test fun `date de diffusion pretee a comet`() {
        PluginKeys666.putAirDate(20, 47, "2026-11-25T00:00:00Z")
        PluginKeys666.putAirDate(1, 2, "pas une date")
        val comet = PluginKeys666.withAutoKeys("comet666", emptyMap())
        val json = org.json.JSONObject(comet["air_dates_auto"] as String)
        org.junit.Assert.assertEquals("2026-11-25", json.getString("20:47"))
        org.junit.Assert.assertFalse(json.has("1:2"))
        org.junit.Assert.assertFalse(PluginKeys666.withAutoKeys("lumio666", emptyMap()).containsKey("air_dates_auto"))
    }
}
