package com.nuvio.tv.ui.screens.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.Font666

private val Blood666K = Color(0xFF7E22B8)
private val KeyBg = Color(0xEE121416)

/**
 * 666TV : clavier à nous pour la recherche, en Creepster vert, AZERTY,
 * avec une citrouille à la place du « O ». Le texte se tape en direct
 * (les résultats suivent), OK lance la recherche, Retour ferme.
 */
@Composable
fun Keyboard666(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    var digits by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val rows = if (digits) listOf("1234567890", "-'&:!?.,", "") else listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xF20A0B0C), RoundedCornerShape(18.dp))
                .border(2.dp, Font666.Green.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ce qui est tapé
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(Color(0xFF0F1112), RoundedCornerShape(14.dp))
                    .border(3.dp, Font666.Green, RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (query.isEmpty()) "TAPE UN FILM OU UNE SÉRIE…" else query.uppercase() + "▏",
                    style = Font666.horror(MaterialTheme.typography.headlineMedium, 1.3f),
                    color = if (query.isEmpty()) Font666.Green.copy(alpha = 0.45f) else Font666.Green,
                    maxLines = 1
                )
            }
            rows.forEachIndexed { r, row ->
                if (row.isEmpty()) return@forEachIndexed
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, ch ->
                        Key666(
                            width = 76.dp,
                            modifier = if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { onQueryChanged(query + ch.lowercaseChar()) }
                        ) { focused ->
                            if (ch == 'O') Pumpkin666(focused) else KeyLabel(ch.toString(), focused, 1.5f)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Key666(width = 330.dp, onClick = { onQueryChanged("$query ") }) { KeyLabel("ESPACE", it) }
                Key666(width = 200.dp, onClick = { onQueryChanged(query.dropLast(1)) }) { KeyLabel("EFFACER", it) }
                Key666(width = 120.dp, onClick = { digits = !digits }) { KeyLabel(if (digits) "ABC" else "123", it) }
                Key666(width = 150.dp, onClick = { onSubmit(); onDismiss() }) { KeyLabel("OK", it) }
            }
        }
    }
}

@Composable
private fun Key666(
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(70.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = KeyBg,
            focusedContainerColor = Blood666K
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
    ) {
        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { content(focused) }
    }
}

@Composable
private fun KeyLabel(text: String, focused: Boolean, boost: Float = 1.15f) {
    Text(
        text = text,
        style = Font666.horror(MaterialTheme.typography.titleLarge, boost),
        color = if (focused) Color.White else Font666.Green
    )
}

/** La touche « O » : une petite citrouille d'Halloween, dans le vert 666. */
@Composable
private fun Pumpkin666(focused: Boolean) {
    val body = if (focused) Color.White else Font666.Green
    val carve = if (focused) Blood666K else Color(0xFF0A0B0C)
    Canvas(Modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        // tige
        drawRect(if (focused) Color.White else Color(0xFF3B7D2A), Offset(w * 0.45f, h * 0.02f), Size(w * 0.1f, h * 0.18f))
        // trois lobes
        drawOval(body, Offset(w * 0.02f, h * 0.18f), Size(w * 0.5f, h * 0.78f))
        drawOval(body, Offset(w * 0.48f, h * 0.18f), Size(w * 0.5f, h * 0.78f))
        drawOval(body, Offset(w * 0.22f, h * 0.15f), Size(w * 0.56f, h * 0.82f))
        // yeux triangulaires
        fun tri(cx: Float) = Path().apply {
            moveTo(cx - w * 0.09f, h * 0.48f); lineTo(cx + w * 0.09f, h * 0.48f); lineTo(cx, h * 0.33f); close()
        }
        drawPath(tri(w * 0.34f), carve)
        drawPath(tri(w * 0.66f), carve)
        // bouche en dents de scie
        val mouth = Path().apply {
            moveTo(w * 0.22f, h * 0.62f)
            lineTo(w * 0.34f, h * 0.70f); lineTo(w * 0.42f, h * 0.62f); lineTo(w * 0.50f, h * 0.72f)
            lineTo(w * 0.58f, h * 0.62f); lineTo(w * 0.66f, h * 0.70f); lineTo(w * 0.78f, h * 0.62f)
            lineTo(w * 0.70f, h * 0.80f); lineTo(w * 0.30f, h * 0.80f); close()
        }
        drawPath(mouth, carve)
    }
}
