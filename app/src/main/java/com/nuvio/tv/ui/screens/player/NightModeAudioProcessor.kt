package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

/**
 * 666TV : mode nuit. Compresseur dynamique : les sons forts (explosions, musique)
 * sont baissés, les sons faibles (voix, chuchotements) remontent. Même gain sur tous
 * les canaux pour garder l'image sonore. S'applique au son décodé en PCM.
 */
internal class NightModeAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var enabled: Boolean = false

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    private var envelope = 0.0
    private var attackCoef = 0.0
    private var releaseCoef = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> {
                val rate = inputAudioFormat.sampleRate.toDouble().coerceAtLeast(8_000.0)
                attackCoef = exp(-1.0 / (ATTACK_S * rate))
                releaseCoef = exp(-1.0 / (RELEASE_S * rate))
                envelope = 0.0
                inputAudioFormat
            }
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() { envelope = 0.0 }
    override fun onReset() { envelope = 0.0 }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val channels = inputAudioFormat.channelCount
        if (!enabled || channels <= 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val frame = DoubleArray(channels)
        val makeup = 10.0.pow(MAKEUP_DB / 20.0)
        while (inputBuffer.remaining() >= channels * bytesPerSample) {
            var peak = 0.0
            for (c in 0 until channels) {
                val v = if (isFloat) inputBuffer.float.toDouble() else inputBuffer.short / 32768.0
                frame[c] = v
                peak = max(peak, abs(v))
            }
            val coef = if (peak > envelope) attackCoef else releaseCoef
            envelope = coef * envelope + (1 - coef) * peak
            val levelDb = 20.0 * log10(envelope.coerceAtLeast(1e-6))
            val overDb = levelDb - THRESHOLD_DB
            val gainDb = if (overDb > 0) -overDb * (1.0 - 1.0 / RATIO) else 0.0
            val gain = 10.0.pow(gainDb / 20.0) * makeup
            for (c in 0 until channels) {
                val s = softLimit(frame[c] * gain)
                if (isFloat) outputBuffer.putFloat(s.toFloat())
                else outputBuffer.putShort((s * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        if (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    private fun softLimit(x: Double): Double {
        val a = abs(x)
        if (a <= KNEE) return x
        val limited = KNEE + (1.0 - KNEE) * tanh((a - KNEE) / (1.0 - KNEE))
        return if (x < 0) -limited else limited
    }

    private companion object {
        const val THRESHOLD_DB = -26.0
        const val RATIO = 4.0
        const val MAKEUP_DB = 8.0
        const val ATTACK_S = 0.005
        const val RELEASE_S = 0.300
        const val KNEE = 0.8
    }
}
