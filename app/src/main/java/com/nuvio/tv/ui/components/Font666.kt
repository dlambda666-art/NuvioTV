package com.nuvio.tv.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nuvio.tv.R

/**
 * 666TV : police horreur (Creepster, licence OFL) pour le menu, les onglets des Paramètres
 * et l'heure. En vert avec un léger halo net ; au focus on repasse sur la police normale en blanc.
 */
object Font666 {
    val Creepster = FontFamily(Font(R.font.creepster_666))
    val Green = Color(0xFF5BE36B)
    private val glow = Shadow(color = Color(0xB31FAF3A), offset = Offset.Zero, blurRadius = 8f)

    fun horror(base: TextStyle, boost: Float = 1.1f): TextStyle = base.copy(
        fontFamily = Creepster,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.6.sp,
        shadow = glow,
        fontSize = if (base.fontSize.isSp) base.fontSize * boost else base.fontSize
    )
}
