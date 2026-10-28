package com.nuvio.tv.core.tools

import android.content.Context
import org.json.JSONObject

/**
 * 666TVF : la Shield prévient Frank quand elle a la preuve qu'un film / une série est en VF
 * (Affection, 28/10 : VF trouvée par StreamFusion et Lumio, mais French-Stream disait VOSTFR,
 * donc absent de « En VF cette semaine »).
 *  - preuve forte : piste audio française dans le fichier lu ;
 *  - preuve par le nom : release marquée TRUEFRENCH / VFF / VFQ / VF2 / VFI (jamais un « MULTi »).
 * Seuls l'identifiant du titre et son type partent ; jamais de clé, de lien ni de config.
 */
object FrankVf666 {
    private const val URL = "https://lambda666-frankenstream.hf.space/vf/confirm"
    private const val PREFS = "666tv_frank_vf"
    private const val RESEND_MS = 7L * 24 * 3600 * 1000

    private val SURE_VF = Regex("""(?i)(?<![a-z0-9])(TRUE[ ._-]?FRENCH|VFF|VFQ|VF2|VFI)(?![a-z0-9])""")
    private val UNSURE = Regex("""(?i)VF ?\?""")

    /** Vrai si le nom de la release annonce une VF sûre (pas un MULTi incertain). */
    fun isSureVfRelease(text: String?): Boolean =
        !text.isNullOrBlank() && SURE_VF.containsMatchIn(text) && !UNSURE.containsMatchIn(text)

    /** Identifiant que Frank comprend : tt… (IMDb) seulement. */
    fun frankId(contentId: String?): String? =
        contentId?.substringBefore(":")?.takeIf { Regex("^tt\\d+$", RegexOption.IGNORE_CASE).matches(it) }?.lowercase()

    fun confirm(context: Context, contentId: String?, contentType: String?, source: String) {
        val id = frankId(contentId) ?: return
        val type = if (contentType.equals("movie", ignoreCase = true)) "movie" else "series"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(id, 0L)
        if (System.currentTimeMillis() - last < RESEND_MS) return
        Thread {
            runCatching {
                val body = JSONObject().put("imdbId", id).put("type", type).put("source", source).toString()
                val conn = (java.net.URL(URL).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) prefs.edit().putLong(id, System.currentTimeMillis()).apply()
            }
        }.start()
    }
}
