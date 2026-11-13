package com.usman.musicplayer.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Stereo biquad IIR filter implementing Robert Bristow-Johnson Audio EQ Cookbook formulas.
 * Zero heap allocations per sample.
 */
class StereoBiquadFilter {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // Left channel state
    private var lx1 = 0.0
    private var lx2 = 0.0
    private var ly1 = 0.0
    private var ly2 = 0.0

    // Right channel state
    private var rx1 = 0.0
    private var rx2 = 0.0
    private var ry1 = 0.0
    private var ry2 = 0.0

    fun reset() {
        lx1 = 0.0; lx2 = 0.0; ly1 = 0.0; ly2 = 0.0
        rx1 = 0.0; rx2 = 0.0; ry1 = 0.0; ry2 = 0.0
    }

    fun setBypass() {
        b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
    }

    fun configurePeaking(sampleRate: Double, freqHz: Double, q: Double, gainDb: Double) {
        if (abs(gainDb) < 0.02) {
            setBypass()
            return
        }
        val safeFreq = freqHz.coerceIn(15.0, sampleRate * 0.45)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.2))

        val rawB0 = 1.0 + alpha * a
        val rawB1 = -2.0 * cosW0
        val rawB2 = 1.0 - alpha * a
        val rawA0 = 1.0 + alpha / a
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha / a

        normalize(rawB0, rawB1, rawB2, rawA0, rawA1, rawA2)
    }

    fun configureLowShelf(sampleRate: Double, freqHz: Double, slope: Double, gainDb: Double) {
        if (abs(gainDb) < 0.02) {
            setBypass()
            return
        }
        val safeFreq = freqHz.coerceIn(15.0, sampleRate * 0.45)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val sqrtA = sqrt(a)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope.coerceIn(0.2, 2.0) - 1.0) + 2.0)

        val rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
        val rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
        val rawA0 = (a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha
        val rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha

        normalize(rawB0, rawB1, rawB2, rawA0, rawA1, rawA2)
    }

    fun configureHighShelf(sampleRate: Double, freqHz: Double, slope: Double, gainDb: Double) {
        if (abs(gainDb) < 0.02) {
            setBypass()
            return
        }
        val safeFreq = freqHz.coerceIn(100.0, sampleRate * 0.46)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val sqrtA = sqrt(a)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope.coerceIn(0.2, 2.0) - 1.0) + 2.0)

        val rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
        val rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
        val rawA0 = (a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha
        val rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha

        normalize(rawB0, rawB1, rawB2, rawA0, rawA1, rawA2)
    }

    fun configureLowPass(sampleRate: Double, freqHz: Double, q: Double = 0.7071) {
        val safeFreq = freqHz.coerceIn(20.0, sampleRate * 0.45)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val rawB0 = (1.0 - cosW0) * 0.5
        val rawB1 = 1.0 - cosW0
        val rawB2 = (1.0 - cosW0) * 0.5
        val rawA0 = 1.0 + alpha
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha

        normalize(rawB0, rawB1, rawB2, rawA0, rawA1, rawA2)
    }

    fun configureHighPass(sampleRate: Double, freqHz: Double, q: Double = 0.7071) {
        val safeFreq = freqHz.coerceIn(15.0, sampleRate * 0.45)
        val w0 = 2.0 * PI * safeFreq / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val rawB0 = (1.0 + cosW0) * 0.5
        val rawB1 = -(1.0 + cosW0)
        val rawB2 = (1.0 + cosW0) * 0.5
        val rawA0 = 1.0 + alpha
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha

        normalize(rawB0, rawB1, rawB2, rawA0, rawA1, rawA2)
    }

    private fun normalize(rb0: Double, rb1: Double, rb2: Double, ra0: Double, ra1: Double, ra2: Double) {
        val invA0 = 1.0 / ra0
        b0 = rb0 * invA0
        b1 = rb1 * invA0
        b2 = rb2 * invA0
        a1 = ra1 * invA0
        a2 = ra2 * invA0
    }

    fun processLeft(x: Double): Double {
        val y = b0 * x + b1 * lx1 + b2 * lx2 - a1 * ly1 - a2 * ly2
        lx2 = lx1; lx1 = x
        ly2 = ly1; ly1 = y
        return y
    }

    fun processRight(x: Double): Double {
        val y = b0 * x + b1 * rx1 + b2 * rx2 - a1 * ry1 - a2 * ry2
        rx2 = rx1; rx1 = x
        ry2 = ry1; ry1 = y
        return y
    }
}

/**
 * Low-pass damped Comb Filter for Concert Hall Reverb tank.
 */
private class DampedCombFilter(maxBufferSize: Int) {
    private val buffer = FloatArray(maxBufferSize.coerceAtLeast(64))
    private var delaySamples = 64
    private var idx = 0
    private var filterStore = 0f

    fun setDelay(samples: Int) {
        delaySamples = samples.coerceIn(16, buffer.size - 1)
        if (idx >= delaySamples) idx = 0
    }

    fun reset() {
        buffer.fill(0f)
        idx = 0
        filterStore = 0f
    }

    fun process(input: Float, feedback: Float, damp1: Float, damp2: Float): Float {
        val output = buffer[idx]
        filterStore = output * damp2 + filterStore * damp1
        buffer[idx] = input + filterStore * feedback
        idx++
        if (idx >= delaySamples) idx = 0
        return output
    }
}

/**
 * Schroeder All-pass Diffuser for silky reverb tail without metallic flutter.
 */
private class AllPassDiffuser(maxBufferSize: Int) {
    private val buffer = FloatArray(maxBufferSize.coerceAtLeast(32))
    private var delaySamples = 32
    private var idx = 0

    fun setDelay(samples: Int) {
        delaySamples = samples.coerceIn(8, buffer.size - 1)
        if (idx >= delaySamples) idx = 0
    }

    fun reset() {
        buffer.fill(0f)
        idx = 0
    }

    fun process(input: Float): Float {
        val bufOut = buffer[idx]
        val output = -input + bufOut
        buffer[idx] = input + bufOut * 0.5f
        idx++
        if (idx >= delaySamples) idx = 0
        return output
    }
}

/**
 * Pure Concert Hall Stereo Reverb Engine.
 * Features:
 * - Vocal-preserving high-pass (~220Hz) so bass & vocals stay crisp and dry
 * - Pre-delay ring buffer (Room Depth)
 * - 4 stereo early-reflection taps (Early Reflections & Hall Size)
 * - 4 parallel damped Comb Filters + 2 All-Pass diffusers per channel (Decay Time & Reverb Amount)
 * - Adjustable Wet/Dry Mix and Stereo Width
 */
class ConcertHallReverbDsp(private val sampleRate: Int) {
    private val srScale = sampleRate / 44100.0
    private val preDelayMax = ((sampleRate * 0.08).toInt()).coerceAtLeast(256)
    private val preDelayL = FloatArray(preDelayMax)
    private val preDelayR = FloatArray(preDelayMax)
    private var preDelayIdx = 0

    private val erMax = ((sampleRate * 0.12).toInt()).coerceAtLeast(512)
    private val erBufL = FloatArray(erMax)
    private val erBufR = FloatArray(erMax)
    private var erIdx = 0

    private val combDelaysBase = intArrayOf(1116, 1277, 1422, 1617)
    private val stereoSpread = 29

    private val combsL = Array(4) { i -> DampedCombFilter(((combDelaysBase[i] + 100) * srScale * 1.45).toInt()) }
    private val combsR = Array(4) { i -> DampedCombFilter(((combDelaysBase[i] + stereoSpread + 100) * srScale * 1.45).toInt()) }

    private val allPassBase = intArrayOf(556, 341)
    private val allPassL = Array(2) { i -> AllPassDiffuser(((allPassBase[i] + 40) * srScale * 1.45).toInt()) }
    private val allPassR = Array(2) { i -> AllPassDiffuser(((allPassBase[i] + stereoSpread + 40) * srScale * 1.45).toInt()) }

    private val inputHpf = StereoBiquadFilter().apply {
        configureHighPass(sampleRate.toDouble(), 210.0, 0.7071)
    }

    private var currentHallSize = -1
    private var preDelaySamples = (sampleRate * 0.018).toInt().coerceIn(8, preDelayMax - 1)
    private val erTapsL = IntArray(4)
    private val erTapsR = IntArray(4)
    private val combFeedbacks = FloatArray(4)
    private var damp1 = 0.35f
    private var damp2 = 0.65f
    private var earlyGain = 0.35f
    private var lateGain = 0.45f
    private var wetMix = 0.25f
    private var dryMix = 0.95f
    private var widthCoeff = 0.8f

    fun reset() {
        preDelayL.fill(0f)
        preDelayR.fill(0f)
        erBufL.fill(0f)
        erBufR.fill(0f)
        preDelayIdx = 0
        erIdx = 0
        inputHpf.reset()
        combsL.forEach { it.reset() }
        combsR.forEach { it.reset() }
        allPassL.forEach { it.reset() }
        allPassR.forEach { it.reset() }
    }

    fun updateParams(state: ConcertHallState) {
        val sizeFactor = 0.55 + (state.hallSize.coerceIn(0, 100) / 100.0) * 0.85
        if (state.hallSize != currentHallSize) {
            currentHallSize = state.hallSize
            for (i in 0 until 4) {
                val baseL = (combDelaysBase[i] * srScale * sizeFactor).toInt()
                val baseR = ((combDelaysBase[i] + stereoSpread) * srScale * sizeFactor).toInt()
                combsL[i].setDelay(baseL)
                combsR[i].setDelay(baseR)
            }
            for (i in 0 until 2) {
                allPassL[i].setDelay((allPassBase[i] * srScale * sizeFactor).toInt())
                allPassR[i].setDelay(((allPassBase[i] + stereoSpread) * srScale * sizeFactor).toInt())
            }
        }

        // Room Depth -> Pre-delay 6ms..48ms
        val depthNorm = state.roomDepth.coerceIn(0, 100) / 100.0
        preDelaySamples = (sampleRate * (0.006 + depthNorm * 0.042)).toInt().coerceIn(8, preDelayMax - 1)

        // Early Reflections tap positions scaled by Hall Size
        val erBaseMs = doubleArrayOf(9.0, 17.0, 26.0, 38.0)
        for (i in 0 until 4) {
            val lSamp = (sampleRate * (erBaseMs[i] * sizeFactor / 1000.0)).toInt().coerceIn(8, erMax - 1)
            val rSamp = (sampleRate * ((erBaseMs[i] + 3.2) * sizeFactor / 1000.0)).toInt().coerceIn(8, erMax - 1)
            erTapsL[i] = lSamp
            erTapsR[i] = rSamp
        }

        // Decay Time (RT60 in seconds) -> exact feedback per comb delay
        val rt60 = state.decayTimeSec.coerceIn(0.4f, 4.8f).toDouble()
        for (i in 0 until 4) {
            val delaySec = (combDelaysBase[i] * srScale * sizeFactor) / sampleRate.toDouble()
            val g = 10.0.pow(-3.0 * delaySec / rt60).coerceIn(0.25, 0.93)
            combFeedbacks[i] = g.toFloat()
        }

        // High-frequency damping keeps hall smooth & non-metallic
        damp1 = (0.28f + (100 - state.hallSize) * 0.0018f).coerceIn(0.20f, 0.55f)
        damp2 = 1.0f - damp1

        earlyGain = (state.earlyReflections.coerceIn(0, 100) / 100f) * 0.55f
        lateGain = (state.reverbAmount.coerceIn(0, 100) / 100f) * 0.65f
        wetMix = (state.wetDryMix.coerceIn(0, 100) / 100f) * 0.85f
        // Gentle dry compensation keeps front vocal crisp & level stable
        dryMix = (1.0f - wetMix * 0.18f).coerceIn(0.75f, 1.0f)
        widthCoeff = (state.stereoWidth.coerceIn(0, 100) / 100f)
    }

    fun processStereo(dryL: Double, dryR: Double): Pair<Double, Double> {
        // High-pass input to reverb tank so bass & sub never turn boomy in hall
        val tankInL = inputHpf.processLeft(dryL).toFloat()
        val tankInR = inputHpf.processRight(dryR).toFloat()

        // Early Reflections buffer
        erBufL[erIdx] = tankInL
        erBufR[erIdx] = tankInR

        var earlyL = 0f
        var earlyR = 0f
        val tapWeights = floatArrayOf(0.42f, 0.28f, 0.18f, 0.12f)
        for (i in 0 until 4) {
            val idxL = (erIdx - erTapsL[i] + erMax) % erMax
            val idxR = (erIdx - erTapsR[i] + erMax) % erMax
            earlyL += erBufL[idxL] * tapWeights[i]
            earlyR += erBufR[idxR] * tapWeights[i]
        }
        erIdx++
        if (erIdx >= erMax) erIdx = 0

        // Pre-delay for late reverb bloom (Room Depth)
        val readPreIdx = (preDelayIdx - preDelaySamples + preDelayMax) % preDelayMax
        val delayedL = preDelayL[readPreIdx]
        val delayedR = preDelayR[readPreIdx]
        preDelayL[preDelayIdx] = tankInL
        preDelayR[preDelayIdx] = tankInR
        preDelayIdx++
        if (preDelayIdx >= preDelayMax) preDelayIdx = 0

        val tankFeedL = delayedL * 0.72f + delayedR * 0.28f
        val tankFeedR = delayedR * 0.72f + delayedL * 0.28f

        var lateL = 0f
        var lateR = 0f
        for (i in 0 until 4) {
            lateL += combsL[i].process(tankFeedL, combFeedbacks[i], damp1, damp2)
            lateR += combsR[i].process(tankFeedR, combFeedbacks[i], damp1, damp2)
        }
        lateL *= 0.25f
        lateR *= 0.25f

        for (i in 0 until 2) {
            lateL = allPassL[i].process(lateL)
            lateR = allPassR[i].process(lateR)
        }

        val wetRawL = earlyL * earlyGain + lateL * lateGain
        val wetRawR = earlyR * earlyGain + lateR * lateGain

        // Stereo width control on wet return
        val wetMid = (wetRawL + wetRawR) * 0.5f
        val wetSide = (wetRawL - wetRawR) * 0.5f * (0.25f + widthCoeff * 1.15f)
        val wetL = wetMid + wetSide
        val wetR = wetMid - wetSide

        val outL = dryL * dryMix + wetL * wetMix
        val outR = dryR * dryMix + wetR * wetMix
        return Pair(outL, outR)
    }
}

/**
 * Full Studio Feel Real-Time DSP Chain.
 * Operates in floating-point stereo precision on decoded PCM frames inside Media3's AudioSink.
 */
class StudioDspPipeline(val sampleRate: Int) {
    private val eqBands = Array(10) { StereoBiquadFilter() }

    // Deep Punchy Bass Engine filters
    private val subBassShelf = StereoBiquadFilter()
    private val deepBassShelf = StereoBiquadFilter()
    private val punchBassPeak = StereoBiquadFilter()
    private val vocalAntiMudDip = StereoBiquadFilter()
    private val bassCrossoverLp = StereoBiquadFilter()

    // HD Crystal Sound Engine filters
    private val vocalClarityPeak = StereoBiquadFilter()
    private val detailEnhancerPeak = StereoBiquadFilter()
    private val crystalTrebleShelf = StereoBiquadFilter()
    private val hfControlShelf = StereoBiquadFilter()
    private val softClarityDeHarsh = StereoBiquadFilter()

    // Loudness contour
    private val loudnessLowShelf = StereoBiquadFilter()
    private val loudnessHighShelf = StereoBiquadFilter()

    // Mono-safe Spatial Side high-pass (keeps low end strictly mono-centered!)
    private val sideLowCutHpf = StereoBiquadFilter().apply {
        configureHighPass(sampleRate.toDouble(), 145.0, 0.7071)
    }
    private val spatialDepthDelayMax = ((sampleRate * 0.004).toInt()).coerceAtLeast(32)
    private val spatialSideDelay = FloatArray(spatialDepthDelayMax)
    private var spatialDelayIdx = 0
    private val pinnaNotchSide = StereoBiquadFilter()

    // Concert Hall Reverb
    private val concertHall = ConcertHallReverbDsp(sampleRate)

    // Bass transient envelope followers (fast attack vs slower average)
    private var bassFastEnv = 0.0
    private var bassSlowEnv = 0.0
    private val fastAtkCoeff = 1.0 - exp(-1.0 / (sampleRate * 0.002))
    private val fastRelCoeff = 1.0 - exp(-1.0 / (sampleRate * 0.035))
    private val slowAtkCoeff = 1.0 - exp(-1.0 / (sampleRate * 0.045))
    private val slowRelCoeff = 1.0 - exp(-1.0 / (sampleRate * 0.200))

    // Dynamic Range macro-RMS envelope
    private var macroRmsEnv = 0.0
    private val macroCoeff = 1.0 - exp(-1.0 / (sampleRate * 0.120))

    // Soft-knee Peak Limiter state
    private var limiterGain = 1.0
    private val limAtkCoeff = 1.0 - exp(-1.0 / (sampleRate * 0.0002))
    private val limRelCoeff = 1.0 - exp(-1.0 / (sampleRate * 0.055))
    @Volatile
    var lastPeakReductionDb: Float = 0f
        private set

    // Cached parameters
    private var masterEnabled = true
    private var eqEnabled = true
    private var preampLinear = 1.0
    private var outputGainLinear = 1.0
    private var leftBalanceGain = 1.0
    private var rightBalanceGain = 1.0

    private var bassEngineEnabled = false
    private var bassGainLinear = 1.0
    private var bassImpactFactor = 0.0
    private var subHarmonicWarmth = 0.0

    private var crystalEnabled = false
    private var dynamicRangeContrast = 0.0

    private var surroundEnabled = false
    private var stereoSideGain = 1.0
    private var surroundCrossAmt = 0.0
    private var spatialDelaySamples = 12

    private var concertHallEnabled = false
    private var limiterEnabled = true
    private var limiterCeilingLinear = 0.944 // -0.5 dBFS

    fun resetState() {
        eqBands.forEach { it.reset() }
        subBassShelf.reset()
        deepBassShelf.reset()
        punchBassPeak.reset()
        vocalAntiMudDip.reset()
        bassCrossoverLp.reset()
        vocalClarityPeak.reset()
        detailEnhancerPeak.reset()
        crystalTrebleShelf.reset()
        hfControlShelf.reset()
        softClarityDeHarsh.reset()
        loudnessLowShelf.reset()
        loudnessHighShelf.reset()
        sideLowCutHpf.reset()
        pinnaNotchSide.reset()
        spatialSideDelay.fill(0f)
        spatialDelayIdx = 0
        concertHall.reset()
        bassFastEnv = 0.0
        bassSlowEnv = 0.0
        macroRmsEnv = 0.0
        limiterGain = 1.0
        lastPeakReductionDb = 0f
    }

    fun applyConfig(state: AudioEffectsState) {
        val sr = sampleRate.toDouble()
        masterEnabled = state.masterEnabled
        eqEnabled = state.equalizerEnabled

        // Preamp + Output gain
        preampLinear = dbToLinear(state.utilities.preampDb.toDouble().coerceIn(-12.0, 12.0))
        outputGainLinear = dbToLinear(state.utilities.outputGainDb.toDouble().coerceIn(-12.0, 6.0))

        // Equal-power Left/Right Balance (-1.0 .. +1.0)
        val bal = state.utilities.balance.coerceIn(-1f, 1f).toDouble()
        leftBalanceGain = if (bal > 0.0) (1.0 - bal).coerceAtLeast(0.0) else 1.0
        rightBalanceGain = if (bal < 0.0) (1.0 + bal).coerceAtLeast(0.0) else 1.0

        // 1. 10-Band EQ weighted by EQ Strength (0..150%)
        val strengthScale = (state.eqStrengthPercent.coerceIn(0, 150) / 100.0)
        for (i in 0 until 10) {
            val band = state.bands.getOrNull(i)
            val freq = band?.centerFreqHz?.toDouble() ?: StudioEffectDefaults.BAND_FREQUENCIES_HZ[i].toDouble()
            val gainDb = if (eqEnabled && band != null) (band.currentGainDb * strengthScale).coerceIn(-14.0, 14.0) else 0.0
            when (i) {
                0 -> eqBands[i].configureLowShelf(sr, freq, 0.9, gainDb)
                9 -> eqBands[i].configureHighShelf(sr, freq, 0.9, gainDb)
                else -> eqBands[i].configurePeaking(sr, freq, 1.41, gainDb)
            }
        }

        // 2. Deep Punchy Bass Engine + BassBoost compatibility knob
        val be = state.bassEngine
        val bbBoostNorm = if (state.utilities.bassBoostEnabled) {
            (state.utilities.bassBoostStrength.coerceIn(0, 1000) / 1000.0)
        } else 0.0
        bassEngineEnabled = be.enabled || bbBoostNorm > 0.01

        if (bassEngineEnabled) {
            val deepAmt = (if (be.enabled) be.deepBass / 100.0 else 0.0) + bbBoostNorm * 0.45
            val punchAmt = (if (be.enabled) be.punchyBass / 100.0 else 0.0) + bbBoostNorm * 0.35
            val subAmt = if (be.enabled) be.subBass / 100.0 else 0.0
            val tightNorm = (be.bassTightness.coerceIn(0, 100) / 100.0)
            val punchFreq = be.bassFrequencyHz.coerceIn(45, 140).toDouble()

            // Sub Bass (~38Hz shelf, max +6.5 dB)
            subBassShelf.configureLowShelf(sr, 40.0, 0.85, (subAmt * 6.5).coerceIn(0.0, 7.0))
            // Deep Bass (~56Hz shelf, max +7.0 dB)
            deepBassShelf.configureLowShelf(sr, 58.0, 0.75, (deepAmt * 6.5).coerceIn(0.0, 7.5))
            // Punchy Bass at bassFrequencyHz, Q tightly controlled by bassTightness (1.1 to 2.8)
            val punchQ = 1.1 + tightNorm * 1.7
            punchBassPeak.configurePeaking(sr, punchFreq, punchQ, (punchAmt * 6.0).coerceIn(0.0, 7.0))
            // Vocal-protecting anti-mud dip at ~215Hz scaled by punch & tightness so vocals stay clean
            val antiMudDb = -((punchAmt * 0.6 + deepAmt * 0.4) * (0.4 + tightNorm * 0.6) * 2.6).coerceIn(0.0, 3.0)
            vocalAntiMudDip.configurePeaking(sr, 215.0, 1.35, antiMudDb)
            // Crossover low-pass for transient impact detector
            bassCrossoverLp.configureLowPass(sr, (punchFreq * 1.45).coerceAtMost(185.0), 0.7071)

            bassGainLinear = dbToLinear(if (be.enabled) be.bassGainDb.toDouble().coerceIn(-6.0, 9.0) else 0.0)
            bassImpactFactor = (if (be.enabled) (be.bassImpact / 100.0) * 0.55 else 0.0)
            subHarmonicWarmth = (subAmt * 0.22).coerceIn(0.0, 0.25)
        } else {
            subBassShelf.setBypass()
            deepBassShelf.setBypass()
            punchBassPeak.setBypass()
            vocalAntiMudDip.setBypass()
            bassGainLinear = 1.0
            bassImpactFactor = 0.0
            subHarmonicWarmth = 0.0
        }

        // 3. HD Crystal Sound Engine
        val cs = state.crystalSound
        crystalEnabled = cs.enabled
        if (crystalEnabled) {
            val vocalDb = (cs.vocalClarity / 100.0) * 4.8
            val detailDb = (cs.detailEnhancer / 100.0) * 4.2
            val trebleDb = (cs.crystalTreble / 100.0) * 5.0
            val hfTiltDb = ((cs.highFrequencyControl - 50) / 50.0) * 4.5
            val softCutDb = -(cs.softClarity / 100.0) * 2.2

            vocalClarityPeak.configurePeaking(sr, 2550.0, 1.1, vocalDb)
            detailEnhancerPeak.configurePeaking(sr, 5400.0, 1.35, detailDb)
            crystalTrebleShelf.configureHighShelf(sr, 11200.0, 0.85, trebleDb)
            hfControlShelf.configureHighShelf(sr, 7800.0, 0.7, hfTiltDb)
            softClarityDeHarsh.configurePeaking(sr, 3550.0, 1.8, softCutDb)
            dynamicRangeContrast = ((cs.dynamicRange - 50) / 50.0) * 0.22
        } else {
            vocalClarityPeak.setBypass()
            detailEnhancerPeak.setBypass()
            crystalTrebleShelf.setBypass()
            hfControlShelf.setBypass()
            softClarityDeHarsh.setBypass()
            dynamicRangeContrast = 0.0
        }

        // 4. Loudness Control (ISO Fletcher-Munson contour)
        if (state.utilities.loudnessEnabled && state.utilities.loudnessGain > 0) {
            val lNorm = state.utilities.loudnessGain.coerceIn(0, 100) / 100.0
            loudnessLowShelf.configureLowShelf(sr, 75.0, 0.75, lNorm * 4.5)
            loudnessHighShelf.configureHighShelf(sr, 10500.0, 0.75, lNorm * 3.0)
        } else {
            loudnessLowShelf.setBypass()
            loudnessHighShelf.setBypass()
        }

        // 5. 3D Surround & Mono-Compatible Spatial Audio
        val sp = state.surround
        val virtFallback = if (state.utilities.virtualizerEnabled) {
            state.utilities.virtualizerStrength.coerceIn(0, 1000) / 1000.0
        } else 0.0
        surroundEnabled = sp.enabled || virtFallback > 0.01

        if (surroundEnabled) {
            val intensity = if (sp.enabled) sp.intensity / 100.0 else virtFallback
            val widthNorm = if (sp.enabled) sp.stereoWidth / 100.0 else (0.5 + virtFallback * 0.3)
            val expNorm = if (sp.enabled) sp.stereoExpansion / 100.0 else virtFallback * 0.5
            val immersiveBonus = if (sp.enabled && sp.immersiveMode) 0.22 else 0.0

            // Side gain >= 0 preserves mono (L+R cancels Side cleanly!)
            stereoSideGain = (0.35 + widthNorm * 1.15 + expNorm * 0.35 + immersiveBonus) *
                (0.65 + intensity * 0.55)
            stereoSideGain = stereoSideGain.coerceIn(0.0, 1.95)

            val vSurround = if (sp.enabled) sp.virtualSurround / 100.0 else virtFallback
            val depth = if (sp.enabled) sp.spatialDepth / 100.0 else virtFallback * 0.5
            val s3d = if (sp.enabled) sp.sound3D / 100.0 else virtFallback * 0.5

            surroundCrossAmt = (vSurround * 0.38 + depth * 0.24 + immersiveBonus * 0.25) * intensity
            surroundCrossAmt = surroundCrossAmt.coerceIn(0.0, 0.55)
            spatialDelaySamples = (sampleRate * (0.00025 + depth * 0.00075))
                .toInt()
                .coerceIn(4, spatialDepthDelayMax - 1)
            pinnaNotchSide.configurePeaking(sr, 1950.0, 1.4, s3d * intensity * 2.8)
        } else {
            stereoSideGain = 1.0
            surroundCrossAmt = 0.0
            pinnaNotchSide.setBypass()
        }

        // 6. Pure Concert Hall Reverb
        concertHallEnabled = state.concertHall.enabled
        if (concertHallEnabled) {
            concertHall.updateParams(state.concertHall)
        }

        // 7. Limiter
        limiterEnabled = state.utilities.limiterEnabled
        val ceilingDb = state.utilities.limiterThresholdDb.coerceIn(-6.0f, -0.1f).toDouble()
        limiterCeilingLinear = dbToLinear(ceilingDb)
    }

    /**
     * Process a single stereo sample pair [inL, inR] -> (outL, outR).
     * Zero allocations; suitable for real-time ExoPlayer audio thread.
     */
    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!masterEnabled) {
            lastPeakReductionDb = 0f
            return Pair(inL, inR)
        }

        // Preamp
        var l = inL * preampLinear
        var r = inR * preampLinear

        // 1. 10-Band EQ
        if (eqEnabled) {
            for (i in 0 until 10) {
                l = eqBands[i].processLeft(l)
                r = eqBands[i].processRight(r)
            }
        }

        // 2. Deep Punchy Bass Engine (with dynamic kick transient shaper & vocal anti-mud separation)
        if (bassEngineEnabled) {
            val lSub = subBassShelf.processLeft(l)
            val rSub = subBassShelf.processRight(r)
            var lBass = punchBassPeak.processLeft(deepBassShelf.processLeft(lSub))
            var rBass = punchBassPeak.processRight(deepBassShelf.processRight(rSub))

            // Anti-mud cut keeps vocal fundamentals clean
            lBass = vocalAntiMudDip.processLeft(lBass)
            rBass = vocalAntiMudDip.processRight(rBass)

            // Subtle sub-harmonic analog warmth on low-pass band only
            if (subHarmonicWarmth > 0.0 || bassImpactFactor > 0.0) {
                val lpL = bassCrossoverLp.processLeft(lBass)
                val lpR = bassCrossoverLp.processRight(rBass)
                val lpEnergy = max(abs(lpL), abs(lpR))

                bassFastEnv += (if (lpEnergy > bassFastEnv) fastAtkCoeff else fastRelCoeff) * (lpEnergy - bassFastEnv)
                bassSlowEnv += (if (lpEnergy > bassSlowEnv) slowAtkCoeff else slowRelCoeff) * (lpEnergy - bassSlowEnv)

                val transientRatio = max(0.0, bassFastEnv - bassSlowEnv)
                val transientBoost = 1.0 + (transientRatio * 3.5 * bassImpactFactor).coerceAtMost(0.55)
                val warmL = lpL + (tanh(lpL * 1.8) - lpL) * subHarmonicWarmth
                val warmR = lpR + (tanh(lpR * 1.8) - lpR) * subHarmonicWarmth

                l = (lBass - lpL) + warmL * transientBoost * bassGainLinear
                r = (rBass - lpR) + warmR * transientBoost * bassGainLinear
            } else {
                l = lBass * bassGainLinear
                r = rBass * bassGainLinear
            }
        }

        // 3. HD Crystal Sound Engine
        if (crystalEnabled) {
            l = softClarityDeHarsh.processLeft(
                hfControlShelf.processLeft(
                    crystalTrebleShelf.processLeft(
                        detailEnhancerPeak.processLeft(
                            vocalClarityPeak.processLeft(l)
                        )
                    )
                )
            )
            r = softClarityDeHarsh.processRight(
                hfControlShelf.processRight(
                    crystalTrebleShelf.processRight(
                        detailEnhancerPeak.processRight(
                            vocalClarityPeak.processRight(r)
                        )
                    )
                )
            )

            if (abs(dynamicRangeContrast) > 0.01) {
                val instMag = max(abs(l), abs(r))
                macroRmsEnv += macroCoeff * (instMag - macroRmsEnv)
                val delta = (instMag - macroRmsEnv).coerceIn(-0.4, 0.4)
                val dynGain = (1.0 + delta * dynamicRangeContrast).coerceIn(0.75, 1.25)
                l *= dynGain
                r *= dynGain
            }
        }

        // 4. Loudness Control
        l = loudnessHighShelf.processLeft(loudnessLowShelf.processLeft(l))
        r = loudnessHighShelf.processRight(loudnessLowShelf.processRight(r))

        // 5. Mono-Compatible 3D Surround & Spatial Expansion
        // Math: M = (L + R)/2, S = (L - R)/2 -> processed outL = M + S', outR = M - S'.
        // Summing outL + outR = 2*M, strictly preserving mono compatibility and sub center!
        if (surroundEnabled) {
            val mid = (l + r) * 0.5
            val rawSide = (l - r) * 0.5
            // High-pass Side channel so low bass (<145Hz) stays anchored in center
            val highSide = sideLowCutHpf.processLeft(rawSide)
            val lowSide = rawSide - highSide

            val shapedHighSide = pinnaNotchSide.processLeft(highSide) * stereoSideGain

            val delayedIdx = (spatialDelayIdx - spatialDelaySamples + spatialDepthDelayMax) % spatialDepthDelayMax
            val delayedSide = spatialSideDelay[delayedIdx].toDouble()
            spatialSideDelay[spatialDelayIdx] = shapedHighSide.toFloat()
            spatialDelayIdx++
            if (spatialDelayIdx >= spatialDepthDelayMax) spatialDelayIdx = 0

            val finalSide = lowSide + shapedHighSide + delayedSide * surroundCrossAmt
            l = mid + finalSide
            r = mid - finalSide
        }

        // 6. Pure Concert Hall Stereo Reverb
        if (concertHallEnabled) {
            val hallOut = concertHall.processStereo(l, r)
            l = hallOut.first
            r = hallOut.second
        }

        // 7. Output Gain & Balance L/R
        l *= outputGainLinear * leftBalanceGain
        r *= outputGainLinear * rightBalanceGain

        // 8. Look-safe Soft-Knee Peak Limiter + Hard Safety Ceiling (prevents digital clipping)
        if (limiterEnabled) {
            val peak = max(abs(l), abs(r))
            val targetGain = if (peak > limiterCeilingLinear && peak > 1e-6) {
                limiterCeilingLinear / peak
            } else {
                1.0
            }
            val coeff = if (targetGain < limiterGain) limAtkCoeff else limRelCoeff
            limiterGain += coeff * (targetGain - limiterGain)
            l *= limiterGain
            r *= limiterGain

            // Soft safety clamp at ceiling
            l = softClamp(l, limiterCeilingLinear)
            r = softClamp(r, limiterCeilingLinear)

            lastPeakReductionDb = if (limiterGain < 0.999) {
                (-20.0 * ln(limiterGain) / ln(10.0)).toFloat().coerceAtLeast(0f)
            } else {
                0f
            }
        } else {
            lastPeakReductionDb = 0f
            // Still guard against integer PCM wrap distortion beyond +/- 1.0
            l = l.coerceIn(-1.0, 1.0)
            r = r.coerceIn(-1.0, 1.0)
        }

        return Pair(l, r)
    }

    private fun softClamp(x: Double, ceiling: Double): Double {
        if (abs(x) <= ceiling) return x
        return if (x > 0.0) ceiling else -ceiling
    }

    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)
}
