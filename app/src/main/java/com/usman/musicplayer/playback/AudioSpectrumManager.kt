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

    // 48 frequency bins representing sub-bass, bass, midrange, highs
    private const val SPECTRUM_BINS = 48
    private val smoothedFrequencies = FloatArray(SPECTRUM_BINS)

    private val _spectrumFlow = MutableStateFlow(FloatArray(SPECTRUM_BINS))
    val spectrumFlow: StateFlow<FloatArray> = _spectrumFlow.asStateFlow()

    fun attachAudioSession(context: Context, audioSessionId: Int) {
        // If Visualizer previously failed due to hardware/emulator or permission limitation,
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
            // Cannot instantiate hardware audiofx Visualizer without RECORD_AUDIO
            // Return without setting visualizerFailed = true so we can retry after permission granted
            return
        }

        release()
        activeSessionId = audioSessionId

        try {
            // Audio session 0 is for output mix, but it's restricted and might crash.
            // Ensure we use the actual session ID from ExoPlayer.
            val vis = Visualizer(audioSessionId).apply {
                val range = Visualizer.getCaptureSizeRange()
                if (range != null && range.size >= 2) {
                    captureSize = range[1] // Maximum resolution
                }
                
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
                            if (fft != null) {
                                processFftData(fft)
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2, // ~30 updates per second
                    false,
                    true
                )
                enabled = true
            }
            visualizer = vis
        } catch (e: Exception) {
            // Error code -3 (ERROR_NO_INIT), -1 or AudioFlinger status -1 when emulator or device
            // does not support audio effects on this session.
            e.printStackTrace()
            visualizerFailed = true
            activeSessionId = -1
            visualizer = null
        } catch (e: Error) {
            // Native error in Visualizer
            e.printStackTrace()
            visualizerFailed = true
            activeSessionId = -1
            visualizer = null
        }
    }

    private fun processFftData(fft: ByteArray) {
        val n = fft.size / 2
        if (n < SPECTRUM_BINS) return

        for (i in 0 until SPECTRUM_BINS) {
            val minIndex = (i * n / SPECTRUM_BINS).coerceIn(0, n - 1)
            val maxIndex = ((i + 1) * n / SPECTRUM_BINS).coerceIn(minIndex + 1, n)

            var maxMag = 0f
            for (j in minIndex until maxIndex) {
                val real = fft[2 * j].toFloat()
                val imag = fft[2 * j + 1].toFloat()
                val mag = hypot(real.toDouble(), imag.toDouble()).toFloat()
                if (mag > maxMag) maxMag = mag
            }

            val normalized = (maxMag / 128f).coerceIn(0f, 1f)

            smoothedFrequencies[i] = if (normalized > smoothedFrequencies[i]) {
                normalized
            } else {
                smoothedFrequencies[i] * 0.82f
            }
        }

        _spectrumFlow.value = smoothedFrequencies.copyOf()
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
