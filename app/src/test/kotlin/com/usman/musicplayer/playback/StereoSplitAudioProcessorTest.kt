package com.usman.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Verifies real decoded PCM channel separation in StereoSplitAudioProcessor using
 * a dual-tone stereo test signal:
 * - Left channel  = 440 Hz sine wave
 * - Right channel = 1000 Hz sine wave
 */
class StereoSplitAudioProcessorTest {

    private val sampleRate = 48_000
    private val frameCount = 4800 // 100 ms -> exact integer cycles for both 440 Hz (44 cycles) and 1000 Hz (100 cycles)

    private fun buildStereoPcm16Buffer(
        leftFreqHz: Double = 440.0,
        rightFreqHz: Double = 1000.0,
        amplitude: Double = 0.6
    ): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(frameCount * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (n in 0 until frameCount) {
            val t = n.toDouble() / sampleRate
            val leftSample = (sin(2.0 * PI * leftFreqHz * t) * amplitude * Short.MAX_VALUE).roundToInt().toShort()
            val rightSample = (sin(2.0 * PI * rightFreqHz * t) * amplitude * Short.MAX_VALUE).roundToInt().toShort()
            buf.putShort(leftSample)
            buf.putShort(rightSample)
        }
        buf.flip()
        return buf
    }

    private fun buildStereoPcmFloatBuffer(
        leftFreqHz: Double = 440.0,
        rightFreqHz: Double = 1000.0,
        amplitude: Float = 0.6f
    ): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(frameCount * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (n in 0 until frameCount) {
            val t = n.toDouble() / sampleRate
            buf.putFloat((sin(2.0 * PI * leftFreqHz * t) * amplitude).toFloat())
            buf.putFloat((sin(2.0 * PI * rightFreqHz * t) * amplitude).toFloat())
        }
        buf.flip()
        return buf
    }

    private fun dftMagnitude(samples: DoubleArray, targetFreqHz: Double): Double {
        var re = 0.0
        var im = 0.0
        val n = samples.size
        for (i in 0 until n) {
            val angle = 2.0 * PI * targetFreqHz * i / sampleRate
            re += samples[i] * cos(angle)
            im -= samples[i] * sin(angle)
        }
        return (2.0 / n) * sqrt(re * re + im * im)
    }

    private fun decodePcm16Stereo(output: ByteBuffer): Pair<DoubleArray, DoubleArray> {
        val outFrames = output.remaining() / 4
        val left = DoubleArray(outFrames)
        val right = DoubleArray(outFrames)
        for (i in 0 until outFrames) {
            left[i] = output.short.toDouble() / Short.MAX_VALUE
            right[i] = output.short.toDouble() / Short.MAX_VALUE
        }
        return left to right
    }

    private fun decodePcmFloatStereo(output: ByteBuffer): Pair<DoubleArray, DoubleArray> {
        val outFrames = output.remaining() / 8
        val left = DoubleArray(outFrames)
        val right = DoubleArray(outFrames)
        for (i in 0 until outFrames) {
            left[i] = output.float.toDouble()
            right[i] = output.float.toDouble()
        }
        return left to right
    }

    @Test
    fun stereoMode_preservesOriginalLeftAndRightUnmodified() {
        val processor = StereoSplitAudioProcessor()
        processor.setChannelRole(PcmSpeakerChannelRole.STEREO)
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        processor.queueInput(buildStereoPcm16Buffer())
        val (outL, outR) = decodePcm16Stereo(processor.getOutput())

        assertEquals(frameCount, outL.size)
        // Left keeps 440 Hz only; Right keeps 1000 Hz only
        assertTrue(dftMagnitude(outL, 440.0) > 0.55)
        assertTrue(dftMagnitude(outL, 1000.0) < 0.01)
        assertTrue(dftMagnitude(outR, 1000.0) > 0.55)
        assertTrue(dftMagnitude(outR, 440.0) < 0.01)
        assertFalse(processor.telemetry.value.isMonoSource)
    }

    @Test
    fun leftChannelRole_outputsPureLeftChannelOnBothSpeakersWithoutRightBleedOrClipping() {
        val processor = StereoSplitAudioProcessor()
        processor.setChannelRole(PcmSpeakerChannelRole.LEFT)
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        processor.queueInput(buildStereoPcm16Buffer())
        val (outL, outR) = decodePcm16Stereo(processor.getOutput())

        assertEquals(frameCount, outL.size)
        // Both L and R output must carry 440 Hz and ~0 energy at 1000 Hz
        assertTrue("Expected strong 440 Hz in outL", dftMagnitude(outL, 440.0) > 0.55)
        assertTrue("Expected zero 1000 Hz bleed in outL", dftMagnitude(outL, 1000.0) < 0.01)
        assertTrue("Expected strong 440 Hz duplicated to outR", dftMagnitude(outR, 440.0) > 0.55)
        assertTrue("Expected zero 1000 Hz bleed in outR", dftMagnitude(outR, 1000.0) < 0.01)
        for (i in outL.indices) {
            assertEquals("Output L and R must match for LEFT speaker", outL[i], outR[i], 1e-6)
            assertTrue("No clipping allowed", abs(outL[i]) <= 1.0)
        }
    }

    @Test
    fun rightChannelRole_outputsPureRightChannelOnBothSpeakersWithoutLeftBleed() {
        val processor = StereoSplitAudioProcessor()
        processor.setChannelRole(PcmSpeakerChannelRole.RIGHT)
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        processor.queueInput(buildStereoPcm16Buffer())
        val (outL, outR) = decodePcm16Stereo(processor.getOutput())

        assertEquals(frameCount, outL.size)
        // Both L and R output must carry 1000 Hz and ~0 energy at 440 Hz
        assertTrue("Expected strong 1000 Hz in outL", dftMagnitude(outL, 1000.0) > 0.55)
        assertTrue("Expected zero 440 Hz bleed in outL", dftMagnitude(outL, 440.0) < 0.01)
        assertTrue("Expected strong 1000 Hz in outR", dftMagnitude(outR, 1000.0) > 0.55)
        assertTrue("Expected zero 440 Hz bleed in outR", dftMagnitude(outR, 440.0) < 0.01)
        for (i in outL.indices) {
            assertEquals("Output L and R must match for RIGHT speaker", outL[i], outR[i], 1e-6)
        }
    }

    @Test
    fun pcmFloatAndMidStreamRoleSwitching_workWithoutReconfiguringPlayer() {
        val processor = StereoSplitAudioProcessor()
        processor.setChannelRole(PcmSpeakerChannelRole.STEREO)
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        // Switch mid-track to LEFT without reconfiguring
        processor.setChannelRole(PcmSpeakerChannelRole.LEFT)
        processor.queueInput(buildStereoPcmFloatBuffer())
        val (leftL, leftR) = decodePcmFloatStereo(processor.getOutput())
        assertTrue(dftMagnitude(leftL, 440.0) > 0.55)
        assertTrue(dftMagnitude(leftR, 1000.0) < 0.01)

        // Switch mid-track to RIGHT without reconfiguring
        processor.setChannelRole(PcmSpeakerChannelRole.RIGHT)
        processor.queueInput(buildStereoPcmFloatBuffer())
        val (rightL, rightR) = decodePcmFloatStereo(processor.getOutput())
        assertTrue(dftMagnitude(rightL, 1000.0) > 0.55)
        assertTrue(dftMagnitude(rightR, 440.0) < 0.01)

        // Switch back to STEREO passthrough
        processor.setChannelRole(PcmSpeakerChannelRole.STEREO)
        processor.queueInput(buildStereoPcmFloatBuffer())
        val (stereoL, stereoR) = decodePcmFloatStereo(processor.getOutput())
        assertTrue(dftMagnitude(stereoL, 440.0) > 0.55)
        assertTrue(dftMagnitude(stereoR, 1000.0) > 0.55)
    }

    @Test
    fun monoSource_isDetectedInTelemetryAndPassesThroughSafely() {
        val processor = StereoSplitAudioProcessor()
        processor.setChannelRole(PcmSpeakerChannelRole.LEFT)
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
        processor.flush()

        val monoBuf = ByteBuffer.allocateDirect(frameCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (n in 0 until frameCount) {
            val t = n.toDouble() / sampleRate
            monoBuf.putShort((sin(2.0 * PI * 440.0 * t) * 0.5 * Short.MAX_VALUE).roundToInt().toShort())
        }
        monoBuf.flip()

        processor.queueInput(monoBuf)
        val out = processor.getOutput()

        assertTrue(processor.telemetry.value.isMonoSource)
        assertEquals(1, processor.telemetry.value.channelCount)
        assertEquals(frameCount * 2, out.remaining())
    }
}
