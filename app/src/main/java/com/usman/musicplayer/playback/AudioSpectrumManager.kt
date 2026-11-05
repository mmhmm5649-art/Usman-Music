package com.usman.musicplayer.playback

import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

object AudioSpectrumManager {

    private var visualizer: Visualizer? = null
    private var activeSessionId: Int = -1
    private var visualizerFailed: Boolean = false

    // 64 frequency bins covering sub-bass, bass, low-mids, high-mids, presence, and brilliance
    const val SPECTRUM_BINS = 64
    private val smoothedFrequencies = FloatArray(SPECTRUM_BINS)
    
    // Reuse buffers to minimize GC churn in high-frequency FFT processing
    private var magnitudesBuffer = FloatArray(0)
    private var currentFrameBuffer = FloatArray(SPECTRUM_BINS)

    private val _spectrumFlow = MutableStateFlow(FloatArray(SPECTRUM_BINS))
    val spectrumFlow: StateFlow<FloatArray> = _spectrumFlow.asStateFlow()

    // Flag to stop processing when visualizer is not on screen (conserves CPU/Battery)
    @Volatile
    var isProcessingEnabled: Boolean = true

    // Throttling: only update flow if enough time has passed (targeting ~40fps update)
    private var lastUpdateTime = 0L
    private const val UPDATE_INTERVAL_MS = 25L

    // Dynamic peak tracker for Auto-Gain Control (AGC) across varying master volumes
    private var dynamicPeak = 55.0f

    fun attachAudioSession(context: Context, audioSessionId: Int) {
        // If Visualizer previously failed due to hardware/emulator limitation,
        // do not repeatedly call native AudioFlinger / set() to avoid log spam
        if (visualizerFailed) return

        // Audio session must be a valid positive ID
        if (audioSessionId <= 0 || audioSessionId == activeSessionId) return

        // Check if RECORD_AUDIO is granted before creating native visualizer
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return
        }

        release()
        activeSessionId = audioSessionId

        try {
            val vis = Visualizer(audioSessionId).apply {
                val range = Visualizer.getCaptureSizeRange()
                if (range != null && range.size >= 2) {
                    captureSize = range[1] // Maximum resolution (typically 1024 bytes -> 512 frequency bins)
                }
                
                // Initialize magnitudes buffer once based on capture size
                magnitudesBuffer = FloatArray(captureSize / 2)

                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {}

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft != null && isProcessingEnabled) {
                                processFftData(fft)
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate(), // Maximum capture rate for fluid response
                    false,
                    true
                )
                enabled = true
            }
            visualizer = vis
        } catch (e: Exception) {
            e.printStackTrace()
            visualizerFailed = true
            activeSessionId = -1
            visualizer = null
        } catch (e: Error) {
            e.printStackTrace()
            visualizerFailed = true
            activeSessionId = -1
            visualizer = null
        }
    }

    /**
     * Process Android FFT data using:
     * 1. Euclidean complex magnitude: sqrt(R^2 + I^2)
     * 2. Logarithmic frequency binning: distributing audible spectrum (30Hz - 16kHz) across 64 visual bars
     * 3. Decibel conversion (dB = 20 * log10(magnitude)) with noise floor threshold
     * 4. Per-band pink-noise compensation (treble boost) so vocal/cymbal/instrument energy matches bass
     * 5. Dynamic range compression and independent attack/release smoothing
     */
    private fun processFftData(fft: ByteArray) {
        val n = fft.size
        val totalBins = (n / 2).coerceAtLeast(1)
        if (totalBins < 4) return

        // Step 1: Compute real FFT magnitudes for all frequency bins
        // Use pre-allocated buffer
        if (magnitudesBuffer.size != totalBins) {
            magnitudesBuffer = FloatArray(totalBins)
        }
        
        magnitudesBuffer[0] = kotlin.math.abs(fft[0].toFloat()) // DC component
        if (totalBins > 1) {
            magnitudesBuffer[totalBins - 1] = kotlin.math.abs(fft[1].toFloat()) // Nyquist frequency
        }
        for (k in 1 until totalBins - 1) {
            val r = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            // Fast hypot approximation: max(|r|, |im|) + min(|r|, |im|) * 0.4
            val absR = kotlin.math.abs(r)
            val absIm = kotlin.math.abs(im)
            magnitudesBuffer[k] = if (absR > absIm) absR + absIm * 0.4f else absIm + absR * 0.4f
        }

        // Step 2: Map into 64 logarithmic frequency bins and convert to dB
        var framePeak = 10f

        // Audible spectrum spans from bin 1 (approx 43Hz at 44.1kHz with 1024 size) to bin 380 (approx 16.3kHz)
        val maxSampleBin = (totalBins * 0.75f).toInt().coerceAtMost(totalBins - 1)

        for (i in 0 until SPECTRUM_BINS) {
            val normStart = i.toFloat() / SPECTRUM_BINS.toFloat()
            val normEnd = (i + 1).toFloat() / SPECTRUM_BINS.toFloat()

            // Logarithmic mapping: power curve of 1.45 perfectly balances bass, mids, and treble
            val startBin = (1 + Math.pow(normStart.toDouble(), 1.45) * (maxSampleBin - 1)).toInt().coerceIn(1, maxSampleBin)
            val endBin = (1 + Math.pow(normEnd.toDouble(), 1.45) * maxSampleBin).toInt().coerceIn(startBin + 1, maxSampleBin + 1)

            var peakInBand = 0f
            for (bin in startBin until endBin) {
                if (bin < totalBins && magnitudesBuffer[bin] > peakInBand) {
                    peakInBand = magnitudesBuffer[bin]
                }
            }

            // High frequency pink-noise equalization curve:
            // Natural acoustic audio falls off at -3dB to -6dB per octave.
            // Compensate so midrange vocals and treble cymbals are visually lively.
            val bandCenter = i.toFloat() / SPECTRUM_BINS.toFloat()
            val eqCurve = 1.0f + 3.2f * (bandCenter * bandCenter) // quadratic boost for upper frequencies
            val equalizedMag = peakInBand * eqCurve

            // Decibel conversion with floor
            val db = if (equalizedMag > 0.5f) {
                (20.0 * kotlin.math.log10(equalizedMag.toDouble())).toFloat()
            } else {
                0f
            }

            if (db > framePeak) {
                framePeak = db
            }
            currentFrameBuffer[i] = db
        }

        // Step 3: Adaptive Auto-Gain Control (smoothly tracks track volume dynamics)
        dynamicPeak = (dynamicPeak * 0.96f + kotlin.math.max(framePeak, 35f) * 0.04f).coerceIn(30f, 90f)

        // Step 4: Normalize to [0..1] and apply independent Attack & Release smoothing
        for (i in 0 until SPECTRUM_BINS) {
            val db = currentFrameBuffer[i]
            // Noise floor threshold at 8 dB
            val minDb = 8f
            val normalized = ((db - minDb) / (dynamicPeak - minDb)).coerceIn(0f, 1f)

            // Attack (fast rise) vs Release (smooth falloff)
            smoothedFrequencies[i] = if (normalized > smoothedFrequencies[i]) {
                smoothedFrequencies[i] * 0.20f + normalized * 0.80f // Fast attack
            } else {
                smoothedFrequencies[i] * 0.82f // Smooth decay
            }
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            _spectrumFlow.value = smoothedFrequencies.copyOf()
            lastUpdateTime = currentTime
        }
    }

    fun clearSpectrum() {
        smoothedFrequencies.fill(0f)
        _spectrumFlow.value = FloatArray(SPECTRUM_BINS)
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Throwable) {
            // Ignore
        }
        visualizer = null
        activeSessionId = -1
        clearSpectrum()
    }
}
