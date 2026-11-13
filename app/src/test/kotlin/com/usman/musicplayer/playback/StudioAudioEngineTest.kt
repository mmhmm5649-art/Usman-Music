package com.usman.musicplayer.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioAudioEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AudioEffectsManager.init(context)
        AudioEffectsManager.resetToDefaults()
    }

    private fun generateStereoSinePcm16(
        sampleRate: Int,
        freqL: Double,
        freqR: Double = freqL,
        amplitude: Double = 0.25,
        frames: Int = 2048
    ): ByteBuffer {
        val buf = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (n in 0 until frames) {
            val sL = (sin(2.0 * PI * freqL * n / sampleRate) * amplitude * 32767.0).toInt().toShort()
            val sR = (sin(2.0 * PI * freqR * n / sampleRate) * amplitude * 32767.0).toInt().toShort()
            buf.putShort(sL)
            buf.putShort(sR)
        }
        buf.flip()
        return buf
    }

    private fun computeRms(pcm16Stereo: ByteBuffer): Pair<Double, Double> {
        val copy = pcm16Stereo.duplicate().order(ByteOrder.nativeOrder())
        var sumSqL = 0.0
        var sumSqR = 0.0
        var frames = 0
        while (copy.remaining() >= 4) {
            val l = copy.getShort() / 32768.0
            val r = copy.getShort() / 32768.0
            sumSqL += l * l
            sumSqR += r * r
            frames++
        }
        if (frames == 0) return Pair(0.0, 0.0)
        return Pair(sqrt(sumSqL / frames), sqrt(sumSqR / frames))
    }

    @Test
    fun tenBandEqualizer_has10BandsAnd13BuiltInPresetsPlusCustomPresetLifecycle() {
        val state0 = AudioEffectsManager.effectsState.value
        assertEquals(10, state0.bands.size)
        assertEquals(
            listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
            state0.bands.map { it.centerFreqHz }
        )

        val expectedBuiltIns = listOf(
            "Studio Reference", "Deep Bass", "Punchy Bass", "Pure Vocal",
            "Crystal Clear", "Concert", "Dance", "Cinematic",
            "Classical", "Rock", "Pop", "Acoustic", "Custom"
        )
        assertTrue(state0.presets.containsAll(expectedBuiltIns))

        // Adjust band 0 (+6.0 dB) and save custom EQ preset
        AudioEffectsManager.setBandGain(0, 600)
        assertTrue(AudioEffectsManager.saveCustomEqPreset("Telugu Club EQ"))
        val afterSave = AudioEffectsManager.effectsState.value
        val savedPreset = afterSave.customEqPresets.first { it.name == "Telugu Club EQ" }
        assertEquals(6.0f, savedPreset.gainsDb[0], 0.05f)

        // Rename custom EQ preset
        assertTrue(AudioEffectsManager.renameCustomEqPreset(savedPreset.id, "Mass Theatre EQ"))
        assertEquals("Mass Theatre EQ", AudioEffectsManager.effectsState.value.currentPresetName)

        // Delete custom EQ preset
        assertTrue(AudioEffectsManager.deleteCustomEqPreset(savedPreset.id))
        assertTrue(AudioEffectsManager.effectsState.value.customEqPresets.none { it.id == savedPreset.id })
    }

    @Test
    fun deepBassVsPunchyBass_emphasizeDistinctFrequenciesAndPreserveVocalClarity() {
        val sr = 48000
        val pipelineDeep = StudioDspPipeline(sr)
        val pipelinePunchy = StudioDspPipeline(sr)

        val baseState = AudioEffectsState(
            masterEnabled = true,
            equalizerEnabled = false,
            utilities = ProUtilitiesState(limiterEnabled = false)
        )

        // Deep Bass only: emphasizes 45Hz depth
        pipelineDeep.applyConfig(
            baseState.copy(
                bassEngine = BassEngineState(
                    enabled = true,
                    deepBass = 90,
                    subBass = 75,
                    punchyBass = 0,
                    bassFrequencyHz = 95,
                    bassTightness = 85
                )
            )
        )

        // Punchy Bass only: emphasizes tight 95Hz kick + vocal anti-mud cut at ~215Hz
        pipelinePunchy.applyConfig(
            baseState.copy(
                bassEngine = BassEngineState(
                    enabled = true,
                    deepBass = 0,
                    subBass = 0,
                    punchyBass = 90,
                    bassFrequencyHz = 95,
                    bassTightness = 90
                )
            )
        )

        fun measureGainAtFreq(pipeline: StudioDspPipeline, freqHz: Double): Double {
            pipeline.resetState()
            var sumSq = 0.0
            val total = 2400
            val settle = 400
            for (n in 0 until total) {
                val x = sin(2.0 * PI * freqHz * n / sr) * 0.2
                val out = pipeline.processStereoSample(x, x)
                if (n >= settle) {
                    sumSq += out.first * out.first
                }
            }
            val outRms = sqrt(sumSq / (total - settle))
            val inRms = 0.2 / sqrt(2.0)
            return outRms / inRms
        }

        val deepAt45Hz = measureGainAtFreq(pipelineDeep, 45.0)
        val punchyAt45Hz = measureGainAtFreq(pipelinePunchy, 45.0)
        assertTrue("Deep Bass should boost 45Hz more than Punchy Bass ($deepAt45Hz vs $punchyAt45Hz)", deepAt45Hz > punchyAt45Hz * 1.25)

        val punchyAt95Hz = measureGainAtFreq(pipelinePunchy, 95.0)
        val punchyAt215HzMud = measureGainAtFreq(pipelinePunchy, 215.0)
        assertTrue("Punchy Bass should boost 95Hz kick ($punchyAt95Hz) while cutting 215Hz vocal mud ($punchyAt215HzMud)", punchyAt95Hz > 1.35 && punchyAt215HzMud < 0.98)
    }

    @Test
    fun concertHallReverb_generatesNaturalDecayTailAfterImpulseAndSupports5Presets() {
        assertEquals(
            listOf("Small Hall", "Grand Concert Hall", "Royal Auditorium", "Live Stage", "Cathedral"),
            ConcertHallPreset.entries.map { it.displayName }
        )

        val sr = 44100
        val pipeline = StudioDspPipeline(sr)
        AudioEffectsManager.selectConcertHallPreset(ConcertHallPreset.CATHEDRAL)
        pipeline.applyConfig(AudioEffectsManager.effectsState.value.copy(equalizerEnabled = false))

        // Feed short 800Hz burst for 20ms then silence, verify reverb tail continues smoothly
        for (n in 0 until 880) {
            val s = sin(2.0 * PI * 800.0 * n / sr) * 0.5
            pipeline.processStereoSample(s, s)
        }
        var tailEnergy = 0.0
        for (n in 0 until 4000) {
            val out = pipeline.processStereoSample(0.0, 0.0)
            tailEnergy += abs(out.first) + abs(out.second)
        }
        assertTrue("Concert Hall reverb should produce non-zero acoustic decay tail after input stops", tailEnergy > 0.5)
    }

    @Test
    fun surroundSpatialEngine_expandsStereoSideWhileStrictlyPreservingMonoSum() {
        val sr = 48000
        val pipelineBypass = StudioDspPipeline(sr)
        val pipelineSurround = StudioDspPipeline(sr)

        val baseState = AudioEffectsState(
            masterEnabled = true,
            equalizerEnabled = false,
            utilities = ProUtilitiesState(limiterEnabled = false)
        )
        pipelineBypass.applyConfig(baseState)
        pipelineSurround.applyConfig(
            baseState.copy(
                surround = SurroundSpatialState(
                    enabled = true,
                    intensity = 85,
                    virtualSurround = 80,
                    stereoWidth = 85,
                    spatialDepth = 75,
                    sound3D = 70,
                    stereoExpansion = 80,
                    immersiveMode = true
                )
            )
        )

        var maxMonoDiff = 0.0
        var sideEnergyBypass = 0.0
        var sideEnergySurround = 0.0

        for (n in 0 until 2000) {
            // Distinct L and R signals (mid + side content at 1kHz & 1.5kHz)
            val inL = sin(2.0 * PI * 1000.0 * n / sr) * 0.3 + sin(2.0 * PI * 1500.0 * n / sr) * 0.15
            val inR = sin(2.0 * PI * 1000.0 * n / sr) * 0.3 - sin(2.0 * PI * 1500.0 * n / sr) * 0.15

            val outB = pipelineBypass.processStereoSample(inL, inR)
            val outS = pipelineSurround.processStereoSample(inL, inR)

            if (n > 200) {
                // Mono sum (L + R) must match unmodified Mid (inL + inR) -> zero phase cancellation!
                val monoIn = inL + inR
                val monoOut = outS.first + outS.second
                maxMonoDiff = maxOf(maxMonoDiff, abs(monoIn - monoOut))

                sideEnergyBypass += abs(outB.first - outB.second)
                sideEnergySurround += abs(outS.first - outS.second)
            }
        }

        assertTrue("3D Surround must preserve mono sum compatibility (maxDiff=$maxMonoDiff)", maxMonoDiff < 1e-4)
        assertTrue("3D Surround must expand stereo side image ($sideEnergySurround > $sideEnergyBypass)", sideEnergySurround > sideEnergyBypass * 1.2)
    }

    @Test
    fun crystalSoundEngine_enhancesVocalAndTreblePresenceCleanly() {
        val sr = 48000
        val pipeline = StudioDspPipeline(sr)
        pipeline.applyConfig(
            AudioEffectsState(
                masterEnabled = true,
                equalizerEnabled = false,
                crystalSound = CrystalSoundState(
                    enabled = true,
                    vocalClarity = 85,
                    crystalTreble = 80,
                    detailEnhancer = 75,
                    highFrequencyControl = 65,
                    dynamicRange = 50,
                    softClarity = 30
                ),
                utilities = ProUtilitiesState(limiterEnabled = false)
            )
        )

        var sumSqVocal = 0.0
        val frames = 1600
        for (n in 0 until frames) {
            val s = sin(2.0 * PI * 2600.0 * n / sr) * 0.2
            val out = pipeline.processStereoSample(s, s)
            if (n >= 300) sumSqVocal += out.first * out.first
        }
        val outRms = sqrt(sumSqVocal / (frames - 300))
        val inRms = 0.2 / sqrt(2.0)
        assertTrue("Crystal Sound Engine should boost 2.6kHz vocal presence", outRms > inRms * 1.25)
    }

    @Test
    fun peakLimiterAndBalance_preventClippingAndPanChannelsAccurately() {
        val sr = 48000
        val pipeline = StudioDspPipeline(sr)
        // Extreme boost (+12dB preamp + +6dB output + Deep Punchy Bass) with -1.0 dBFS limiter ceiling
        pipeline.applyConfig(
            AudioEffectsState(
                masterEnabled = true,
                equalizerEnabled = true,
                bands = StudioEffectDefaults.defaultBands(List(10) { 10f }),
                bassEngine = BassEngineState(enabled = true, deepBass = 100, punchyBass = 100, bassGainDb = 8f),
                utilities = ProUtilitiesState(
                    preampDb = 12f,
                    outputGainDb = 6f,
                    limiterEnabled = true,
                    limiterThresholdDb = -1.0f // ~0.891 linear ceiling
                )
            )
        )

        var maxPeak = 0.0
        for (n in 0 until 2000) {
            val s = sin(2.0 * PI * 75.0 * n / sr) * 0.85
            val out = pipeline.processStereoSample(s, s)
            maxPeak = maxOf(maxPeak, abs(out.first), abs(out.second))
        }
        assertTrue("Limiter must prevent clipping above -1.0 dBFS ceiling (maxPeak=$maxPeak)", maxPeak <= 0.90)
        assertTrue("Limiter should report positive peak reduction dB", pipeline.lastPeakReductionDb > 0f)

        // Test hard Left balance (-1.0f) mutes Right channel
        pipeline.applyConfig(
            AudioEffectsState(
                masterEnabled = true,
                equalizerEnabled = false,
                utilities = ProUtilitiesState(balance = -1.0f, limiterEnabled = false)
            )
        )
        val panned = pipeline.processStereoSample(0.5, 0.5)
        assertEquals(0.5, panned.first, 1e-4)
        assertEquals(0.0, panned.second, 1e-4)
    }

    @Test
    fun media3AudioProcessor_processesLivePcmAcrossSongChangesPauseResumeAndSessionRecreation() {
        val processor = AudioEffectsManager.getAudioProcessor()
        val format44k = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val outFormat = processor.configure(format44k)
        processor.flush()
        assertEquals(44100, outFormat.sampleRate)

        // Activate "Deep Punchy Bass" quick preset
        AudioEffectsManager.selectQuickPreset("deep_punchy_bass")
        assertEquals("Deep Punchy Bass", AudioEffectsManager.effectsState.value.activeQuickPresetName)

        // Song 1 PCM buffer
        val inputSong1 = generateStereoSinePcm16(44100, 65.0, 65.0, amplitude = 0.20)
        val inRms1 = computeRms(inputSong1).first
        processor.queueInput(inputSong1)
        val outSong1 = processor.output
        val outRms1 = computeRms(outSong1).first
        assertTrue("StudioFeelAudioProcessor must actively boost 65Hz bass on Song 1 ($outRms1 > $inRms1)", outRms1 > inRms1 * 1.25)

        // Simulate Pause / Flush / Song 2 Transition (settings must persist!)
        processor.flush()
        val inputSong2 = generateStereoSinePcm16(44100, 65.0, 65.0, amplitude = 0.20)
        processor.queueInput(inputSong2)
        val outRms2 = computeRms(processor.output).first
        assertTrue("Settings must remain active across song transitions and flushes ($outRms2 > $inRms1)", outRms2 > inRms1 * 1.25)

        // Simulate Audio Session Recreation (e.g. session 101 -> 202)
        AudioEffectsManager.attachAudioSession(context, 101)
        AudioEffectsManager.attachAudioSession(context, 101) // duplicate call is no-op
        AudioEffectsManager.attachAudioSession(context, 202) // session recreation

        val inputAfterSessionRecreate = generateStereoSinePcm16(44100, 65.0, 65.0, amplitude = 0.20)
        processor.queueInput(inputAfterSessionRecreate)
        val outRmsAfterRecreate = computeRms(processor.output).first
        assertTrue("Audio effects must stay active after audio session recreation", outRmsAfterRecreate > inRms1 * 1.25)

        // Release & Cleanup
        AudioEffectsManager.releaseEffects()
        assertEquals(-1, AudioEffectsManager.effectsState.value.availability.audioSessionId)
    }
}
