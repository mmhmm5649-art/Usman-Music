package com.usman.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Media3 ExoPlayer AudioProcessor executing the Studio Feel DSP pipeline directly on decoded PCM frames.
 * - Zero allocations inside queueInput()
 * - Lock-free atomic state snapshot updates from UI / ViewModel thread
 * - Supports PCM 16-bit and PCM Float (mono & stereo; safely bypasses exotic >2ch streams)
 */
@UnstableApi
class StudioFeelAudioProcessor(
    private val onFormatConfigured: ((sampleRateHz: Int, channelCount: Int, encodingName: String, active: Boolean) -> Unit)? = null
) : BaseAudioProcessor() {

    private var pipeline: StudioDspPipeline? = null
    private val targetVersion = AtomicLong(0L)
    private var appliedVersion = -1L
    private var activeSampleRate: Int = 44100
    private var activeChannelCount: Int = 2
    private var activeEncoding: Int = C.ENCODING_PCM_16BIT

    @Volatile
    private var latestState: AudioEffectsState = AudioEffectsState()

    fun updateState(newState: AudioEffectsState) {
        latestState = newState
        targetVersion.incrementAndGet()
    }

    fun getLastPeakReductionDb(): Float = pipeline?.lastPeakReductionDb ?: 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        val ch = inputAudioFormat.channelCount
        val sr = inputAudioFormat.sampleRate

        val supportedEnc = enc == C.ENCODING_PCM_16BIT || enc == C.ENCODING_PCM_FLOAT
        if (!supportedEnc || ch !in 1..2 || sr <= 0) {
            pipeline = null
            onFormatConfigured?.invoke(sr, ch, encodingLabel(enc), false)
            return AudioProcessor.AudioFormat.NOT_SET
        }

        activeSampleRate = sr
        activeChannelCount = ch
        activeEncoding = enc

        val newPipeline = StudioDspPipeline(sr).apply {
            applyConfig(latestState)
        }
        pipeline = newPipeline
        appliedVersion = targetVersion.get()
        onFormatConfigured?.invoke(sr, ch, encodingLabel(enc), true)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val currentPipeline = pipeline
        val currentState = latestState
        val ver = targetVersion.get()
        if (currentPipeline != null && ver != appliedVersion) {
            currentPipeline.applyConfig(currentState)
            appliedVersion = ver
        }

        val outBuffer = replaceOutputBuffer(remaining)

        if (currentPipeline == null || !currentState.masterEnabled) {
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        val ch = if (inputAudioFormat.channelCount > 0) inputAudioFormat.channelCount else activeChannelCount
        val enc = if (inputAudioFormat.encoding != C.INDEX_UNSET && inputAudioFormat.encoding != 0) {
            inputAudioFormat.encoding
        } else {
            activeEncoding
        }

        if (enc == C.ENCODING_PCM_16BIT) {
            val frameBytes = ch * 2
            val frameCount = remaining / frameBytes
            val scaleIn = 1.0 / 32768.0
            val scaleOut = 32767.0
            if (ch == 2) {
                for (i in 0 until frameCount) {
                    val inL = inputBuffer.getShort().toDouble() * scaleIn
                    val inR = inputBuffer.getShort().toDouble() * scaleIn
                    val out = currentPipeline.processStereoSample(inL, inR)
                    val sL = (out.first * scaleOut).toInt().coerceIn(-32768, 32767).toShort()
                    val sR = (out.second * scaleOut).toInt().coerceIn(-32768, 32767).toShort()
                    outBuffer.putShort(sL)
                    outBuffer.putShort(sR)
                }
            } else {
                for (i in 0 until frameCount) {
                    val inM = inputBuffer.getShort().toDouble() * scaleIn
                    val out = currentPipeline.processStereoSample(inM, inM)
                    val mono = ((out.first + out.second) * 0.5 * scaleOut)
                        .toInt()
                        .coerceIn(-32768, 32767)
                        .toShort()
                    outBuffer.putShort(mono)
                }
            }
            // Copy any trailing partial bytes if present
            while (inputBuffer.hasRemaining()) {
                outBuffer.put(inputBuffer.get())
            }
        } else if (enc == C.ENCODING_PCM_FLOAT) {
            val frameBytes = ch * 4
            val frameCount = remaining / frameBytes
            if (ch == 2) {
                for (i in 0 until frameCount) {
                    val inL = inputBuffer.getFloat().toDouble()
                    val inR = inputBuffer.getFloat().toDouble()
                    val out = currentPipeline.processStereoSample(inL, inR)
                    outBuffer.putFloat(out.first.toFloat())
                    outBuffer.putFloat(out.second.toFloat())
                }
            } else {
                for (i in 0 until frameCount) {
                    val inM = inputBuffer.getFloat().toDouble()
                    val out = currentPipeline.processStereoSample(inM, inM)
                    outBuffer.putFloat(((out.first + out.second) * 0.5).toFloat())
                }
            }
            while (inputBuffer.hasRemaining()) {
                outBuffer.put(inputBuffer.get())
            }
        } else {
            outBuffer.put(inputBuffer)
        }

        outBuffer.flip()
    }

    override fun onFlush() {
        pipeline?.resetState()
    }

    override fun onReset() {
        pipeline?.resetState()
        pipeline = null
        onFormatConfigured?.invoke(44100, 2, "PCM", false)
    }

    private fun encodingLabel(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_16BIT -> "PCM 16-bit"
        C.ENCODING_PCM_FLOAT -> "PCM 32-bit Float"
        C.ENCODING_PCM_24BIT -> "PCM 24-bit"
        C.ENCODING_PCM_32BIT -> "PCM 32-bit"
        else -> "PCM"
    }
}
