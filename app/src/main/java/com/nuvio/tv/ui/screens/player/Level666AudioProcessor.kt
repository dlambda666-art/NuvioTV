package com.nuvio.tv.ui.screens.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tanh

/**
 * 666TVF (01/11) : volume constant. Chaque lien arrive au même niveau moyen : un film mixé trop bas
 * est remonté, un film trop fort est baissé (la barre réglée sur 4 suffit partout). On mesure le niveau
 * moyen sur quelques secondes (les silences ne comptent pas) et on ajuste très doucement, sans écraser
 * les nuances d'une scène (ça, c'est le mode nuit). Garde-fou anti-saturation en sortie.
 * S'applique au son décodé par la Shield (le volume constant demande le décodage, voir Level666).
 */
internal class Level666AudioProcessor : BaseAudioProcessor() {

    @Volatile private var enabled: Boolean = true
    fun setEnabled(value: Boolean) { enabled = value }

    private var sampleRate = 48_000.0
    private var meanSquare = 0.0          // niveau moyen mesuré (puissance)
    private var gainDb = 0.0              // gain appliqué, lissé
    private var framesSeen = 0L
    private var hasMeasure = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> {
                sampleRate = inputAudioFormat.sampleRate.toDouble().coerceAtLeast(8_000.0)
                resetState()
                inputAudioFormat
            }
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    // Un saut dans le film garde le réglage (même film) ; un nouveau film repart de zéro (onReset).
    override fun onFlush() {}
    override fun onReset() { resetState() }

    private fun resetState() { meanSquare = 0.0; gainDb = 0.0; framesSeen = 0; hasMeasure = false }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val channels = inputAudioFormat.channelCount
        if (!enabled || channels <= 0) {
            outputBuffer.put(inputBuffer); outputBuffer.flip(); return
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val frame = DoubleArray(channels)
        while (inputBuffer.remaining() >= channels * bytesPerSample) {
            var sq = 0.0
            for (c in 0 until channels) {
                val v = if (isFloat) inputBuffer.float.toDouble() else inputBuffer.short / 32768.0
                frame[c] = v
                sq += v * v
            }
            sq /= channels
            framesSeen++
            // Mesure : rapide au démarrage (2-3 s), puis lente ; les silences n'abaissent pas la mesure.
            val warm = framesSeen < WARMUP_S * sampleRate
            val tau = if (warm) MEASURE_FAST_S else MEASURE_SLOW_S
            if (sq > GATE_POWER || !hasMeasure) {
                val a = exp(-1.0 / (tau * sampleRate))
                meanSquare = a * meanSquare + (1 - a) * sq
                if (meanSquare > GATE_POWER) hasMeasure = true
            }
            if (hasMeasure) {
                val levelDb = 10.0 * log10(meanSquare.coerceAtLeast(1e-12))
                val target = targetGainDb(levelDb)
                // On baisse plus vite qu'on ne remonte (jamais de surprise forte).
                val t = if (target < gainDb) (if (warm) 0.3 else GAIN_DOWN_S) else (if (warm) 0.6 else GAIN_UP_S)
                val g = exp(-1.0 / (t * sampleRate))
                gainDb = g * gainDb + (1 - g) * target
            }
            val gain = 10.0.pow(gainDb / 20.0)
            for (c in 0 until channels) {
                val s = softLimit(frame[c] * gain)
                if (isFloat) outputBuffer.putFloat(s.toFloat())
                else outputBuffer.putShort((s * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        if (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    companion object {
        /** Niveau moyen visé (dBFS, moyenne d'énergie) : celui d'un film « normal » à 4 sur la barre. */
        const val TARGET_DB = -21.0
        const val MAX_BOOST_DB = 14.0
        const val MAX_CUT_DB = -10.0
        const val WARMUP_S = 2.5
        const val MEASURE_FAST_S = 0.6
        const val MEASURE_SLOW_S = 4.0
        const val GAIN_UP_S = 4.0
        const val GAIN_DOWN_S = 1.0
        val GATE_POWER = 10.0.pow(-55.0 / 10.0)
        const val KNEE = 0.85

        /** Règle pure (testée) : gain à viser pour un niveau moyen mesuré. */
        fun targetGainDb(levelDb: Double): Double = (TARGET_DB - levelDb).coerceIn(MAX_CUT_DB, MAX_BOOST_DB)

        fun softLimit(x: Double): Double {
            val a = abs(x)
            if (a <= KNEE) return x
            val limited = KNEE + (1.0 - KNEE) * tanh((a - KNEE) / (1.0 - KNEE))
            return if (x < 0) -limited else limited
        }
    }
}

/** 666TVF : réglage « Volume constant » (par Shield, activé par défaut). */
object Level666 {
    private const val PREFS = "666tv_volume"
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("constant", true)
    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("constant", on).apply()
}
