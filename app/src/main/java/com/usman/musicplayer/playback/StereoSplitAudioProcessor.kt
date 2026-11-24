package com.usman.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer

/**
 * Output channel mapping role applied at the real decoded PCM stage in Media3 ExoPlayer.
 *
 * - [STEREO]: Normal listening mode (default) — completely bypasses channel splitting and outputs
 *   the original unmodified stereo stream.
 * - [LEFT]: Stereo Split LEFT speaker — extracts only the original Left PCM channel and duplicates
 *   it to both local output channels `(L, L)` so phone speakers / Bluetooth speakers play only Left
 *   without clipping or channel summing.
 * - [RIGHT]: Stereo Split RIGHT speaker — extracts only the original Right PCM channel and
 *   duplicates it to both local output channels `(R, R)`.
 */
enum class PcmSpeakerChannelRole(val wireValue: String) {
    STEREO("STEREO"),
    LEFT("LEFT"),
    RIGHT("RIGHT");

    companion object {
        fun fromWire(raw: String?): PcmSpeakerChannelRole {
            return when (raw?.trim()?.uppercase()) {
                "LEFT" -> LEFT
                "RIGHT" -> RIGHT
                else -> STEREO
            }
        }
    }
}

data class StereoSplitAudioTelemetry(
    val activeRole: PcmSpeakerChannelRole = PcmSpeakerChannelRole.STEREO,
    val channelCount: Int = 2,
    val sampleRateHz: Int = 44100,
    val encodingName: String = "PCM_16BIT",
    val isMonoSource: Boolean = false,
    val isSplitActive: Boolean = false
)

/**
 * Real Media3 [BaseAudioProcessor] that performs sample-accurate decoded PCM channel separation
 * for Listen Together Stereo Split Speaker Mode.
 *
 * Placed BEFORE StudioFeelAudioProcessor in the ExoPlayer audio pipeline so L/R channel
 * extraction happens on the clean decoded stereo signal prior to any downstream reverb/virtualizer.
 */
@UnstableApi
class StereoSplitAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var channelRole: PcmSpeakerChannelRole = PcmSpeakerChannelRole.STEREO

    private val _telemetry = MutableStateFlow(StereoSplitAudioTelemetry())
    val telemetry: StateFlow<StereoSplitAudioTelemetry> = _telemetry.asStateFlow()

    fun getChannelRole(): PcmSpeakerChannelRole = channelRole

    fun setChannelRole(role: PcmSpeakerChannelRole) {
        channelRole = role
        refreshTelemetry(inputAudioFormat)
    }

    fun resetToStereo() {
        setChannelRole(PcmSpeakerChannelRole.STEREO)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT

        if (!supportedEncoding) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount !in 1..2) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }

        refreshTelemetry(inputAudioFormat)
        // Always stay active for 1ch/2ch PCM so switching between NORMAL / LEFT / RIGHT
        // works instantaneously mid-song without recreating ExoPlayer.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val role = channelRole
        val channels = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding

        // Mode A (Normal Listening) or mono track: direct bit-identical passthrough with zero modification.
        if (role == PcmSpeakerChannelRole.STEREO || channels != 2) {
            val outBuffer = replaceOutputBuffer(remaining)
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        val bytesPerSample = bytesPerSampleFor(encoding)
        val frameBytes = bytesPerSample * 2
        if (frameBytes <= 0 || remaining < frameBytes) {
            val outBuffer = replaceOutputBuffer(remaining)
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        val fullFrameBytes = (remaining / frameBytes) * frameBytes
        val outBuffer = replaceOutputBuffer(fullFrameBytes)
        val isLeftRole = role == PcmSpeakerChannelRole.LEFT
        val endPos = inputBuffer.position() + fullFrameBytes

        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                var pos = inputBuffer.position()
                while (pos + 4 <= endPos) {
                    val sample = if (isLeftRole) inputBuffer.getShort(pos) else inputBuffer.getShort(pos + 2)
                    outBuffer.putShort(sample)
                    outBuffer.putShort(sample)
                    pos += 4
                }
                inputBuffer.position(endPos)
            }

            C.ENCODING_PCM_FLOAT -> {
                var pos = inputBuffer.position()
                while (pos + 8 <= endPos) {
                    val sample = if (isLeftRole) inputBuffer.getFloat(pos) else inputBuffer.getFloat(pos + 4)
                    outBuffer.putFloat(sample)
                    outBuffer.putFloat(sample)
                    pos += 8
                }
                inputBuffer.position(endPos)
            }

            C.ENCODING_PCM_32BIT -> {
                var pos = inputBuffer.position()
                while (pos + 8 <= endPos) {
                    val sample = if (isLeftRole) inputBuffer.getInt(pos) else inputBuffer.getInt(pos + 4)
                    outBuffer.putInt(sample)
                    outBuffer.putInt(sample)
                    pos += 8
                }
                inputBuffer.position(endPos)
            }

            C.ENCODING_PCM_24BIT -> {
                var pos = inputBuffer.position()
                while (pos + 6 <= endPos) {
                    val offset = if (isLeftRole) pos else pos + 3
                    val b0 = inputBuffer.get(offset)
                    val b1 = inputBuffer.get(offset + 1)
                    val b2 = inputBuffer.get(offset + 2)
                    outBuffer.put(b0)
                    outBuffer.put(b1)
                    outBuffer.put(b2)
                    outBuffer.put(b0)
                    outBuffer.put(b1)
                    outBuffer.put(b2)
                    pos += 6
                }
                inputBuffer.position(endPos)
            }

            else -> {
                outBuffer.put(inputBuffer)
            }
        }

        outBuffer.flip()
    }

    override fun onReset() {
        refreshTelemetry(AudioFormat.NOT_SET)
    }

    private fun refreshTelemetry(format: AudioFormat) {
        val role = channelRole
        val valid = format != AudioFormat.NOT_SET && format.channelCount > 0
        val channels = if (valid) format.channelCount else 2
        val sampleRate = if (valid && format.sampleRate > 0) format.sampleRate else 44100
        val encodingLabel = if (valid) encodingDisplayName(format.encoding) else "PCM_16BIT"
        val isMono = valid && channels == 1
        _telemetry.update {
            StereoSplitAudioTelemetry(
                activeRole = role,
                channelCount = channels,
                sampleRateHz = sampleRate,
                encodingName = encodingLabel,
                isMonoSource = isMono,
                isSplitActive = role != PcmSpeakerChannelRole.STEREO && !isMono
            )
        }
    }

    private fun bytesPerSampleFor(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private fun encodingDisplayName(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_16BIT -> "PCM_16BIT"
        C.ENCODING_PCM_24BIT -> "PCM_24BIT"
        C.ENCODING_PCM_32BIT -> "PCM_32BIT"
        C.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
        else -> "PCM"
    }
}

/**
 * Shared singleton bridge connecting ListenTogetherCoordinator with the Media3 ExoPlayer
 * [StereoSplitAudioProcessor] attached inside [UsmanPlaybackService].
 */
@UnstableApi
object StereoSplitAudioController {
    val audioProcessor: StereoSplitAudioProcessor = StereoSplitAudioProcessor()

    val telemetry: StateFlow<StereoSplitAudioTelemetry> = audioProcessor.telemetry

    fun setChannelRole(role: PcmSpeakerChannelRole) {
        audioProcessor.setChannelRole(role)
    }

    fun resetToNormalStereo() {
        audioProcessor.resetToStereo()
    }
}
