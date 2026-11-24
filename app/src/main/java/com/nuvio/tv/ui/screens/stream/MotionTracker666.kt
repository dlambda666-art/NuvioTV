package com.nuvio.tv.ui.screens.stream

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.cos
import kotlin.math.sin

/**
 * 24/11 (David) : le détecteur de mouvement d'Alien pendant la recherche des liens.
 * Un bip qui s'accélère à mesure que les cavaliers répondent ; un point par cavalier qui a trouvé des liens.
 */
object MotionTracker666 {
    /** Règle pure (testée) : écart entre deux bips (le vrai rythme du film) : 1,05 s au début, 0,55 s quand tous ont répondu. */
    fun beepIntervalMs(done: Int, total: Int): Long {
        if (total <= 0) return 1_050L
        val p = (done.toFloat() / total).coerceIn(0f, 1f)
        return (1_050L - (500L * p)).toLong().coerceIn(550L, 1_050L)
    }

    /** Règle pure (testée) : le bip « proche » (le vrai son de fin du film) quand plus de la moitié a répondu. */
    fun nearBeep(done: Int, total: Int): Boolean = total > 0 && done * 2 > total

    /** Règle pure (testée) : distance affichée (en mètres), qui se rapproche avec les réponses. */
    fun distanceMeters(done: Int, total: Int): Int {
        if (total <= 0) return 20
        val p = (done.toFloat() / total).coerceIn(0f, 1f)
        return (20 - (18 * p)).toInt().coerceAtLeast(2)
    }
}

private val TrackerGreen = Color(0xFF39FF7A)
private val TrackerDim = Color(0xFF0F3A1E)

@Composable
fun MotionTracker666View(done: Int, total: Int, blips: Int) {
    val context = LocalContext.current
    val doneState = rememberUpdatedState(done)
    val totalState = rememberUpdatedState(total)
    var pulse by remember { mutableFloatStateOf(0f) }
    var t by remember { mutableFloatStateOf(0f) }

    // le bip (SoundPool : court et précis), seulement tant que l'écran est là
    val pool = remember {
        android.media.SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    }
    // 24/11 : le vrai son du détecteur d'Alien (fourni par David) : bip lointain puis bip proche
    val soundId = remember { runCatching { pool.load(context, com.nuvio.tv.R.raw.detecteur_666, 1) }.getOrDefault(0) }
    val nearId = remember { runCatching { pool.load(context, com.nuvio.tv.R.raw.detecteur_pres_666, 1) }.getOrDefault(0) }
    DisposableEffect(Unit) { onDispose { runCatching { pool.release() } } }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        var nextBeep = start + 500L
        while (true) {
            withFrameMillis { now ->
                t = (now - start) / 1000f
                if (now >= nextBeep) {
                    val id = if (MotionTracker666.nearBeep(doneState.value, totalState.value) && nearId != 0) nearId else soundId
                    if (id != 0 && !com.nuvio.tv.core.tools.Announce666.inPlayer()) runCatching { pool.play(id, 0.7f, 0.7f, 1, 0, 1f) }
                    pulse = 0f
                    nextBeep = now + MotionTracker666.beepIntervalMs(doneState.value, totalState.value)
                } else {
                    pulse = (pulse + 0.016f).coerceAtMost(1f)
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(260.dp, 170.dp)) {
            val c = Offset(size.width / 2f, size.height * 0.95f)
            val r = size.height * 0.92f
            // éventail du détecteur
            for (k in 1..4) {
                drawArc(TrackerDim, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(c.x - r * k / 4f, c.y - r * k / 4f), size = Size(r * k / 2f, r * k / 2f), style = Stroke(2f))
            }
            drawLine(TrackerDim, c, Offset(c.x + r * cos(Math.toRadians(200.0)).toFloat(), c.y + r * sin(Math.toRadians(200.0)).toFloat()), 2f)
            drawLine(TrackerDim, c, Offset(c.x + r * cos(Math.toRadians(340.0)).toFloat(), c.y + r * sin(Math.toRadians(340.0)).toFloat()), 2f)
            // l'onde du bip
            val pr = r * (0.15f + 0.85f * pulse)
            drawArc(TrackerGreen.copy(alpha = (1f - pulse) * 0.9f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(c.x - pr, c.y - pr), size = Size(pr * 2, pr * 2), style = Stroke(4f))
            // les points : un par cavalier qui a trouvé des liens, de plus en plus près
            val dist = MotionTracker666.distanceMeters(doneState.value, totalState.value) / 20f
            for (i in 0 until blips.coerceAtMost(6)) {
                val ang = Math.toRadians((215.0 + i * 22.0 + 6.0 * sin(t * 0.7 + i))).toFloat()
                val rr = r * (0.25f + 0.65f * dist) * (0.9f + 0.1f * sin(t * 1.3f + i))
                val p = Offset(c.x + rr * cos(ang), c.y + rr * sin(ang))
                drawCircle(TrackerGreen.copy(alpha = 0.35f), radius = 14f, center = p)
                drawCircle(TrackerGreen, radius = 6f, center = p)
            }
        }
        Text(
            "${MotionTracker666.distanceMeters(done, total)} M",
            color = TrackerGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "les cavaliers répondent : $done / $total",
            color = TrackerGreen.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 13.sp
        )
    }
}
