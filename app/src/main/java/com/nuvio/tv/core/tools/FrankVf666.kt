package com.nuvio.tv.core.tools

import android.content.Context

/**
 * 666TVF : la Shield prévient Frank quand elle a la preuve qu'un film / une série est en VF
 * (Affection, 28/10 : VF trouvée par StreamFusion et Lumio, mais French-Stream disait VOSTFR,
 * donc absent de « En VF cette semaine »).
 *  - preuve forte : piste audio française dans le fichier lu ;
 *  - preuve par le nom : release marquée TRUEFRENCH / VFF / VFQ / VF2 / VFI (jamais un « MULTi »).
 * Seuls l'identifiant du titre et son type partent ; jamais de clé, de lien ni de config.
 */
object FrankVf666 {

    // 666TVF (03/11) : « FRENCH » seul compte aussi (Resident Evil était en FRENCH et rien n'a crié).
    // « SUBFRENCH », « FRENCH SUBS » ou un VOSTFR à côté = des sous-titres, pas une VF.
    private val SURE_VF = Regex("""(?i)(?<![a-z0-9])(TRUE[ ._-]?FRENCH|FRENCH|VFF|VFQ|VF2|VFI)(?![a-z0-9])""")
    private val UNSURE = Regex("""(?i)VF ?\?|(?<![a-z0-9])(VOST(FR)?|STFR|SUB[ ._-]?FRENCH|FRENCH[ ._-]?(SUBS?|SUBBED|SUBTITLES?))(?![a-z0-9])""")

    /** Vrai si le nom de la release annonce une VF sûre (pas un MULTi incertain). */
    fun isSureVfRelease(text: String?): Boolean =
        !text.isNullOrBlank() && SURE_VF.containsMatchIn(text) && !UNSURE.containsMatchIn(text)

    /** Identifiant que Frank comprend : tt… (IMDb) seulement. */
    fun frankId(contentId: String?): String? =
        contentId?.substringBefore(":")?.takeIf { Regex("^tt\\d+$", RegexOption.IGNORE_CASE).matches(it) }?.lowercase()

    /** 666TVF (09/11) : plus rien n'est envoyé (Frank mis en pause par Hugging Face). Gardé pour les appels restants. */
    @Suppress("UNUSED_PARAMETER")
    fun confirm(context: Context, contentId: String?, contentType: String?, source: String) = Unit
}
