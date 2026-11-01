package com.nuvio.tv.ui.screens.player

/**
 * 666TVF (01/11) : sous-titres forcés. Beaucoup de releases VF nomment la piste « Forcés », « FR forcé »,
 * « French (Forced) », « VFF Forces »… On reconnaît le mot entier (anglais ou français, avec ou sans accent),
 * pas un bout de mot (« Forcené », « Workforce » ne comptent pas).
 */
object Forced666 {
    private val FORCED = Regex(
        """(?<![\p{L}])(forced|forc[eé]e?s?)(?![\p{L}])""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun isForcedName(text: String?): Boolean =
        !text.isNullOrBlank() && FORCED.containsMatchIn(text.lowercase())
}
