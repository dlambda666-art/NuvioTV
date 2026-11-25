package com.nuvio.tv.core.tools

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 22/11 : « Rapport pour Claude ». Tout ce qui sert à réparer, en un texte à copier-coller dans
 * la conversation : version, Shield, réglages, cases de cavalier, derniers tests des cavaliers,
 * journal des annonces et des coupures.
 *
 * JAMAIS de clé, de token, de passkey ni de lien de config : tout passe par [clean] (testé).
 * Lisible sur le téléphone (réseau de la maison seulement) : http://<ip de la Shield>:8666/rapport.
 */
object Rapport666 {
    const val PATH = "/rapport"
    /** 23/11 : clin d'œil à The Thing (film préféré de David) : MacReady enregistre son journal. */
    const val MACREADY = "Avant-poste 31, journal de MacReady : je consigne ceci au cas où personne ne s'en sortirait…"

    private val URL_RE = Regex("""(?i)\b(?:https?|stremio|magnet|wss?|ftp)://\S+|magnet:\?\S+""")
    private val KV_RE = Regex("""(?i)\b(api[_-]?key|apikey|passkey|pass|password|token|secret|key|auth|sid|uid|hash)\s*[=:]\s*\S+""")
    private val LONG_RE = Regex("""[A-Za-z0-9_\-+/=]{24,}""")
    private val CARD_RE = Regex("""\b(?:\d[ -]?){13,19}\b""")

    /** Règle pure (testée) : enlève liens, clés, tokens et longues suites qui pourraient en être. */
    fun clean(raw: String): String = raw
        .replace(URL_RE, "[lien masqué]")
        .replace(KV_RE) { "${it.groupValues[1]}=[masqué]" }
        .replace(CARD_RE, "[masqué]")
        .replace(LONG_RE, "[masqué]")

    /** Règle pure (testée) : une ligne de pile « at classe.méthode(Fichier.kt:12) » garde ses noms (longs mais sans secret) ;
     *  toute autre ligne (message d'erreur) est filtrée comme le reste. */
    fun cleanTrace(line: String): String {
        val frame = Regex("""^\s*at [\w$.<>\-]+\([\w$.\- ]*(?::\d+)?\)\s*$""")
        return if (frame.matches(line)) line else clean(line)
    }

    /** Règle pure (testée) : vrai si le texte contient encore un secret possible (garde-fou). */
    fun looksSecret(text: String): Boolean =
        URL_RE.containsMatchIn(text) || KV_RE.containsMatchIn(text.replace("=[masqué]", "")) || LONG_RE.containsMatchIn(text)

    /** Règle pure (testée) : l'adresse de la page du rapport sur le réseau de la maison. */
    fun pageUrl(ip: String?): String? =
        ip?.takeIf { Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(it) }?.let { "http://$it:${Lien666.PORT}$PATH" }

    fun build(context: Context, now: Long = System.currentTimeMillis()): String {
        val fr = Locale.FRANCE
        val date = SimpleDateFormat("EEEE d MMMM yyyy · HH:mm", fr).format(Date(now))
        val day = SimpleDateFormat("EEE d · HH:mm", fr)
        val out = StringBuilder()
        fun line(s: String = "") { out.append(clean(s)).append('\n') }

        line("RAPPORT POUR CLAUDE — FrankenStream ${runCatching { Maj666.myName(context) }.getOrDefault("?")}")
        // 23/11 : The Thing, le journal de MacReady au magnéto
        line(MACREADY)
        line("Shield du ${Net666.whereAmI()} (${Build.MODEL} · ${Build.DEVICE}) · Android ${Build.VERSION.RELEASE}")
        line(date)
        line()

        line("== Réglages ==")
        line("Toujours revenir : " + if (runCatching { com.nuvio.tv.core.wake.Return666.enabled(context) }.getOrDefault(false)) "activé" else "COUPÉ")
        line("Ouvrir sur l'Abattoir : " + if (runCatching { com.nuvio.tv.core.wake.WakeLaunch666.abattoirOnOpen(context) }.getOrDefault(false)) "activé" else "coupé")
        line("4 connexions en WiFi (appris) : " + if (runCatching { WifiParallel666.learned(context) }.getOrDefault(false)) "oui" else "non")
        line("Annonces par-dessus les autres applis : " + if (runCatching { Announce666.canOverlay(context) }.getOrDefault(false)) "autorisées" else "PAS autorisées")
        line()

        line("== Cavaliers ==")
        line("Ordre : " + PluginOrder666.order.joinToString(" → ") { PluginOrder666.labelOf(it) })
        val names = CavalierSlots666.names.value
        val empty = CavalierSlots666.emptySlots.value
        CavalierSlots666.SLOTS.forEach { s ->
            line("Case ${s.number} : " + when {
                s.scraperId in empty -> "libre"
                names[s.internal] != null -> names[s.internal]!!
                else -> "lien collé (nom pas encore lu)"
            })
        }
        val healthPrefs = context.getSharedPreferences("666tv_plugin_health", Context.MODE_PRIVATE)
        val healthAt = healthPrefs.getLong("at", 0L)
        line("Derniers tests" + if (healthAt > 0) " (${day.format(Date(healthAt))}) :" else " : jamais lancés")
        runCatching {
            val json = org.json.JSONObject(healthPrefs.getString("results", "{}") ?: "{}")
            json.keys().forEach { id ->
                val o = json.getJSONObject(id)
                val name = PluginOrder666.displayName(id, o.optString("name"))
                line("  ${if (o.optInt("links") > 0) "✓" else "✗"} $name : ${o.optInt("links")} lien(s) · ${o.optString("detail")}")
            }
        }
        line()

        // 24/11 : M6+ (jamais l'adresse e-mail ni le mot de passe : seulement « oui / non »)
        line("== M6+ ==")
        line("Compte M6+ enregistré : " + if (runCatching { com.nuvio.tv.core.m6.M6Plus666.hasAccount(context) }.getOrDefault(false)) "oui" else "non")
        val m6 = runCatching { com.nuvio.tv.core.m6.M6Plus666.problems(context) }.getOrDefault(emptyList())
        if (m6.isEmpty()) line("Erreurs : (aucune)")
        m6.takeLast(10).forEach { line("${day.format(Date(it.at))} · ${it.where} · ${it.msg}") }
        line()

        line("== Annonces (7 jours) ==")
        val annonces = runCatching { Announce666.entries(context, now) }.getOrDefault(emptyList()).take(40)
        if (annonces.isEmpty()) line("(aucune)")
        annonces.forEach { line("${day.format(Date(it.at))} · ${it.text} · ${it.where}") }
        line()

        line("== Blocages / plantages (où, dans le code) ==")
        val traces = runCatching { com.nuvio.tv.core.wake.ExitReason666.traces(context) }.getOrDefault("")
        if (traces.isBlank()) line("(aucun noté)") else traces.lines().forEach { out.append(cleanTrace(it)).append('\n') }
        line()

        line("== Coupures réseau / redémarrages ==")
        val net = runCatching { Net666.entries(context) }.getOrDefault(emptyList()).sortedByDescending { it.start }.take(25)
        if (net.isEmpty()) line("(aucune)")
        net.forEach { line(Net666.line(it, now)) }
        line()
        line("(Aucune clé, aucun token, aucun lien de config dans ce rapport.)")
        return out.toString()
    }

    /** La page HTML servie au téléphone : le texte + un bouton pour tout copier. */
    fun page(text: String): String {
        val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Rapport pour Claude</title>
<style>body{margin:0;background:#141214;color:#eee;font-family:system-ui,sans-serif;padding:16px}
h1{color:#78e66e;font-size:20px}button{background:#7a3fd9;color:#fff;border:0;border-radius:24px;padding:14px 22px;font-size:17px;width:100%}
textarea{width:100%;height:65vh;margin-top:12px;background:#1f1c21;color:#eee;border:1px solid #7a3fd9;border-radius:8px;font:13px monospace;padding:8px;box-sizing:border-box}
p{color:#bbb;font-size:14px}</style></head><body>
<h1>Rapport pour Claude</h1><p><i>Avant-poste 31 · journal de MacReady</i></p><p>Appuie sur le bouton, puis colle le texte dans la conversation avec Claude.</p>
<button onclick="var t=document.getElementById('r');t.focus();t.select();t.setSelectionRange(0,99999);var ok=false;try{ok=document.execCommand('copy')}catch(e){}if(navigator.clipboard&&!ok){navigator.clipboard.writeText(t.value)}this.textContent='Copié ✓'">Tout copier</button>
<textarea id="r" readonly>$esc</textarea></body></html>"""
    }
}
