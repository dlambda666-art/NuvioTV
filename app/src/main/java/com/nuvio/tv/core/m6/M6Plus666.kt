package com.nuvio.tv.core.m6

import android.content.Context
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 24/11 (David) : M6+ dans Frank (M6, W9, 6ter, Gulli en direct + le replay M6+), avec SON compte M6+.
 *
 * Même chemin que l'extension Kodi « Catch-up TV & More » (resources/lib/channels/fr/6play.py), qui marche
 * chez David sans VPN : connexion au compte (Gigya) → jeton (front-auth) → jeton de lecture (upfront-token)
 * → flux DASH protégé Widevine, licence drmtoday (en-tête x-dt-auth-token). Lecture officielle, rien de
 * contourné.
 *
 * SÉCURITÉ : l'identifiant et le mot de passe M6+ sont tapés par David dans Frank et restent sur la Shield
 * (prefs privées « 666tv_m6plus ») : jamais dans le Rapport, jamais envoyés ailleurs qu'à M6.
 */
object M6Plus666 {

    // ---------------------------------------------------------------- règles pures (testées)

    /** Les chaînes en direct (comme l'extension Kodi : pas les radios, Frank = pas de musique). */
    data class Channel(val label: String, val liveId: String, val subtitle: String)

    val CHANNELS = listOf(
        Channel("M6", "M6", "En direct"),
        Channel("W9", "W9", "En direct"),
        Channel("6ter", "6T", "En direct"),
        Channel("Gulli", "gulli", "En direct")
    )

    /** La réponse de connexion arrive en « jsonp » : on retire l'enveloppe jsonp_xxx( … ); */
    fun stripJsonp(text: String): String {
        val t = text.trim()
        val open = t.indexOf('(')
        val close = t.lastIndexOf(')')
        return if (t.startsWith("{") || open < 0 || close <= open) t else t.substring(open + 1, close)
    }

    data class Asset(val type: String, val quality: String, val url: String)

    /** Le meilleur flux du type voulu : HD avant SD (jamais de choix au hasard). null si aucun. */
    fun pickAsset(assets: List<Asset>, type: String): Asset? =
        assets.filter { it.type == type && it.url.isNotBlank() }
            .maxByOrNull { if (it.quality.equals("hd", true)) 2 else if (it.quality.equals("sd", true)) 1 else 0 }

    /** drmtoday répond {"license":"<base64>"} : on en sort le base64 (null si la réponse n'est pas ce JSON). */
    fun licenseBase64(response: String): String? {
        val t = response.trim()
        if (!t.startsWith("{")) return null
        return runCatching { JSONObject(t).optString("license").takeIf { it.isNotBlank() } }.getOrNull()
    }

    /** Message clair pour David quand M6 refuse (sans jamais afficher l'identifiant ni les jetons). */
    fun explain(code: Int): String = when (code) {
        401, 403 -> "M6+ refuse la lecture : vérifie ton compte (ou ce programme demande un abonnement M6+ Max)"
        404 -> "Cette vidéo n'est plus disponible sur M6+"
        451 -> "M6+ n'autorise pas cette vidéo depuis ta connexion"
        in 500..599 -> "M6+ ne répond pas pour le moment, réessaie dans un instant"
        else -> "M6+ : erreur $code"
    }

    // ---------------------------------------------------------------- compte (reste sur la Shield)

    private const val PREFS = "666tv_m6plus"

    fun hasAccount(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !p.getString("login", null).isNullOrBlank() && !p.getString("password", null).isNullOrBlank()
    }

    fun accountLabel(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("login", null)?.takeIf { it.isNotBlank() }

    fun saveAccount(context: Context, login: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("login", login.trim()).putString("password", password).apply()
        session = null
    }

    fun forgetAccount(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("login").remove("password").apply()
        session = null
    }

    /** Pour le Rapport : les dernières erreurs M6+ (sans adresse e-mail, sans jeton, sans lien complet). */
    fun safeMessage(msg: String?): String =
        (msg ?: "erreur inconnue").replace(Regex("https?://[^\\s?]*\\?\\S*")) { it.value.substringBefore('?') + "?…" }
            .replace(Regex("[A-Za-z0-9_\\-]{40,}"), "…").take(600)

    fun noteError(context: Context, where: String, msg: String?) {
        runCatching {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = runCatching { JSONArray(p.getString("errors", "[]")) }.getOrDefault(JSONArray())
            arr.put(JSONObject().put("at", System.currentTimeMillis()).put("where", where).put("msg", safeMessage(msg)))
            val keep = JSONArray(); val from = maxOf(0, arr.length() - 10)
            for (i in from until arr.length()) keep.put(arr.get(i))
            p.edit().putString("errors", keep.toString()).apply()
        }
    }

    data class Problem(val at: Long, val where: String, val msg: String)

    /**
     * 25/11 (piège M6+ 2.0.10 : écran blanc puis cinéma, Rapport pas rafraîchi) : si l'écran M6+ plante, la
     * cause est gardée ici et affichée EN CLAIR à la prochaine ouverture de M6+ (une photo suffit).
     * Jamais d'e-mail ni de jeton dedans (safeMessage).
     */
    fun crashText(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        val frames = (root.stackTrace.take(6) + e.stackTrace.filter { it.className.startsWith("com.nuvio") }.take(4))
            .distinct().joinToString(" ← ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        return safeMessage("${root.javaClass.simpleName}: ${root.message} | $frames").take(600)
    }

    fun saveCrash(context: Context, e: Throwable) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("crash", crashText(e)).putLong("crash_at", System.currentTimeMillis()).commit()
        }
        noteError(context, "plantage de l'écran M6+", crashText(e))
    }

    /** 25/11 : sur les Shield de David, la lecture ne marche qu'en Widevine logiciel (L3) : on le retient. */
    fun prefersL3(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("l3", false)

    fun rememberL3(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("l3", true).apply()
    }

    fun lastCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("crash", null)

    fun clearCrash(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("crash").remove("crash_at").apply()
    }

    fun problems(context: Context): List<Problem> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("errors", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Problem(o.optLong("at"), o.optString("where"), o.optString("msg")) } }
    }.getOrDefault(emptyList())

    private fun deviceId(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString("device", null)?.let { return it }
        val id = "_luid_" + UUID.randomUUID().toString()
        p.edit().putString("device", id).apply()
        return id
    }

    // ---------------------------------------------------------------- réseau

    class M6Exception(message: String) : Exception(message)

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val API_KEY = "3_hH5KBv25qZTd_sURpixbQW6a4OsiIzIEF2Ei_2H7TXTGLJb_1Hr4THKZianCQhWK"
    private const val MW = "https://android.middleware.6play.fr/6play/v2/platforms/m6group_androidmob/services/6play"
    const val LICENSE_URL = "https://lic.drmtoday.com/license-proxy-widevine/cenc/"

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }
    val noRedirect: OkHttpClient by lazy { http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    private fun get(url: String, headers: Map<String, String> = emptyMap(), client: OkHttpClient = http): String {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw M6Exception(explain(r.code))
            return r.body?.string().orEmpty()
        }
    }

    private data class Session(val uid: String, val jwt: String, val at: Long)
    @Volatile private var session: Session? = null

    /** Connexion au compte M6+ (gardée 20 min, comme une session normale). */
    @Synchronized
    private fun login(context: Context): Session {
        session?.let { if (System.currentTimeMillis() - it.at < 20 * 60_000L) return it }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val login = p.getString("login", null)
        val password = p.getString("password", null)
        if (login.isNullOrBlank() || password.isNullOrBlank()) throw M6Exception("Entre ton compte M6+ (bouton « Mon compte M6+ »)")
        val form = FormBody.Builder()
            .add("loginID", login).add("password", password).add("apiKey", API_KEY)
            .add("format", "jsonp").add("callback", "jsonp_3bbusffr388pem4").build()
        val req = Request.Builder().url("https://login-gigya.m6.fr/accounts.login").post(form)
            .header("User-Agent", UA).header("referer", "https://www.6play.fr/connexion").build()
        val body = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val j = runCatching { JSONObject(stripJsonp(body)) }.getOrNull()
        val uid = j?.optString("UID").orEmpty()
        if (uid.isBlank()) throw M6Exception("M6+ refuse la connexion : vérifie ton adresse e-mail et ton mot de passe")
        val jwtBody = get(
            "https://front-auth.6cloud.fr/v2/platforms/m6group_web/getJwt",
            mapOf(
                "x-auth-gigya-signature" to j!!.optString("UIDSignature"),
                "x-auth-gigya-signature-timestamp" to j.opt("signatureTimestamp").toString(),
                "x-auth-gigya-uid" to uid,
                "x-auth-device-id" to deviceId(context),
                "x-customer-name" to "m6web"
            )
        )
        val jwt = JSONObject(jwtBody).optString("token")
        if (jwt.isBlank()) throw M6Exception("M6+ n'a pas ouvert la session, réessaie")
        return Session(uid, jwt, System.currentTimeMillis()).also { session = it }
    }

    private fun upfrontToken(context: Context, path: (String) -> String): String {
        val s = login(context)
        val body = get(
            path(s.uid),
            mapOf("X-Customer-Name" to "m6web", "X-Client-Release" to "5.103.3", "Authorization" to "Bearer " + s.jwt)
        )
        return JSONObject(body).optString("token").ifBlank { throw M6Exception("M6+ n'a pas donné l'autorisation de lecture") }
    }

    private fun assets(arr: JSONArray?): List<Asset> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            Asset(a.optString("type"), a.optString("video_quality"), a.optString("full_physical_path"))
        }
    }

    /** Ce qu'il faut au lecteur : le flux DASH + le jeton de licence. */
    data class Playable(val title: String, val manifest: String, val licenseToken: String)

    fun liveStream(context: Context, channel: Channel): Playable {
        val token = upfrontToken(context) { uid ->
            "https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web/services/6play/users/$uid/live/dashcenc_${channel.liveId}/upfront-token"
        }
        val body = get("$MW/live?channel=${channel.liveId}&with=service_display_images,nextdiffusion,extra_data")
        val live = JSONObject(body).optJSONArray(channel.liveId)?.optJSONObject(0)?.optJSONObject("live")
        val asset = pickAsset(assets(live?.optJSONArray("assets")), "delta_dashcenc_h264")
            ?: throw M6Exception("Le direct de ${channel.label} n'est pas disponible pour le moment")
        return Playable(channel.label, asset.url, token)
    }

    fun replayStream(context: Context, videoId: String, title: String): Playable {
        val token = upfrontToken(context) { uid ->
            "https://drm.6cloud.fr/v1/customers/m6web/platforms/m6group_web/services/m6replay/users/$uid/videos/$videoId/upfront-token"
        }
        val body = get("$MW/videos/$videoId?csa=6&with=clips,freemiumpacks", mapOf("x-customer-name" to "m6web"))
        val clip = JSONObject(body).optJSONArray("clips")?.optJSONObject(0)
        val asset = pickAsset(assets(clip?.optJSONArray("assets")), "usp_dashcenc_h264")
            ?: throw M6Exception("Cette vidéo n'est pas lisible ici")
        // comme l'extension : l'adresse finale est donnée par une redirection
        val finalUrl = runCatching {
            noRedirect.newCall(Request.Builder().url(asset.url).header("User-Agent", UA).build()).execute()
                .use { it.header("location") }
        }.getOrNull()?.takeIf { it.startsWith("http") } ?: asset.url
        return Playable(title, finalUrl, token)
    }

    /** Appel de licence Widevine (utilisé par le lecteur). */
    fun license(challenge: ByteArray, token: String): String {
        val req = Request.Builder().url(LICENSE_URL)
            .post(challenge.toRequestBody("application/octet-stream".toMediaType()))
            .header("User-Agent", UA).header("x-dt-auth-token", token).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw M6Exception(explain(r.code))
            return r.body?.string().orEmpty()
        }
    }

    // ---------------------------------------------------------------- catalogue

    data class Program(val id: String, val title: String, val image: String?, val description: String = "")
    data class Folder(val id: String?, val title: String)
    data class Video(val id: String, val title: String, val image: String?, val description: String, val durationS: Int)

    private fun image(obj: JSONObject?): String? {
        val imgs = obj?.optJSONArray("images") ?: return null
        for (i in 0 until imgs.length()) {
            val im = imgs.optJSONObject(i) ?: continue
            val role = im.optString("role")
            if (role == "vignette" || role == "carousel") {
                val key = im.optString("external_key")
                if (key.isNotBlank()) return "https://images.6play.fr/v1/images/$key/raw"
            }
        }
        return null
    }

    fun search(query: String): List<Program> {
        val payload = JSONObject().put(
            "requests", JSONArray().put(
                JSONObject()
                    .put("indexName", "rtlmutu_prod_bedrock_layout_items_v2_m6web_main")
                    .put("query", query)
                    .put("params", "clickAnalytics=true&hitsPerPage=50&facetFilters=[[\"metadata.item_type:program\"], [\"metadata.platforms_assets:m6group_web\"]]")
            )
        )
        val req = Request.Builder()
            .url("https://nhacvivxxk-dsn.algolia.net/1/indexes/*/queries?x-algolia-agent=Algolia%20for%20JavaScript%20(4.24.0)%3B%20Browser")
            .post(payload.toString().toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .header("User-Agent", UA)
            .header("x-algolia-api-key", "6ef59fc6d78ac129339ab9c35edd41fa")
            .header("x-algolia-application-id", "NHACVIVXXK").build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw M6Exception(explain(r.code)); r.body?.string().orEmpty()
        }
        val out = mutableListOf<Program>()
        val results = JSONObject(body).optJSONArray("results") ?: return out
        for (i in 0 until results.length()) {
            val hits = results.optJSONObject(i)?.optJSONArray("hits") ?: continue
            for (h in 0 until hits.length()) {
                val hit = hits.optJSONObject(h) ?: continue
                val id = hit.optJSONObject("content")?.opt("id")?.toString() ?: continue
                val content = hit.optJSONObject("item")?.optJSONObject("itemContent")
                val title = content?.optString("title").orEmpty()
                val img = content?.optJSONObject("image")?.optString("external_key")?.takeIf { it.isNotBlank() }
                    ?.let { "https://images.6play.fr/v1/images/$it/raw" }
                if (title.isNotBlank() && out.none { it.id == id }) out += Program(id, title, img)
            }
        }
        return out
    }

    /** Un programme : ses dossiers (Saison 1, Saison 2…) ; « Toutes les vidéos » en dernier, comme l'extension. */
    fun folders(programId: String): Pair<Program, List<Folder>> {
        val j = JSONObject(get("$MW/programs/$programId?with=links,subcats,rights"))
        val program = Program(programId, j.optString("title"), image(j), j.optString("description"))
        val subs = j.optJSONArray("program_subcats")
        val list = mutableListOf<Folder>()
        if (subs != null) for (i in 0 until subs.length()) {
            val s = subs.optJSONObject(i) ?: continue
            list += Folder(s.opt("id")?.toString(), s.optString("title"))
        }
        list += Folder(null, "Toutes les vidéos")
        return program to list
    }

    fun videos(programId: String, folderId: String?): List<Video> {
        val url = if (folderId == null)
            "$MW/programs/$programId/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=999&offset=0"
        else
            "$MW/programs/$programId/videos?csa=6&with=clips,freemiumpacks&type=vi,vc,playlist&limit=999&offset=0&subcat=$folderId&sort=subcat"
        val arr = JSONArray(get(url))
        val out = mutableListOf<Video>()
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            if (v.optString("type") == "playlist") continue
            val clip = v.optJSONArray("clips")?.optJSONObject(0)
            out += Video(
                v.opt("id")?.toString() ?: continue,
                v.optString("title"),
                image(clip) ?: image(v),
                clip?.optString("description").orEmpty(),
                clip?.optInt("duration") ?: 0
            )
        }
        return out.sortedWith(compareBy { naturalKey(it.title) })
    }

    /** Épisode 2 avant Épisode 10 (comme l'extension). */
    fun naturalKey(title: String): String =
        Regex("\\d+").replace(title.lowercase()) { it.value.padStart(6, '0') }
}
