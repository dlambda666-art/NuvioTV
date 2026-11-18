package com.nuvio.tv.core.plugin

/**
 * FrankenStream (18/11) : au lieu d'un simple « HTTP 0 », la vraie cause quand un addon ne répond pas.
 * Seulement dire ce qui se passe (aucun contournement). Jamais d'adresse ni de clé dans le texte.
 */
object NetCause666 {
    /** Règle pure (testée) : la cause courte, en français, à partir de l'erreur réseau. */
    fun describe(t: Throwable?): String {
        var e: Throwable? = t
        var depth = 0
        while (e != null && depth < 6) {
            val name = e.javaClass.simpleName
            val msg = e.message.orEmpty()
            when {
                e is java.net.UnknownHostException || name == "UnknownHostException" ||
                    msg.contains("Unable to resolve host", true) || msg.contains("No address associated", true) ->
                    return "adresse introuvable (site fermé, déplacé ou bloqué par le fournisseur d'accès)"
                e is java.net.SocketTimeoutException || name.contains("Timeout", true) || msg.contains("timeout", true) ||
                    msg.contains("timed out", true) -> return "délai dépassé (le site ne répond pas à temps)"
                e is javax.net.ssl.SSLHandshakeException || e is javax.net.ssl.SSLPeerUnverifiedException ||
                    name.contains("CertPath", true) || msg.contains("certificate", true) || msg.contains("Trust anchor", true) ->
                    return "certificat refusé (connexion sécurisée impossible)"
                e is java.net.ConnectException || msg.contains("Connection refused", true) || msg.contains("Failed to connect", true) ->
                    return "connexion refusée (le serveur ne répond pas)"
                msg.contains("reset", true) || msg.contains("unexpected end of stream", true) || msg.contains("Broken pipe", true) ||
                    msg.contains("closed", true) -> return "connexion coupée en route"
                msg.contains("Canceled", true) || msg.contains("Cancelled", true) -> return "recherche annulée"
            }
            e = e.cause
            depth++
        }
        return "pas de réponse du serveur"
    }
}
