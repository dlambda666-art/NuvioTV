package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.util.Locale

/**
 * FrankenStream (16/11, pris dans Nuvio 1.1.0 stable) : sous le titre, quand on affiche les contrôles,
 * des badges de ce qui est en lecture : qualité, HDR / Dolby Vision, son, canaux, langue de la piste
 * audio (VF en vert), source (WEB-DL, BluRay…).
 *
 * Nuvio utilise des packs d'images à configurer ; ici, de simples badges texte aux couleurs de Frank,
 * lus sur ce que le lecteur décode vraiment (et le nom du fichier pour ce qu'il ne dit pas).
 */
data class MediaInfo666(
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val hdr: List<String> = emptyList(),
    val audioMime: String? = null,
    val audioCodecs: String? = null,
    val audioLabel: String? = null,
    val audioChannels: Int? = null
)

object PlayerInfoBadges666 {
    data class Badge(val text: String, val vf: Boolean = false, val cavalier: Boolean = false)

    /**
     * 20/11 — règle pure (testée) : nom du cavalier à afficher. D'abord celui du lien (bingeGroup « 666|… »),
     * sinon le nom de la source s'il ressemble à un cavalier (pas « Les 4 cavaliers… », le nom du groupe).
     */
    fun cavalierName(fromBinge: String?, addonName: String?): String? {
        val raw = fromBinge ?: addonName?.trim()?.takeIf { it.endsWith("666", ignoreCase = true) } ?: return null
        return com.nuvio.tv.core.tools.PluginOrder666.displayName("", raw)
    }

    /** 20/11 : badge du cavalier, en tête des badges. */
    fun withCavalier(badges: List<Badge>, cavalier: String?): List<Badge> =
        if (cavalier.isNullOrBlank()) badges else listOf(Badge("🐎 $cavalier", cavalier = true)) + badges

    private fun has(text: String, re: String) = Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** Règle pure (testée). */
    fun badges(info: MediaInfo666?, release: String?, audioLanguage: String?): List<Badge> {
        val r = release.orEmpty()
        val out = ArrayList<Badge>()
        // Qualité : ce que le lecteur décode, sinon le nom du fichier
        val h = info?.videoHeight ?: 0
        val w = info?.videoWidth ?: 0
        val res = when {
            h >= 1600 || w >= 3200 -> "4K"
            h >= 900 || w >= 1700 -> "1080p"
            h >= 600 || w >= 1100 -> "720p"
            has(r, """\b(2160p|4k|uhd)\b""") -> "4K"
            has(r, """\b1080p\b""") -> "1080p"
            else -> null
        }
        res?.let { out += Badge(it) }
        // HDR / Dolby Vision
        val hdr = info?.hdr.orEmpty()
        when {
            "DV" in hdr || has(r, """\b(dv|dovi|dolby[ ._-]?vision)\b""") -> out += Badge("Dolby Vision")
            has(r, """\bhdr10(\+|plus)""") -> out += Badge("HDR10+")
            "HDR10" in hdr || has(r, """\bhdr(10)?\b""") -> out += Badge("HDR10")
            "HLG" in hdr -> out += Badge("HLG")
        }
        // Son
        val mime = info?.audioMime.orEmpty().lowercase()
        val codecs = (info?.audioCodecs.orEmpty() + " " + info?.audioLabel.orEmpty()).lowercase()
        val codec = when {
            mime.contains("true-hd") || mime.contains("truehd") -> "TrueHD"
            mime.contains("eac3") || mime.contains("e-ac3") -> "Dolby Digital+"
            mime.contains("ac3") || mime.contains("ac-3") -> "Dolby Digital"
            mime.contains("dts.hd") || mime.contains("dts-hd") || codecs.startsWith("dtsh") || codecs.startsWith("dtsl") ->
                if (has(r, """\b(dts[ ._-]?hd[ ._-]?ma|master[ ._-]?audio)\b""") || codecs.startsWith("dtsl")) "DTS-HD MA" else "DTS-HD"
            mime.contains("dts") -> "DTS"
            mime.contains("mp4a") || mime.contains("aac") -> "AAC"
            mime.contains("opus") -> "Opus"
            mime.isBlank() && has(r, """\b(ddp|eac3|e-ac-3|dd\+)""") -> "Dolby Digital+"
            mime.isBlank() && has(r, """\btruehd\b""") -> "TrueHD"
            mime.isBlank() && has(r, """\b(ac3|dd5)""") -> "Dolby Digital"
            else -> null
        }
        codec?.let { out += Badge(it) }
        if (mime.contains("joc") || codecs.contains("atmos") || has(r, """\batmos\b""")) out += Badge("Atmos")
        when (info?.audioChannels) {
            8 -> out += Badge("7.1")
            6 -> out += Badge("5.1")
            2 -> out += Badge("2.0")
            1 -> out += Badge("Mono")
            else -> when {
                has(r, """\b7[ ._]1\b""") -> out += Badge("7.1")
                has(r, """\b5[ ._]1\b""") -> out += Badge("5.1")
            }
        }
        // Langue de la piste audio choisie
        val lang = audioLanguage?.trim()?.lowercase().orEmpty()
        when {
            lang.startsWith("fr") -> out += Badge("VF", vf = true)
            lang.isNotEmpty() && lang != "und" -> {
                val name = Locale(lang.take(3)).getDisplayLanguage(Locale.FRANCE)
                    .takeIf { it.isNotBlank() && !it.equals(lang, true) }
                    ?.replaceFirstChar { it.titlecase(Locale.FRANCE) }
                out += Badge(name ?: lang.uppercase())
            }
        }
        // Source
        when {
            has(r, """\bremux\b""") -> out += Badge("Remux")
            has(r, """\b(blu[ ._-]?ray|bdrip|brrip|bdmv)\b""") -> out += Badge("BluRay")
            has(r, """\bweb[ ._-]?dl\b""") -> out += Badge("WEB-DL")
            has(r, """\bweb[ ._-]?rip\b""") -> out += Badge("WEBRip")
            has(r, """\bhdlight\b""") -> out += Badge("HDLight")
            has(r, """\bhdtv\b""") -> out += Badge("HDTV")
            has(r, """\bweb\b""") -> out += Badge("WEB")
        }
        return out
    }
}

private val Mauve666 = Color(0xFFC08BFF)

@Composable
internal fun rememberPlayerBadges666(viewModel: PlayerViewModel, uiState: PlayerUiState): List<PlayerInfoBadges666.Badge> {
    var info by remember { mutableStateOf<MediaInfo666?>(null) }
    val selectedAudio = uiState.audioTracks.firstOrNull { it.isSelected }
    LaunchedEffect(uiState.showControls, uiState.isBuffering, uiState.currentStreamUrl, selectedAudio?.index) {
        if (uiState.showControls && !uiState.isBuffering) info = viewModel.mediaInfo666()
    }
    val release = viewModel.release666()
    val cavalier = viewModel.cavalier666()
    return remember(info, release, selectedAudio?.language, cavalier) {
        PlayerInfoBadges666.withCavalier(PlayerInfoBadges666.badges(info, release, selectedAudio?.language), cavalier)
    }
}

@Composable
internal fun PlayerBadges666(badges: List<PlayerInfoBadges666.Badge>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.take(9).forEach { b ->
            val color = if (b.vf) com.nuvio.tv.ui.components.Font666.Green else Mauve666
            Text(
                text = b.text,
                color = if (b.vf) com.nuvio.tv.ui.components.Font666.Green else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .border(1.5.dp, color, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}
