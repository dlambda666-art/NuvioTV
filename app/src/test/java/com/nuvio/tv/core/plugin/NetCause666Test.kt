package com.nuvio.tv.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Cas pièges (18/11, Wastream « HTTP 0 ») : la vraie cause, en français, jamais d'adresse. */
class NetCause666Test {
    @Test fun `site introuvable`() {
        val t = java.io.IOException("wrap", java.net.UnknownHostException("Unable to resolve host \"noplay.fr\""))
        val d = NetCause666.describe(t)
        assertEquals("adresse introuvable (site fermé, déplacé ou bloqué par le fournisseur d'accès)", d)
        assertFalse(d.contains("noplay"))
    }

    @Test fun `delai depasse`() =
        assertEquals("délai dépassé (le site ne répond pas à temps)", NetCause666.describe(java.net.SocketTimeoutException("timeout")))

    @Test fun `certificat`() =
        assertEquals("certificat refusé (connexion sécurisée impossible)", NetCause666.describe(javax.net.ssl.SSLHandshakeException("bad")))

    @Test fun `connexion refusee`() =
        assertEquals("connexion refusée (le serveur ne répond pas)", NetCause666.describe(java.net.ConnectException("Connection refused")))

    @Test fun `inconnu`() {
        assertEquals("pas de réponse du serveur", NetCause666.describe(RuntimeException("x")))
        assertEquals("pas de réponse du serveur", NetCause666.describe(null))
    }
}
