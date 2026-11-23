package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (22/11) : le « Rapport pour Claude » ne contient JAMAIS de clé, de token ni de lien de config. */
class Rapport666Test {
    @Test fun `aucun lien de config`() {
        val raw = "Wastream : HTTP 502 sur https://wacustom.example/eyJhbGciOiJIUzI1NiJ9.eyJrZXkiOiJzZWNyZXQifQ/manifest.json"
        val c = Rapport666.clean(raw)
        assertFalse(c.contains("http"))
        assertFalse(c.contains("eyJ"))
        assertTrue(c.contains("HTTP 502"))
        assertFalse(Rapport666.looksSecret(c))
    }

    @Test fun `aucune cle ni passkey ni token`() {
        for (raw in listOf("passkey=0123456789abcdef0123456789abcdef", "apikey: ABCDEF1234567890XYZ", "token=abc", "torbox 1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d")) {
            val c = Rapport666.clean(raw)
            assertFalse(raw, Rapport666.looksSecret(c))
            assertFalse(raw, c.contains("0123456789abcdef"))
            assertFalse(raw, c.contains("ABCDEF1234567890XYZ"))
        }
    }

    @Test fun `aucun lien magnet ni stremio`() {
        val c = Rapport666.clean("magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01 et stremio://addon/config")
        assertFalse(c.contains("btih")); assertFalse(c.contains("stremio://"))
    }

    @Test fun `le texte utile reste lisible`() {
        val ok = "sam. 21 · 15:47 · 🔔 WWE Smackdown! S28E47 en VF · Haut"
        assertEquals(ok, Rapport666.clean(ok))
        assertEquals("Case 3 : Wastream MULTI", Rapport666.clean("Case 3 : Wastream MULTI"))
    }

    @Test fun `adresse de la page seulement sur le reseau de la maison`() {
        assertEquals("http://192.168.1.20:8666/rapport", Rapport666.pageUrl("192.168.1.20"))
        assertNull(Rapport666.pageUrl(null))
        assertNull(Rapport666.pageUrl("fe80::1"))
    }

    // 23/11 : la ligne de MacReady passe le filtre telle quelle et ne ressemble à aucun secret
    @Test fun `ligne MacReady intacte`() {
        org.junit.Assert.assertEquals(Rapport666.MACREADY, Rapport666.clean(Rapport666.MACREADY))
        assertFalse(Rapport666.looksSecret(Rapport666.MACREADY))
    }
}
