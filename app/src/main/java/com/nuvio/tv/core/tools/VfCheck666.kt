package com.nuvio.tv.core.tools

import android.content.Context
import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.time.LocalDate

/**
 * 666TVF (09/11) : le cerveau VF de Frank, sur la Shield (Frank a été mis en pause par Hugging Face).
 * Mêmes règles que Frank, dans le même ordre :
 *  1. pas encore sorti → pas de VF ;
 *  2. en abonnement / gratuit sur une grande plateforme FR ou BE qui double (Netflix, Prime, Disney+,
 *     Canal+, Max, Apple TV, Paramount+, TF1, M6, France TV, Arte, Auvio…) → VF ; les chaînes de
 *     niche (Shadowz, Insomnia…, souvent VOSTFR) ne comptent pas, la location / l'achat non plus ;
 *  3. sinon la fiche French-Stream : VF / VOSTFR, et un film récent en version cinéma (CAM, TS, MD…)
 *     ne compte pas ;
 *  4. une piste française entendue à la lecture, ou une VF trouvée par le guetteur → VF.
 * Les listes « VF » n'affichent que ce qui est confirmé. Verdicts gardés sur la Shield.
 */
object VfCheck666 {
    /**
     * 11/11 : TOUTES les listes TMDB des tuiles passent au tri VF (Shudder mettait *Cycle*, en CAM et
     * sans VF). Seules les listes « à venir » (sorties après aujourd'hui) y échappent : rien n'y est
     * encore sorti, elles seraient vides.
     */
    fun filtersList(f: com.nuvio.tv.domain.model.TmdbCollectionFilters, today: String): Boolean {
        val gte = f.releaseDateGte?.takeIf { it.length >= 10 }?.take(10) ?: return true
        return gte <= today
    }

    private const val TAG = "VfCheck666"
    private const val PREFS = "666tv_vfcheck"
    /**
     * Adresses de French-Stream (le site en change souvent). La 1re qui répond est retenue ;
     * si elle tombe, on essaie les autres. Nouvelle adresse = l'ajouter ici (09/11 : fs23.lol).
     */
    val FS_HOSTS = listOf("https://fs23.lol", "https://maj.french-stream.pink")
    @Volatile private var fsIndex = 0
    private val FS: String get() = FS_HOSTS[fsIndex % FS_HOSTS.size]

    enum class Tag { DUB, DUB_SUB, SUB, NONE }
    fun isVf(t: Tag?) = t == Tag.DUB || t == Tag.DUB_SUB

    @Volatile private var ctx: Context? = null
    fun init(context: Context) { if (ctx == null) ctx = context.applicationContext }
    private fun prefs() = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Tag?>>()
    private val gate = Semaphore(4)
    private val fsPace = Mutex()
    @Volatile private var fsNextAt = 0L
    @Volatile private var fsBlockedUntil = 0L

    // ---------- règles pures (testées) ----------

    private val DUBBING_PROVIDERS = Regex("""netflix|prime video|disney|canal|apple tv|^max\b|hbo|paramount|tf1|^m6|france ?tv|france\.tv|arte|auvio|rtbf|vtm|streamz|salto""", RegexOption.IGNORE_CASE)
    private val NICHE_CHANNEL = Regex("""channel|chaine|chaîne""", RegexOption.IGNORE_CASE)
    val CAM_RELEASE = Regex("""\b(?:HD[ ._-]?)?CAM(?:[ ._-]?RIP)?\b|\b(?:HD[ ._-]?)?TS\b|\bTELE[ ._-]?SYNC\b|\b(?:HD[ ._-]?)?TC\b|\bTELE[ ._-]?CINE\b|\bMD\b|\bMICRO[ ._-]?DUBBED\b""", RegexOption.IGNORE_CASE)

    /** Plateforme FR/BE qui double (abonnement, gratuit, pub) : la règle 2 de Frank. */
    fun onDubbingPlatform(providersResults: JSONObject?): Boolean {
        if (providersResults == null) return false
        for (region in listOf("FR", "BE")) {
            val r = providersResults.optJSONObject(region) ?: continue
            for (kind in listOf("flatrate", "free", "ads")) {
                val arr = r.optJSONArray(kind) ?: continue
                for (i in 0 until arr.length()) {
                    val name = arr.optJSONObject(i)?.optString("provider_name").orEmpty()
                    if (DUBBING_PROVIDERS.containsMatchIn(name) && !NICHE_CHANNEL.containsMatchIn(name)) return true
                }
            }
        }
        return false
    }

    fun languageTag(text: String): Tag {
        val t = text.uppercase()
        val dub = Regex("""\bVF\b|\bFRENCH\b|\bTRUEFRENCH\b|\bDUB\b""").containsMatchIn(t)
        val sub = Regex("""\bVOSTFR\b|\bSUB\b""").containsMatchIn(t)
        return when { dub && sub -> Tag.DUB_SUB; dub -> Tag.DUB; sub -> Tag.SUB; else -> Tag.NONE }
    }

    fun norm(s: String?): String = Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun canon(s: String?) = norm(s).split(' ').filter { it.isNotBlank() && it != "1" }.joinToString(" ")

    data class FsItem(val title: String, val href: String, val series: Boolean, val tag: Tag, val year: Int?)

    /** Résultats de la recherche French-Stream (HTML de search.php), règle pure testée. */
    fun parseSearch(html: String): List<FsItem> {
        val out = ArrayList<FsItem>()
        val blocks = html.split(Regex("""<div class=['"]search-item""")).drop(1)
        for (b in blocks) {
            val series = b.startsWith(" search-series")
            val rawTitle = Regex("""class=['"]search-title['"]>([^<]*)<""").find(b)?.groupValues?.get(1)?.trim().orEmpty()
                .replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"")
            if (rawTitle.isBlank()) continue
            val href = Regex("""location\.href=['"]([^'"]+)['"]""").find(b)?.groupValues?.get(1)
                ?: Regex("""&quot;l&quot;:&quot;(.*?)&quot;""").find(b)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: continue
            val year = Regex("""\((19|20)\d{2}\)""").find(rawTitle)?.value?.substring(1, 5)?.toIntOrNull()
            val title = rawTitle.replace(Regex("""\s*-\s*s[ée]rie\s*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\(?\d{4}\)?"""), "").replace(Regex("""[\s\-–]+(Saison|S|Season)\s*\d+""", RegexOption.IGNORE_CASE), "").trim()
            val page = href.substringBefore('?').substringAfterLast('/').removeSuffix(".html")
            val isSeries = series || Regex("saison|season|/s-tv/", RegexOption.IGNORE_CASE).containsMatchIn("$rawTitle $href")
            out += FsItem(title, href, isSeries, languageTag("$rawTitle $page"), year)
        }
        return out
    }

    /** Fusion des étiquettes de plusieurs fiches du même titre (comme Frank). */
    fun merge(tags: List<Tag>): Tag {
        val dub = tags.any { it == Tag.DUB || it == Tag.DUB_SUB }
        val sub = tags.any { it == Tag.SUB || it == Tag.DUB_SUB }
        return when { dub && sub -> Tag.DUB_SUB; dub -> Tag.DUB; sub -> Tag.SUB; else -> Tag.NONE }
    }

    // ---------- cache ----------

    private fun key(id: String) = id.lowercase()

    fun known(id: String): Tag? {
        val raw = prefs()?.getString(key(id), null) ?: return null
        val tag = runCatching { Tag.valueOf(raw.substringBefore('|')) }.getOrNull() ?: return null
        val at = raw.substringAfter('|', "0").toLongOrNull() ?: 0L
        // une VF ne disparaît pas ; « pas de VF » est revérifié (3 jours si récent, sinon 30)
        if (!isVf(tag)) {
            val ttl = if (raw.contains("|r")) 3L else 30L
            if (System.currentTimeMillis() - at > ttl * 24 * 3600 * 1000) return null
        }
        return tag
    }

    private fun store(ids: List<String>, tag: Tag, recent: Boolean) {
        val p = prefs() ?: return
        val v = "${tag.name}|${System.currentTimeMillis()}" + if (recent) "|r" else ""
        p.edit().apply { ids.filter { it.isNotBlank() }.forEach { putString(key(it), v) } }.apply()
    }

    /** Identifiant IMDb connu d'un titre (tmdb:N → tt…), pour les affiches et le badge VF. */
    fun imdbFor(id: String?): String? = when {
        id == null -> null
        id.startsWith("tt") -> id.substringBefore(':')
        id.startsWith("tmdb:") -> prefs()?.getString("imdb:$id", null)
        else -> null
    }

    /** Vrai si ce titre est confirmé en VF (badge « VF » sur l'affiche). Lecture du cache seulement. */
    fun isKnownVf(id: String?, imdbId: String? = null): Boolean =
        listOfNotNull(imdbId, id?.substringBefore(":")?.takeIf { it.startsWith("tt") }, id?.takeIf { it.startsWith("tmdb:") })
            .any { runCatching { isVf(known(it)) }.getOrDefault(false) }

    // ---------- dernier recours : les cavaliers ----------

    private fun enqueueStreamCheck(imdb: String, type: String) {
        val p = prefs() ?: return
        synchronized(this) {
            val q = p.getStringSet("stream_queue", emptySet()).orEmpty()
            if (q.size < 300) p.edit().putStringSet("stream_queue", q + "$imdb|$type").apply()
        }
    }

    /** Titres à faire vérifier par les cavaliers (imdb, type), au plus [max]. */
    fun pendingStreamChecks(max: Int): List<Pair<String, String>> =
        prefs()?.getStringSet("stream_queue", emptySet()).orEmpty().take(max)
            .map { it.substringBefore('|') to it.substringAfter('|', "movie") }

    fun streamChecked(imdb: String, type: String) {
        val p = prefs() ?: return
        synchronized(this) {
            p.edit().putStringSet("stream_queue", p.getStringSet("stream_queue", emptySet()).orEmpty() - "$imdb|$type").apply()
        }
    }

    /** Preuve directe (piste française entendue, guetteur, cloche) : VF pour de bon. */
    /** 666TVF (13/11) : oublie le verdict (faux cri retiré) : le titre sera revérifié normalement. */
    fun forget(id: String?) {
        val i = id?.substringBefore(':')?.takeIf { it.startsWith("tt") } ?: return
        prefs()?.edit()?.remove(key(i))?.apply()
    }

    fun confirm(id: String?) {
        val i = id?.substringBefore(':')?.takeIf { it.startsWith("tt") } ?: return
        store(listOf(i), Tag.DUB, false)
    }

    // ---------- vérification ----------

    private suspend fun tmdb(path: String): JSONObject? {
        val key = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return null
        val sep = if ('?' in path) '&' else '?'
        val raw = Http666.get("https://api.themoviedb.org/3$path${sep}api_key=$key", 15_000) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    /** Verdict (en cache, sinon vérifié maintenant). Null = pas de réponse (on réessaiera). */
    suspend fun check(id: String, type: String): Tag? {
        known(id)?.let { return it }
        val d = running.computeIfAbsent(key(id)) {
            scope.async { gate.withPermit { runCatching { detect(id, type) }.onFailure { Log.w(TAG, "vérif : ${it.message}") }.getOrNull() } }
        }
        return try { d.await() } finally { running.remove(key(id)) }
    }

    private suspend fun detect(id: String, type: String): Tag? {
        val series = type == "series" || type == "tv"
        val kind = if (series) "tv" else "movie"
        val tmdbId: String = when {
            id.startsWith("tmdb:") -> id.removePrefix("tmdb:")
            id.startsWith("tt") -> {
                val f = tmdb("/find/${id.substringBefore(':')}?external_source=imdb_id") ?: return null
                (f.optJSONArray(if (series) "tv_results" else "movie_results")?.optJSONObject(0)?.optInt("id") ?: 0)
                    .takeIf { it > 0 }?.toString() ?: run { store(listOf(id), Tag.NONE, false); return Tag.NONE }
            }
            else -> return null
        }
        val d = tmdb("/$kind/$tmdbId?language=fr-FR&append_to_response=external_ids,watch/providers") ?: return null
        val imdb = d.optJSONObject("external_ids")?.optString("imdb_id").orEmpty().ifBlank { d.optString("imdb_id") }
        val ids = listOf(id, "tmdb:$tmdbId", imdb).distinct()
        if (imdb.startsWith("tt")) prefs()?.edit()?.putString("imdb:tmdb:$tmdbId", imdb)?.apply()
        val date = d.optString(if (series) "first_air_date" else "release_date")
        val year = date.take(4).toIntOrNull()
        val recent = year != null && year >= LocalDate.now().year - 1
        // 1. pas encore sorti
        if (date.isBlank() || date > LocalDate.now().plusDays(1).toString()) { store(ids, Tag.NONE, true); return Tag.NONE }
        // 2. grande plateforme qui double
        if (onDubbingPlatform(d.optJSONObject("watch/providers")?.optJSONObject("results"))) { store(ids, Tag.DUB, recent); return Tag.DUB }
        // 3. French-Stream
        val titles = listOf(d.optString("title").ifBlank { d.optString("name") }, d.optString("original_title").ifBlank { d.optString("original_name") })
            .filter { it.isNotBlank() }.distinctBy { canon(it) }
        val tag = frenchStream(titles, series, year, recent) ?: return null
        store(ids, tag, recent)
        // 4. dernier recours de Frank : tes sources de liens, en douce (le 1er cavalier, quand tu ne regardes rien)
        if (!isVf(tag) && imdb.startsWith("tt")) enqueueStreamCheck(imdb, if (series) "series" else "movie")
        return tag
    }

    /** [path] commence par « / ». Essaie l'adresse retenue, puis les autres. */
    private suspend fun fsRequest(path: String, body: String?): String? {
        if (System.currentTimeMillis() < fsBlockedUntil) return null
        repeat(FS_HOSTS.size) {
            val base = FS
            fsPace.withLock {
                val wait = fsNextAt - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                fsNextAt = System.currentTimeMillis() + 1_200
            }
            val text = runCatching {
                val c = URL(base + path).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                c.setRequestProperty("Referer", "$base/")
                if (body != null) {
                    c.requestMethod = "POST"; c.doOutput = true
                    c.setRequestProperty("Origin", base)
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    c.outputStream.use { it.write(body.toByteArray()) }
                }
                try {
                    if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().readText()
                } finally { c.disconnect() }
            }.getOrNull()
            if (text != null) {
                // French-Stream bloque : on ne prend pas un blocage pour un « pas de VF », on attend
                if (Regex("just a moment|cf-chl|captcha|too many requests", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                    fsBlockedUntil = System.currentTimeMillis() + 15 * 60_000L
                    return null
                }
                fsUp()
                return text
            }
            fsIndex++   // cette adresse ne répond pas : la suivante
        }
        fsAllDown()
        return null
    }

    // ---------- Mémoire partagée (11/11) ----------

    /** Les titres confirmés en VF (et les correspondances TMDB → IMDb), pour l'autre Shield / la clé. */
    fun exportVf(): JSONObject {
        val out = JSONObject()
        prefs()?.all.orEmpty().forEach { (k, v) ->
            val sv = v as? String ?: return@forEach
            if ((k.startsWith("tt") || k.startsWith("tmdb:")) && sv.startsWith("DUB")) out.put(k, sv)
            else if (k.startsWith("imdb:tmdb:")) out.put(k, sv)
        }
        return out
    }

    /** Fusionne : une VF confirmée ailleurs l'est ici aussi. Renvoie le nombre de nouveaux titres VF. */
    fun importVf(json: JSONObject): Int {
        val p = prefs() ?: return 0
        var n = 0
        val e = p.edit()
        json.keys().forEach { k ->
            val v = json.optString(k)
            if (k.startsWith("imdb:tmdb:")) { if (!p.contains(k)) e.putString(k, v) }
            else if (v.startsWith("DUB") && p.getString(k, null)?.startsWith("DUB") != true) { e.putString(k, v); n++ }
        }
        e.apply()
        return n
    }

    /** Ligne « Moteur VF » : jours depuis la dernière nouveauté lue (règle pure testée : orange à 2 jours). */
    fun newsStale(lastNewsAt: Long, now: Long): Boolean = lastNewsAt > 0 && now - lastNewsAt > 2 * 24 * 3600_000L

    // ---------- Nouveautés French-Stream (11/11) ----------

    data class FsNews(val title: String, val series: Boolean, val vf: Boolean)

    /** Flux RSS des nouveautés French-Stream, règle pure testée : « Film en VF » / « Serie Episode VF ». */
    fun parseRss(xml: String): List<FsNews> =
        xml.split("<item").drop(1).mapNotNull { item ->
            val raw = Regex("""<title>(?:<!\[CDATA\[)?(.*?)(?:]]>)?</title>""", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1)
                ?.replace("&amp;", "&")?.replace("&#039;", "'")?.replace("&quot;", "\"")?.trim().orEmpty()
            if (raw.isBlank()) return@mapNotNull null
            val cats = Regex("""<category>(?:<!\[CDATA\[)?(.*?)(?:]]>)?</category>""", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1).orEmpty()
            val series = Regex("""(?i)saison\s*\d+|s[ée]rie""").containsMatchIn("$raw $cats")
            val title = raw.replace(Regex("""(?i)\s*[-–]\s*saison\s*\d+.*$"""), "").trim()
            val vf = Regex("""(?i)\bFilm en VF\b|\bEpisode VF\b""").containsMatchIn(cats)
            FsNews(title, series, vf)
        }

    /** Les derniers ajouts EN VF de French-Stream (films puis séries). Vide si le site se tait. */
    suspend fun fsNews(): List<FsNews> =
        listOf("films", "s-tv").flatMap { cat ->
            fsRequest("/engine/rss.php?do=cat&category=$cat", null)?.let { parseRss(it) }.orEmpty()
        }.filter { it.vf }.distinct().also { if (it.isNotEmpty()) prefs()?.edit()?.putLong("fs_news_at", System.currentTimeMillis())?.apply() }

    /**
     * Règle pure (testée) : le bon titre TMDB pour un nom vu sur French-Stream (titre exact, français ou
     * original). Films : sortis depuis [minYear] (des nouveautés, pas un vieux film du même nom).
     */
    fun pickTmdb(results: org.json.JSONArray?, title: String, series: Boolean, minYear: Int, today: String): Int? {
        if (results == null) return null
        val want = canon(title)
        var best: Pair<Int, String>? = null
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val names = listOf("title", "name", "original_title", "original_name").map { canon(o.optString(it)) }
            if (want !in names) continue
            val date = o.optString(if (series) "first_air_date" else "release_date")
            if (date.isBlank() || date > today) continue
            if (!series && (date.take(4).toIntOrNull() ?: 0) < minYear) continue
            if (best == null || date > best.second) best = o.optInt("id") to date
        }
        return best?.first?.takeIf { it > 0 }
    }

    /**
     * 11/11 : la ligne « Moteur VF » d'Outils 666. Interroge French-Stream (son flux de nouveautés) et
     * résume ce que la Shield sait. (texte, état : vrai = ok, null = à surveiller, faux = en panne)
     */
    suspend fun engineStatus(): Pair<String, Boolean?> {
        val news = runCatching { fsNews() }.getOrDefault(emptyList())
        val all = prefs()?.all.orEmpty()
        val vf = all.count { (k, v) -> (k.startsWith("tt") || k.startsWith("tmdb:")) && (v as? String)?.startsWith("DUB") == true }
        val queue = prefs()?.getStringSet("stream_queue", emptySet()).orEmpty().size
        val host = FS.removePrefix("https://")
        val known = "$vf titres VF connus · $queue en attente des cavaliers"
        return when {
            System.currentTimeMillis() < fsBlockedUntil -> "French-Stream ($host) bloque pour l'instant (15 min) · $known" to null
            news.isNotEmpty() -> "French-Stream ($host) · ${news.size} nouveautés VF · $known" to true
            newsStale(prefs()?.getLong("fs_news_at", 0L) ?: 0L, System.currentTimeMillis()) ->
                "French-Stream répond mais n'annonce plus aucune nouveauté depuis 2 jours (page changée ?) · $known" to null
            else -> "French-Stream ne répond pas (${FS_HOSTS.joinToString { it.removePrefix("https://") }}) · $known" to false
        }
    }

    // ---------- French-Stream muet : la cloche (11/11) ----------

    private fun fsAllDown(now: Long = System.currentTimeMillis()) {
        val p = prefs() ?: return
        if (p.getLong("fs_down_since", 0L) == 0L) p.edit().putLong("fs_down_since", now).apply()
    }

    private fun fsUp() {
        val p = prefs() ?: return
        if (p.getLong("fs_down_since", 0L) != 0L || p.getBoolean("fs_down_alerted", false))
            p.edit().remove("fs_down_since").remove("fs_down_alerted").apply()
    }

    /** Règle pure (testée) : aucune adresse ne répond depuis 3 h → on sonne, une fois par panne. */
    fun fsDownShouldAlert(since: Long, alerted: Boolean, now: Long): Boolean =
        since > 0 && !alerted && now - since >= 3 * 3600_000L

    /** Vrai une seule fois par panne : toutes les adresses French-Stream se taisent. */
    fun takeFsDownAlert(now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs() ?: return false
        if (!fsDownShouldAlert(p.getLong("fs_down_since", 0L), p.getBoolean("fs_down_alerted", false), now)) return false
        p.edit().putBoolean("fs_down_alerted", true).apply()
        return true
    }

    private suspend fun frenchStream(titles: List<String>, series: Boolean, year: Int?, recent: Boolean): Tag? {
        for (title in titles) {
            val html = fsRequest("/engine/ajax/search.php", "query=${URLEncoder.encode(title, "UTF-8")}&page=1") ?: return null
            val matches = parseSearch(html).filter { it.series == series && canon(it.title) == canon(title) &&
                (series || year == null || it.year == null || kotlin.math.abs(it.year - year) <= 1) }
            if (matches.isEmpty()) continue
            var tag = merge(matches.map { it.tag })
            if (tag == Tag.NONE) {
                val page = fsRequest(matches.first().href.let { if (it.startsWith("/")) it else "/$it" }, null) ?: return null
                val field = Regex("""id=["']film_lang["'][^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE).find(page)?.groupValues?.get(1)
                tag = if (field == null) Tag.NONE else languageTag(field.replace(Regex("<[^>]+>"), " "))
            }
            // film récent en VF : pas une version cinéma (CAM, TS, MD…)
            if (isVf(tag) && !series && recent) {
                val page = fsRequest(matches.first().href.let { if (it.startsWith("/")) it else "/$it" }, null)
                val quality = page?.let { Regex("""id=["']film_quality["'][^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
                    ?.replace(Regex("<[^>]+>"), " ")?.trim().orEmpty()
                if (CAM_RELEASE.containsMatchIn(quality)) return if (tag == Tag.DUB_SUB) Tag.SUB else Tag.NONE
            }
            return tag
        }
        return Tag.NONE
    }

    /**
     * Les titres d'une liste confirmés en VF. Attend au plus [budgetMs] les vérifications des titres
     * pas encore connus ; celles qui ne finissent pas à temps continuent en douce (prêtes la fois suivante).
     */
    suspend fun keepVf(items: List<MetaPreview>, budgetMs: Long = 12_000): List<MetaPreview> {
        if (items.isEmpty()) return items
        val verdicts = withTimeoutOrNull(budgetMs) {
            items.map { m -> scope.async { m.id to check(m.imdbId?.takeIf { it.startsWith("tt") } ?: m.id, m.rawType) } }.awaitAll().toMap()
        } ?: items.associate { m -> m.id to (known(m.imdbId ?: m.id) ?: known(m.id)) }
        // (les vérifications pas finies à temps continuent en douce : prêtes à la prochaine ouverture)
        return items.filter { isVf(verdicts[it.id]) }.map { m -> if (m.imdbId == null) m.copy(imdbId = imdbFor(m.id)) else m }
    }
}
