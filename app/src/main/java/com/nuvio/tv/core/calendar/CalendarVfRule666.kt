package com.nuvio.tv.core.calendar

import java.time.LocalDate

/**
 * 666TVF (07/11) : un résultat de cavalier prouve-t-il que CET épisode est en VF ?
 * SmackDown : la cloche a sonné « VF dispo » parce que Lumio renvoyait un lien… de la semaine
 * passée ; AHS S13 pareil. Avant, n'importe quel lien http comptait. Maintenant on écarte :
 *  - les VF douteuses (« VF ? », VOSTFR, SUBFRENCH…) ;
 *  - un autre épisode (SxxEyy différent) ;
 *  - une autre date (WWE, UFC… : 2026.10.31 pour un épisode du 06/11) — à un jour près ;
 *  - le 720p et moins (jamais regardé).
 */
object CalendarVfRule666 {
    private val UNSURE = Regex("""(?i)VF ?\?|(?<![a-z0-9])(VOST(FR)?|STFR|SUB[ ._-]?FRENCH|FRENCH[ ._-]?(SUBS?|SUBBED|SUBTITLES?))(?![a-z0-9])""")
    private val EPISODE = Regex("""(?i)(?<![a-z0-9])S(\d{1,2})[ ._-]?E(\d{1,3})(?!\d)""")
    private val YMD = Regex("""(?<!\d)(20\d{2})[ ._/-](\d{1,2})[ ._/-](\d{1,2})(?!\d)""")
    private val DMY = Regex("""(?<!\d)(\d{1,2})[ ._/-](\d{1,2})[ ._/-](20\d{2})(?!\d)""")
    private val HD = Regex("""(?i)(1080p|1440p|2160p|\b4k\b|\buhd\b)""")
    private val LOW = Regex("""(?i)(?<![0-9])(720p|576p|480p|360p)""")

    /** Règle pure (testée). [airDates] = jours de diffusion admis (US et France). */
    fun counts(text: String, quality: String?, season: Int?, episode: Int?, airDates: Set<LocalDate>): Boolean {
        if (UNSURE.containsMatchIn(text)) return false
        if (season != null && episode != null) {
            val eps = EPISODE.findAll(text).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
            if (eps.isNotEmpty() && eps.none { (s, e) -> s == season && e == episode }) return false
        }
        if (airDates.isNotEmpty()) {
            val dates = (YMD.findAll(text).mapNotNull { m -> date(m.groupValues[1], m.groupValues[2], m.groupValues[3]) } +
                DMY.findAll(text).mapNotNull { m -> date(m.groupValues[3], m.groupValues[2], m.groupValues[1]) }).toList()
            val ok = airDates.flatMap { listOf(it.minusDays(1), it, it.plusDays(1)) }.toSet()
            if (dates.isNotEmpty() && dates.none { it in ok }) return false
        }
        val q = listOfNotNull(quality, text).joinToString(" ")
        if (LOW.containsMatchIn(q) && !HD.containsMatchIn(q)) return false
        return true
    }

    private fun date(y: String, m: String, d: String): LocalDate? =
        runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
}
