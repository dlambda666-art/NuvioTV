package com.nuvio.tv.core.m6

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M6Plus666Test {

    @Test fun `connexion - on retire l'enveloppe jsonp`() {
        assertEquals("{\"UID\":\"x\"}", M6Plus666.stripJsonp("jsonp_3bbusffr388pem4({\"UID\":\"x\"});"))
        assertEquals("{\"a\":1}", M6Plus666.stripJsonp("{\"a\":1}"))
    }

    @Test fun `flux - HD avant SD, et seulement le bon type`() {
        val a = listOf(
            M6Plus666.Asset("usp_dashcenc_h264", "sd", "https://sd"),
            M6Plus666.Asset("usp_dashcenc_h264", "hd", "https://hd"),
            M6Plus666.Asset("http_h264", "hd", "https://autre")
        )
        assertEquals("https://hd", M6Plus666.pickAsset(a, "usp_dashcenc_h264")?.url)
        assertNull(M6Plus666.pickAsset(a, "delta_dashcenc_h264"))
        assertNull(M6Plus666.pickAsset(listOf(M6Plus666.Asset("usp_dashcenc_h264", "hd", "")), "usp_dashcenc_h264"))
    }

    @Test fun `licence - on lit le champ license de drmtoday`() {
        assertEquals("QUJD", M6Plus666.licenseBase64("{\"status\":\"ok\",\"license\":\"QUJD\"}"))
        assertNull(M6Plus666.licenseBase64("binaire"))
        assertNull(M6Plus666.licenseBase64("{\"status\":\"ko\"}"))
    }

    @Test fun `chaines - les 4 directs, pas de radio (Frank = pas de musique)`() {
        assertEquals(listOf("M6", "W9", "6ter", "Gulli"), M6Plus666.CHANNELS.map { it.label })
        assertEquals("6T", M6Plus666.CHANNELS.first { it.label == "6ter" }.liveId)
        assertFalse(M6Plus666.CHANNELS.any { it.liveId in setOf("fun_radio", "rtl2") })
    }

    @Test fun `episodes - 2 avant 10`() {
        val t = listOf("Épisode 10", "Épisode 2", "Épisode 1").sortedBy { M6Plus666.naturalKey(it) }
        assertEquals(listOf("Épisode 1", "Épisode 2", "Épisode 10"), t)
    }

    @Test fun `messages - clairs, sans rien de secret`() {
        assertTrue(M6Plus666.explain(403).contains("compte"))
        assertTrue(M6Plus666.explain(503).contains("réessaie"))
    }

    @Test fun `rapport - jamais de jeton ni de lien complet`() {
        val m = M6Plus666.safeMessage("échec https://lic.drmtoday.com/x?token=abc jeton eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9eyJzdWIiOiIxMjM0NTY3ODkwIn0")
        assertFalse(m.contains("token=abc"))
        assertFalse(m.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertTrue(m.contains("lic.drmtoday.com"))
    }
}
