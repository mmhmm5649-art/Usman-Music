package com.usman.musicplayer.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.usman.musicplayer.MainActivity
import com.usman.musicplayer.util.DeviceUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidTvForegroundAudioStabilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AudioEffectsManager.init(context)
        AudioEffectsManager.resetToDefaults()
        AudioSpectrumManager.release()
    }

    @After
    fun tearDown() {
        AudioSpectrumManager.release()
        AudioEffectsManager.resetToDefaults()
    }

    private fun createStereoSinePcm16(
        sampleRate: Int = 48_000,
        freqHz: Double = 440.0,
        amplitude: Double = 0.35,
        frames: Int = 2048
    ): ByteBuffer {
        val buf = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (n in 0 until frames) {
            val sample = (sin(2.0 * PI * freqHz * n / sampleRate) * amplitude * 32767.0)
                .toInt()
                .coerceIn(-32768, 32767)
                .toShort()
            buf.putShort(sample)
            buf.putShort(sample)
        }
        buf.flip()
        return buf
    }

    @Test
    fun defaultStudioReferencePreset_engagesBitIdenticalUnityBypass_andTogglesCleanlyWithEffects() {
        val processor = AudioEffectsManager.getAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        // Default Studio Reference has 0 dB across all 10 bands and all enhancement modules off.
        assertTrue(
            "Default flat Studio Reference preset should engage zero-CPU identity bypass",
            processor.isIdentityBypassForDiagnostics()
        )

        val input = createStereoSinePcm16()
        val expectedBytes = ByteArray(input.remaining())
        input.duplicate().get(expectedBytes)

        processor.queueInput(input)
        val output = processor.output
        val actualBytes = ByteArray(output.remaining())
        output.get(actualBytes)
        assertTrue(
            "Identity bypass must pass PCM16 samples through bit-identically",
            expectedBytes.contentEquals(actualBytes)
        )

        // Enabling EQ boost, Bass Boost, Surround, and Concert Hall must disable identity bypass
        // and process cleanly with zero-allocation processStereoFrame().
        AudioEffectsManager.setBandGain(1, 500) // +5.0 dB at 62Hz
        AudioEffectsManager.setBassBoostEnabled(true)
        AudioEffectsManager.setBassBoostStrength(700)
        AudioEffectsManager.setVirtualizerEnabled(true)
        AudioEffectsManager.setVirtualizerStrength(600)
        AudioEffectsManager.selectConcertHallPreset(ConcertHallPreset.GRAND_CONCERT_HALL)

        val activeInput = createStereoSinePcm16(freqHz = 80.0)
        processor.queueInput(activeInput)
        val activeOutput = processor.output
        assertFalse(
            "Active DSP modules must exit identity bypass",
            processor.isIdentityBypassForDiagnostics()
        )
        assertEquals(2048 * 4, activeOutput.remaining())

        // Verify valid non-silent, non-clipping audio output
        var peakAbs = 0
        while (activeOutput.remaining() >= 2) {
            val s = abs(activeOutput.getShort().toInt())
            if (s > peakAbs) peakAbs = s
        }
        assertTrue("Active DSP output must remain audible and clean (peakAbs=$peakAbs)", peakAbs in 1000..32767)

        // Turning Master OFF returns immediately to bit-identical bypass
        AudioEffectsManager.setMasterEnabled(false)
        val offInput = createStereoSinePcm16()
        val offExpected = ByteArray(offInput.remaining())
        offInput.duplicate().get(offExpected)
        processor.queueInput(offInput)
        val offOutput = processor.output
        val offActual = ByteArray(offOutput.remaining())
        offOutput.get(offActual)
        assertTrue("Master OFF must restore identity bypass", processor.isIdentityBypassForDiagnostics())
        assertTrue("Master OFF must output bit-identical PCM", offExpected.contentEquals(offActual))
    }

    @Test
    fun attachAudioSession_isStrictlyIdempotentAcrossRepeatedForegroundCycles() {
        val processor = AudioEffectsManager.getAudioProcessor()
        AudioEffectsManager.releaseEffects()

        // First attach for session 555 should increment processor version once
        val versionBeforeFirstAttach = processor.getTargetVersionForDiagnostics()
        AudioEffectsManager.attachAudioSession(context, 555)
        val versionAfterFirstAttach = processor.getTargetVersionForDiagnostics()
        assertEquals(
            "First attach for a new session should push DSP state once",
            versionBeforeFirstAttach + 1,
            versionAfterFirstAttach
        )

        // 15 repeated foreground / play-resume attach calls for the SAME session must be strict no-ops
        repeat(15) {
            AudioEffectsManager.init(context)
            AudioEffectsManager.attachAudioSession(context, 555)
        }
        assertEquals(
            "Repeated init/attach calls for the same audio session must NOT bump DSP config version",
            versionAfterFirstAttach,
            processor.getTargetVersionForDiagnostics()
        )
    }

    @Test
    fun audioSpectrumManager_skipsNativeVisualizerAttachmentOnAndroidTv() {
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)
        Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)

        assertTrue("Test environment should detect Android TV", DeviceUtils.isTvDevice(context))
        assertFalse(
            "AudioSpectrumManager processing must default to disabled until phone visualizer mounts",
            AudioSpectrumManager.isProcessingEnabled
        )

        AudioSpectrumManager.attachAudioSession(context, 777)

        assertFalse(
            "Native android.media.audiofx.Visualizer must NEVER attach on Android TV",
            AudioSpectrumManager.isVisualizerAttachedForDiagnostics()
        )
        assertEquals(
            "Active visualizer session must remain unset (-1) on Android TV",
            -1,
            AudioSpectrumManager.getActiveSessionIdForDiagnostics()
        )
    }

    @Test
    fun mainActivityOnAndroidTv_preservesDisplayModeAndAudioSessionAcrossMinimizeReopenCycles() {
        Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        context.resources.configuration.uiMode =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                Configuration.UI_MODE_TYPE_TELEVISION

        AudioEffectsManager.attachAudioSession(context, 888)
        val processor = AudioEffectsManager.getAudioProcessor()
        val versionAfterAttach = processor.getTargetVersionForDiagnostics()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()

        val activity = controller.get()
        assertEquals(
            "Android TV must NOT override preferredDisplayModeId on the foreground window",
            0,
            activity.window.attributes.preferredDisplayModeId
        )

        // Simulate 10 minimize -> reopen cycles on Android TV
        repeat(10) {
            controller.pause().stop()
            controller.start().resume()
        }

        assertEquals(
            "Repeated TV minimize/reopen cycles must not disturb DSP config version",
            versionAfterAttach,
            processor.getTargetVersionForDiagnostics()
        )
        assertFalse(
            "Native Visualizer must remain unattached across TV foreground/background cycles",
            AudioSpectrumManager.isVisualizerAttachedForDiagnostics()
        )

        controller.pause().stop().destroy()
    }

    @Test
    fun concertHallBypassTransition_clearsStaleReverbTail_andPhoneDetectionRemainsIntact() {
        // Phone environment (default Robolectric without FEATURE_LEANBACK or TV uiMode)
        assertFalse("Default phone context must NOT be misclassified as Android TV", DeviceUtils.isTvDevice(context))

        val sr = 48_000
        val pipeline = StudioDspPipeline(sr)
        val reverbOn = AudioEffectsState(
            masterEnabled = true,
            equalizerEnabled = false,
            concertHall = ConcertHallState(
                enabled = true,
                hallSize = 90,
                roomDepth = 80,
                reverbAmount = 85,
                decayTimeSec = 3.5f,
                earlyReflections = 75,
                wetDryMix = 70,
                stereoWidth = 90
            )
        )
        pipeline.applyConfig(reverbOn)
        assertFalse(pipeline.isIdentityBypass)

        // Excite reverb tank with signal
        for (n in 0 until 1024) {
            val s = sin(2.0 * PI * 800.0 * n / sr) * 0.6
            pipeline.processStereoFrame(s, s)
        }

        // Transition into identity bypass -> must flush reverb delay buffers
        pipeline.applyConfig(AudioEffectsState(masterEnabled = true, equalizerEnabled = false))
        assertTrue(pipeline.isIdentityBypass)

        // Re-enable reverb with silence input -> must emit clean silence (no stale impulse pop!)
        pipeline.applyConfig(reverbOn)
        var maxStaleEnergy = 0.0
        for (n in 0 until 2000) {
            pipeline.processStereoFrame(0.0, 0.0)
            maxStaleEnergy = maxOf(maxStaleEnergy, abs(pipeline.outL), abs(pipeline.outR))
        }
        assertTrue(
            "Re-enabling reverb after bypass must start with clean zeroed delay buffers (maxStaleEnergy=$maxStaleEnergy)",
            maxStaleEnergy < 1e-6
        )
    }
}
