package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 666TV : renfort des voix pour TOUTES les pistes décodées en PCM
 * (stéréo AAC/Opus, mono, 5.1 décodé par l'appareil…), là où le réglage
 * « canal central » de FFmpeg ne s'applique pas.
 *
 * - 3 canaux ou plus : gain direct sur le canal central (ordre Android FL, FR, FC…).
 * - stéréo / mono : on isole ce qui est commun aux deux canaux (le centre fantôme,
 *   là où sont les dialogues), on n'en garde que la bande de la voix (~300 Hz–4 kHz)
 *   et on l'ajoute aux deux canaux.
 * Un limiteur doux évite la saturation. Quand FFmpeg applique déjà le gain du
 * centre (transcodage AC-3 ou downmix), ce processeur s'efface.
 */
internal class DialogueBoostAudioProcessor(
    private val externalCenterMixActive: () -> Boolean
) : BaseAudioProcessor() {

    @Volatile
    private var boostDb: Int = 0

    fun setBoostDb(db: Int) {
        boostDb = db
    }

    // Filtre passe-bande (RBJ, gain 0 dB au centre), état par canal traité.
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> {
                designBandPass(inputAudioFormat.sampleRate.toDouble())
                inputAudioFormat
            }
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    private fun designBandPass(sampleRate: Double) {
        val f0 = VOICE_CENTER_HZ.coerceAtMost(sampleRate / 4.0)
        val w0 = 2.0 * PI * f0 / sampleRate
        val alpha = sin(w0) / (2.0 * VOICE_Q)
        val a0 = 1.0 + alpha
        b0 = alpha / a0
        b1 = 0.0
        b2 = -alpha / a0
        a1 = -2.0 * cos(w0) / a0
        a2 = (1.0 - alpha) / a0
        resetFilter()
    }

    private fun resetFilter() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }

    override fun onFlush() {
        resetFilter()
    }

    override fun onReset() {
        resetFilter()
        boostDb = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val db = boostDb
        val channels = inputAudioFormat.channelCount
        if (db == 0 || channels <= 0 || externalCenterMixActive()) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }
        val linear = 10.0.pow(db / 20.0)
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val frame = DoubleArray(channels)
        while (inputBuffer.remaining() >= channels * bytesPerSample) {
            for (c in 0 until channels) {
                frame[c] = if (isFloat) inputBuffer.float.toDouble() else inputBuffer.short / 32768.0
            }
            if (channels >= 3) {
                frame[2] *= linear
            } else {
                val mid = if (channels == 2) (frame[0] + frame[1]) * 0.5 else frame[0]
                val voice = b0 * mid + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = mid; y2 = y1; y1 = voice
                val add = (linear - 1.0) * voice
                for (c in 0 until channels) frame[c] += add
            }
            for (c in 0 until channels) {
                val s = softLimit(frame[c])
                if (isFloat) {
                    outputBuffer.putFloat(s.toFloat())
                } else {
                    outputBuffer.putShort((s * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }
        if (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    private fun softLimit(x: Double): Double {
        val a = abs(x)
        if (a <= LIMIT_KNEE) return x
        val over = (a - LIMIT_KNEE) / (1.0 - LIMIT_KNEE)
        val limited = LIMIT_KNEE + (1.0 - LIMIT_KNEE) * tanh(over)
        return if (x < 0) -limited else limited
    }

    private companion object {
        const val VOICE_CENTER_HZ = 1_200.0
        const val VOICE_Q = 0.55
        const val LIMIT_KNEE = 0.85
    }
}
