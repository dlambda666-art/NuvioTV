package com.nuvio.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Thème 666TV : le vert du « TV » de notre logo (dégradé du haut vers le bas des lettres),
 * sur un fond noir légèrement teinté. Gratuit, et thème par défaut de 666TV.
 */
object TV666Colors {
    private val tvGreenGradient = listOf(
        Color(0xFF49DD89),   // haut du TV
        Color(0xFF19C88E),
        Color(0xFF10946D)    // bas du TV
    )

    val Palette = ThemeColorPalette(
        secondary = Color(0xFF2DD58F),
        secondaryVariant = Color(0xFF10946D),
        onSecondary = NuvioPrimitives.neutral925,
        onSecondaryVariant = NuvioPrimitives.white,
        accentGradient = tvGreenGradient,
        focusRing = Color(0xFF49DD89),
        focusRingGradient = tvGreenGradient,
        focusBackground = Color(0xFF12392B),
        background = Color(0xFF0C0F0E),
        backgroundElevated = Color(0xFF151B18),
        backgroundCard = Color(0xFF18221E),
        surface = Color(0xFF19201D),
        surfaceVariant = Color(0xFF223029),
        panel = Color(0xFF151B18),
        field = Color(0xFF1B2621),
        menu = Color(0xFF19201D),
        modal = Color(0xFF151B18)
    )
}
