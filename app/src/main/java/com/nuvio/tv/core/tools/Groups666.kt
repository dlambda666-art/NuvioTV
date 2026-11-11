package com.nuvio.tv.core.tools

import android.content.Context
import org.json.JSONObject

/**
 * 666TVF (09/11) : les groupes de release « MULTi avec ou sans VF », appris sur la Shield
 * (avant : par Frank, mis en pause par Hugging Face). Même règle que Frank :
 *  - les noms de releases vus dans les listes de liens (VFF/TRUEFRENCH/FRENCH → groupe français,
 *    PL/ITA/GER… sans VF → groupe étranger) ;
 *  - ce que la lecture prouve (piste française entendue ou non) : la preuve la plus forte.
 * Les listes partent vers Comet666 (et les autres cavaliers 666) dans leurs réglages, le temps d'une
 * recherche. Rien ne sort de la Shield.
 */
object Groups666 {
    private const val PREFS = "666tv_groups"
    private const val KEY = "groups_v1"
    private const val MAX_GROUPS = 1500

    private val FR_TAG = Regex("""(?i)\bVF[FQI2]?\b|\bTRUE[ ._-]*FRENCH\b|\bFRENCH\b|\bVOF\b""")
    private val SUB_ONLY = Regex("""(?i)\bVOST(?:FR)?\b|\bSTFR\b|\bSUBFRENCH\b""")
    private val OTHER_DUB = Regex("""(?i)\b(?:POLISH|PLDUB|PL|LEKTOR|DUBBING|ITA|ITALIAN|GER|GERMAN|DEUTSCH|SPANISH|CASTELLANO|LATINO|RUS|RUSSIAN|UKR|HUN|CZE|CZECH|TUR|TURKISH|POR|PORTUGUESE|HINDI|TAMIL)\b""")
    private val NOT_GROUP = setOf(
        "MKV", "MP4", "AVI", "X264", "X265", "H264", "H265", "HEVC", "WEB", "WEBDL", "WEBRIP", "DL", "HDR", "DV",
        "REMUX", "BLURAY", "MULTI", "FRENCH", "VFF", "VFQ", "VOSTFR", "AAC", "AC3", "DTS", "ATMOS", "SDR",
        "1080P", "2160P", "720P", "PROPER", "REPACK"
    )

    data class Entry(var fr: Int = 0, var other: Int = 0, var shieldFr: Int = 0, var shieldNo: Int = 0)

    private val groups = HashMap<String, Entry>()
    @Volatile private var ctx: Context? = null
    @Volatile private var dirty = false
    @Volatile private var lastSave = 0L

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        runCatching {
            val json = JSONObject(ctx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}")
            synchronized(groups) {
                json.keys().forEach { g ->
                    val o = json.optJSONObject(g) ?: return@forEach
                    groups[g] = Entry(o.optInt("fr"), o.optInt("other"), o.optInt("sf"), o.optInt("sn"))
                }
            }
        }
    }

    /** Règle pure (testée) : « …MULTi.1080p…-GROUPE.mkv » → GROUPE. */
    fun groupOf(release: String?): String? {
        val line = release.orEmpty().lineSequence().firstOrNull().orEmpty().trim()
            .replace(Regex("""\s*\[[^\]]*]\s*$"""), "")
            .replace(Regex("""(?i)\.(mkv|mp4|avi|ts|m2ts)$"""), "")
        val m = Regex("""-([A-Za-z0-9][A-Za-z0-9_]{1,19})$""").find(line) ?: return null
        val g = m.groupValues[1].uppercase()
        if (g in NOT_GROUP || g.all { it.isDigit() }) return null
        return g
    }

    private fun entry(g: String) = groups.getOrPut(g) { Entry() }

    /** Noms de releases vus dans une liste de liens. */
    fun observe(titles: List<String?>) {
        var changed = false
        synchronized(groups) {
            for (t in titles) {
                val text = t.orEmpty()
                val g = groupOf(text) ?: continue
                if (SUB_ONLY.containsMatchIn(text) && !FR_TAG.containsMatchIn(text.replace(SUB_ONLY, ""))) continue
                if (FR_TAG.containsMatchIn(text)) { entry(g).fr++; changed = true }
                else if (OTHER_DUB.containsMatchIn(text)) { entry(g).other++; changed = true }
            }
        }
        if (changed) { dirty = true; saveSoon() }
    }

    /** Ce que la lecture a prouvé (piste française entendue ou non). */
    fun verdict(release: String?, hasFrench: Boolean): String? {
        val g = groupOf(release) ?: return null
        synchronized(groups) { if (hasFrench) entry(g).shieldFr++ else entry(g).shieldNo++ }
        dirty = true
        saveSoon(force = true)
        return g
    }

    /** Règle pure (testée) : la lecture l'emporte ; sinon une vraie majorité. */
    fun classify(e: Entry): String? = when {
        e.shieldFr > e.shieldNo -> "fr"
        e.shieldNo > e.shieldFr -> "other"
        e.fr >= 2 && e.fr >= 3 * e.other -> "fr"
        e.other >= 3 && e.fr == 0 -> "other"
        else -> null
    }

    /** (groupes français, groupes étrangers) appris. */
    fun lists(): Pair<List<String>, List<String>> = synchronized(groups) {
        val fr = ArrayList<String>(); val other = ArrayList<String>()
        groups.forEach { (g, e) -> when (classify(e)) { "fr" -> fr += g; "other" -> other += g } }
        fr.sorted() to other.sorted()
    }

    fun knownCount(): Int = synchronized(groups) { groups.size }

    private fun saveSoon(force: Boolean = false) {
        val c = ctx ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSave < 60_000) return
        if (!dirty) return
        lastSave = now
        dirty = false
        val json = JSONObject()
        synchronized(groups) {
            // on garde les groupes les plus vus si la liste devient trop longue
            groups.entries.sortedByDescending { (_, e) -> e.fr + e.other + 5 * (e.shieldFr + e.shieldNo) }.take(MAX_GROUPS)
                .forEach { (g, e) ->
                    json.put(g, JSONObject().put("fr", e.fr).put("other", e.other).put("sf", e.shieldFr).put("sn", e.shieldNo))
                }
        }
        runCatching { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply() }
    }

    /** 11/11 : tout ce qui est appris, pour l'autre Shield / la clé (JSON, sans clé ni adresse). */
    fun exportJson(): JSONObject = synchronized(groups) {
        JSONObject().apply { groups.forEach { (g, e) -> put(g, JSONObject().put("fr", e.fr).put("other", e.other).put("sf", e.shieldFr).put("sn", e.shieldNo)) } }
    }

    /** Règle pure (testée) : fusion sans double compte (chaque compteur garde le plus grand des deux). */
    fun merged(a: Entry?, b: Entry): Entry = if (a == null) b.copy() else
        Entry(maxOf(a.fr, b.fr), maxOf(a.other, b.other), maxOf(a.shieldFr, b.shieldFr), maxOf(a.shieldNo, b.shieldNo))

    /** Fusionne ce que l'autre Shield a appris. Renvoie le nombre de groupes nouveaux ou changés. */
    fun importJson(json: JSONObject): Int {
        var n = 0
        synchronized(groups) {
            json.keys().forEach { g ->
                val o = json.optJSONObject(g) ?: return@forEach
                val e = merged(groups[g], Entry(o.optInt("fr"), o.optInt("other"), o.optInt("sf"), o.optInt("sn")))
                if (groups[g] != e) { groups[g] = e; n++ }
            }
        }
        if (n > 0) { dirty = true; saveSoon(force = true) }
        return n
    }

    /** Pour les tests. */
    internal fun resetForTest() = synchronized(groups) { groups.clear() }
}
