package com.usman.musicplayer.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Data model for a single frequency band in the graphic Equalizer
 */
data class EqualizerBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val formattedFreq: String,
    val minGainMb: Short,
    val maxGainMb: Short,
    val currentGainMb: Short
) {
    val currentGainDb: Float
        get() = currentGainMb / 100f

    val minGainDb: Float
        get() = minGainMb / 100f

    val maxGainDb: Float
        get() = maxGainMb / 100f
}

/**
 * Full state for audio effects and graphic equalizer
 */
data class AudioEffectsState(
    val masterEnabled: Boolean = true,
    val equalizerEnabled: Boolean = true,
    val isEqualizerSupported: Boolean = true,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val currentPresetIndex: Short = -1,
    val currentPresetName: String = "Custom",
    val minGainMb: Short = -1500,
    val maxGainMb: Short = 1500,

    // Bass Boost
    val isBassBoostSupported: Boolean = true,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0, // 0 to 1000

    // Virtualizer / Spatial Audio (separate stereo expansion)
    val isVirtualizerSupported: Boolean = true,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0, // 0 to 1000

    // Concert Hall (Authentic Acoustic Reverb)
    val isReverbSupported: Boolean = true,
    val concertHallEnabled: Boolean = false,
    val concertHallIntensity: Int = 60, // 0 to 100%
    val concertHallTier: String = "Medium", // "Low", "Medium", "High"
    val isEnvironmentalReverbActive: Boolean = false,
    val currentReverbPreset: Short = PresetReverb.PRESET_NONE
)

/**
 * Manages real-time Android AudioFX:
 * - Equalizer (hardware graphic equalizer attached to audio session)
 * - BassBoost (low-end frequency harmonic exciter)
 * - Virtualizer (stereo-widening spatial audio)
 * - EnvironmentalReverb / PresetReverb (authentic Concert Hall acoustic space simulation)
 *
 * Persists preferences across app launches using SharedPreferences.
 */
object AudioEffectsManager {

    private const val TAG = "AudioEffectsManager"
    private const val PREFS_NAME = "usman_audio_effects_prefs"

    private var currentAudioSessionId: Int = -1
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var presetReverb: PresetReverb? = null
    private var prefs: SharedPreferences? = null

    private val _effectsState = MutableStateFlow(AudioEffectsState())
    val effectsState: StateFlow<AudioEffectsState> = _effectsState.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadSavedPreferences()
        }
    }

    private fun loadSavedPreferences() {
        val p = prefs ?: return
        val master = p.getBoolean("master_enabled", true)
        val eqEnabled = p.getBoolean("eq_enabled", true)
        val presetIdx = p.getInt("preset_idx", -1).toShort()
        val presetName = p.getString("preset_name", "Custom") ?: "Custom"

        val bbEnabled = p.getBoolean("bb_enabled", false)
        val bbStrength = p.getInt("bb_strength", 600).toShort()

        val virtEnabled = p.getBoolean("virt_enabled", false)
        val virtStrength = p.getInt("virt_strength", 500).toShort()

        val chEnabled = p.getBoolean("ch_enabled", false)
        val chIntensity = p.getInt("ch_intensity", 60)
        val chTier = when {
            chIntensity <= 40 -> "Low"
            chIntensity <= 75 -> "Medium"
            else -> "High"
        }
        val chPreset = if (chEnabled) mapIntensityToPreset(chIntensity) else PresetReverb.PRESET_NONE

        _effectsState.update {
            it.copy(
                masterEnabled = master,
                equalizerEnabled = eqEnabled,
                currentPresetIndex = presetIdx,
                currentPresetName = presetName,
                bassBoostEnabled = bbEnabled,
                bassBoostStrength = bbStrength,
                virtualizerEnabled = virtEnabled,
                virtualizerStrength = virtStrength,
                concertHallEnabled = chEnabled,
                concertHallIntensity = chIntensity,
                concertHallTier = chTier,
                currentReverbPreset = chPreset
            )
        }
    }

    // Session-specific cache for failed effect types to prevent repeated crashes/logs
    private val failedEffects = mutableSetOf<String>()

    /**
     * Attach all audio effects to the active audio session ID from ExoPlayer
     */
    fun attachAudioSession(context: Context, audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) {
            Log.w(TAG, "Invalid audioSessionId: $audioSessionId. Delaying effect attach.")
            return
        }

        init(context)

        if (currentAudioSessionId == audioSessionId && (equalizer != null || environmentalReverb != null || presetReverb != null)) {
            Log.d(TAG, "Audio effects already attached to session $audioSessionId")
            return
        }

        releaseEffects()
        currentAudioSessionId = audioSessionId
        failedEffects.clear() // Reset failed cache for new session

        // 1. Equalizer Setup
        if (!failedEffects.contains("Equalizer")) {
            try {
                val eq = Equalizer(0, audioSessionId).apply {
                    enabled = _effectsState.value.masterEnabled && _effectsState.value.equalizerEnabled
                }
                equalizer = eq
                _effectsState.update { it.copy(isEqualizerSupported = true) }
                setupEqualizerBands(eq)
            } catch (e: Throwable) {
                Log.d(TAG, "Equalizer not supported on session $audioSessionId", e)
                equalizer = null
                _effectsState.update { it.copy(isEqualizerSupported = false) }
                failedEffects.add("Equalizer")
            }
        }

        // 2. Bass Boost Setup
        if (!failedEffects.contains("BassBoost")) {
            try {
                val bb = BassBoost(0, audioSessionId).apply {
                    val state = _effectsState.value
                    enabled = state.masterEnabled && state.bassBoostEnabled
                    if (strengthSupported) {
                        setStrength(state.bassBoostStrength)
                    }
                }
                bassBoost = bb
                _effectsState.update { it.copy(isBassBoostSupported = true) }
            } catch (e: Throwable) {
                Log.d(TAG, "BassBoost not supported on session $audioSessionId", e)
                bassBoost = null
                _effectsState.update { it.copy(isBassBoostSupported = false) }
                failedEffects.add("BassBoost")
            }
        }

        // 3. Virtualizer Setup (Spatial Width)
        if (!failedEffects.contains("Virtualizer")) {
            try {
                val virt = Virtualizer(0, audioSessionId).apply {
                    val state = _effectsState.value
                    enabled = state.masterEnabled && state.virtualizerEnabled
                    if (strengthSupported) {
                        setStrength(state.virtualizerStrength)
                    }
                }
                virtualizer = virt
                _effectsState.update { it.copy(isVirtualizerSupported = true) }
            } catch (e: Throwable) {
                Log.d(TAG, "Virtualizer not supported on session $audioSessionId", e)
                virtualizer = null
                _effectsState.update { it.copy(isVirtualizerSupported = false) }
                failedEffects.add("Virtualizer")
            }
        }

        // 4. Concert Hall Reverb (Parametric EnvironmentalReverb with PresetReverb fallback)
        setupConcertHallReverb(audioSessionId)
    }

    private fun setupConcertHallReverb(audioSessionId: Int) {
        val state = _effectsState.value
        val shouldEnable = state.masterEnabled && state.concertHallEnabled

        // Attempt genuine parametric EnvironmentalReverb first for authentic hall acoustics
        try {
            val env = EnvironmentalReverb(0, audioSessionId)
            applyEnvironmentalSettings(env, state.concertHallIntensity)
            env.enabled = shouldEnable
            environmentalReverb = env
            presetReverb = null
            _effectsState.update {
                it.copy(
                    isReverbSupported = true,
                    isEnvironmentalReverbActive = true
                )
            }
            Log.d(TAG, "EnvironmentalReverb initialized on session $audioSessionId (active=$shouldEnable)")
            return
        } catch (e: Throwable) {
            Log.w(TAG, "EnvironmentalReverb unsupported on session $audioSessionId, trying PresetReverb fallback", e)
            environmentalReverb = null
        }

        // Fallback: PresetReverb
        try {
            val pReverb = PresetReverb(0, audioSessionId).apply {
                preset = if (state.concertHallEnabled) mapIntensityToPreset(state.concertHallIntensity) else PresetReverb.PRESET_NONE
                enabled = shouldEnable
            }
            presetReverb = pReverb
            _effectsState.update {
                it.copy(
                    isReverbSupported = true,
                    isEnvironmentalReverbActive = false,
                    currentReverbPreset = pReverb.preset
                )
            }
            Log.d(TAG, "PresetReverb fallback initialized on session $audioSessionId (preset=${pReverb.preset})")
        } catch (e2: Throwable) {
            Log.e(TAG, "Neither EnvironmentalReverb nor PresetReverb supported on session $audioSessionId", e2)
            presetReverb = null
            _effectsState.update {
                it.copy(
                    isReverbSupported = false,
                    isEnvironmentalReverbActive = false
                )
            }
        }
    }

    /**
     * Calibrate EnvironmentalReverb acoustic parameters to deliver authentic concert hall reverberation:
     * - Smooth early reflections (20-30ms) for real physical stage dimensions without discrete echoes.
     * - Lush, diffuse decay tail (1.4s to 3.4s) that blooms warmly behind the music.
     * - High-frequency absorption (decayHFRatio and roomHFLevel) that prevents metallic ringing.
     * - Balanced room level so the dry vocal and acoustic instruments remain upfront and crystal-clear.
     */
    private fun applyEnvironmentalSettings(reverb: EnvironmentalReverb, intensity: Int) {
        try {
            val clamped = intensity.coerceIn(5, 100)
            val t = clamped / 100f // 0.05 to 1.0

            // 1. Decay Time (1.3s at Low to 3.4s at High)
            // A classical symphonic hall has an RT60 of ~2.0s - 2.6s.
            val decayMs = (1300 + (t * 2100)).toInt()
            reverb.decayTime = decayMs

            // 2. High-Frequency Decay Ratio (HF Damping)
            // Values < 1000 cause higher frequencies to roll off faster than low frequencies,
            // emulating natural air and wood-paneled hall absorption without harsh ringing.
            val hfRatio = (750 + (t * 110)).toInt().toShort() // 750 to 860 per mille
            reverb.decayHFRatio = hfRatio

            // 3. Room Level (Master Reverb Output)
            // Kept between -1600 mB (-16 dB) and -300 mB (-3 dB) so the direct dry audio
            // and vocal presence remain intelligible and punchy.
            val roomLvl = (-1600 + (t * 1300)).toInt().toShort()
            reverb.roomLevel = roomLvl

            // 4. Room High-Frequency Attenuation
            // Attenuation between -800 mB and -450 mB prevents harsh sibilance or cymbal wash.
            val roomHFLvl = (-800 + (t * 350)).toInt().toShort()
            reverb.roomHFLevel = roomHFLvl

            // 5. Early Reflections Level & Delay
            // Early reflections arrive 18ms to 32ms after direct sound, establishing stage size.
            val reflLvl = (-1500 + (t * 1000)).toInt().toShort()
            val reflDelay = (18 + (t * 14)).toInt()
            reverb.reflectionsLevel = reflLvl
            reverb.reflectionsDelay = reflDelay

            // 6. Late Reverb Tail Level & Delay
            // Reverb tail begins 24ms to 40ms after early reflections for a continuous diffuse bloom.
            val revLvl = (-1200 + (t * 1000)).toInt().toShort()
            val revDelay = (24 + (t * 16)).toInt()
            reverb.reverbLevel = revLvl
            reverb.reverbDelay = revDelay

            // 7. Diffusion & Density
            // Maximum diffusion (1000/1000) for silky smooth reverberation without flutter.
            reverb.diffusion = 1000.toShort()
            reverb.density = 1000.toShort()
        } catch (e: Exception) {
            Log.w(TAG, "applyEnvironmentalSettings partial failure, continuing", e)
        }
    }

    private fun mapIntensityToPreset(intensity: Int): Short {
        return when {
            intensity <= 40 -> PresetReverb.PRESET_LARGEROOM // subtle room ambience
            intensity <= 75 -> PresetReverb.PRESET_MEDIUMHALL // natural concert hall
            else -> PresetReverb.PRESET_LARGEHALL // spacious hall
        }
    }

    private fun setupEqualizerBands(eq: Equalizer) {
        val numBands = eq.numberOfBands.toInt()
        val range: ShortArray = eq.bandLevelRange // [minMb, maxMb] typically [-1500, 1500]
        val minMb: Short = if (range.size >= 2) range[0] else (-1500).toShort()
        val maxMb: Short = if (range.size >= 2) range[1] else (1500).toShort()

        val p = prefs
        val bandsList = mutableListOf<EqualizerBand>()

        for (i in 0 until numBands) {
            val bandIdx: Short = i.toShort()
            val centerFreqMhz = eq.getCenterFreq(bandIdx)
            val centerFreqHz = centerFreqMhz / 1000
            val formatted = formatFrequency(centerFreqHz)

            val savedGain: Short = (p?.getInt("band_gain_$bandIdx", 0) ?: 0).toShort()
            val clampedGain: Short = savedGain.coerceIn(minMb, maxMb).toShort()

            try {
                eq.setBandLevel(bandIdx, clampedGain)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply band level $bandIdx", e)
            }

            bandsList.add(
                EqualizerBand(
                    bandIndex = bandIdx,
                    centerFreqHz = centerFreqHz,
                    formattedFreq = formatted,
                    minGainMb = minMb,
                    maxGainMb = maxMb,
                    currentGainMb = clampedGain
                )
            )
        }

        val hwPresets = mutableListOf<String>()
        val numPresets = eq.numberOfPresets.toInt()
        for (i in 0 until numPresets) {
            val name = try { eq.getPresetName(i.toShort()) } catch (e: Exception) { "Preset $i" }
            if (name.isNotBlank()) hwPresets.add(name)
        }

        val allPresets = if (hwPresets.isNotEmpty()) {
            hwPresets
        } else {
            listOf("Normal", "Flat", "Bass", "Treble", "Vocal", "Rock", "Pop", "Jazz", "Classical", "Dance", "Hip-Hop")
        }

        _effectsState.update {
            it.copy(
                bands = bandsList,
                presets = allPresets,
                minGainMb = minMb,
                maxGainMb = maxMb
            )
        }
    }

    private fun formatFrequency(hz: Int): String {
        return if (hz >= 1000) {
            val khz = hz / 1000f
            if (khz % 1.0f == 0.0f) "${khz.toInt()}k" else String.format("%.1fk", khz)
        } else {
            "${hz}Hz"
        }
    }

    // --- Interactive Controls ---

    fun setMasterEnabled(enabled: Boolean) {
        _effectsState.update { it.copy(masterEnabled = enabled) }
        prefs?.edit()?.putBoolean("master_enabled", enabled)?.apply()

        try {
            equalizer?.enabled = enabled && _effectsState.value.equalizerEnabled
            bassBoost?.enabled = enabled && _effectsState.value.bassBoostEnabled
            virtualizer?.enabled = enabled && _effectsState.value.virtualizerEnabled
            environmentalReverb?.enabled = enabled && _effectsState.value.concertHallEnabled
            presetReverb?.enabled = enabled && _effectsState.value.concertHallEnabled
        } catch (e: Exception) {
            Log.w(TAG, "setMasterEnabled failed", e)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _effectsState.update { it.copy(equalizerEnabled = enabled) }
        prefs?.edit()?.putBoolean("eq_enabled", enabled)?.apply()

        try {
            equalizer?.enabled = _effectsState.value.masterEnabled && enabled
        } catch (e: Exception) {
            Log.w(TAG, "setEqualizerEnabled failed", e)
        }
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, gainMb)
        } catch (e: Exception) {
            Log.w(TAG, "setBandLevel failed for band $bandIndex", e)
        }

        _effectsState.update { current ->
            val updatedBands = current.bands.map {
                if (it.bandIndex == bandIndex) it.copy(currentGainMb = gainMb) else it
            }
            current.copy(
                bands = updatedBands,
                currentPresetIndex = -1,
                currentPresetName = "Custom"
            )
        }

        prefs?.edit()?.apply {
            putInt("band_gain_$bandIndex", gainMb.toInt())
            putInt("preset_idx", -1)
            putString("preset_name", "Custom")
            apply()
        }
    }

    fun selectPreset(presetIndex: Short, presetName: String) {
        val eq = equalizer
        if (eq != null && presetIndex >= 0) {
            try {
                eq.usePreset(presetIndex)
                val updatedBands = _effectsState.value.bands.map { band ->
                    val level = eq.getBandLevel(band.bandIndex)
                    band.copy(currentGainMb = level)
                }
                _effectsState.update {
                    it.copy(
                        bands = updatedBands,
                        currentPresetIndex = presetIndex,
                        currentPresetName = presetName
                    )
                }
                prefs?.edit()?.apply {
                    putInt("preset_idx", presetIndex.toInt())
                    putString("preset_name", presetName)
                    updatedBands.forEach { b ->
                        putInt("band_gain_${b.bandIndex}", b.currentGainMb.toInt())
                    }
                    apply()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "eq.usePreset failed for $presetIndex", e)
            }
        }

        // Custom presets curve fallback
        applyCustomPresetCurve(presetName)
    }

    private fun applyCustomPresetCurve(name: String) {
        val eq = equalizer
        val minGain = _effectsState.value.minGainMb.toFloat()
        val maxGain = _effectsState.value.maxGainMb.toFloat()
        val range = (maxGain - minGain) / 2f

        val ratios = when (name.lowercase()) {
            "flat", "normal" -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
            "bass" -> floatArrayOf(0.85f, 0.65f, 0.1f, -0.1f, -0.2f)
            "treble" -> floatArrayOf(-0.2f, -0.1f, 0.15f, 0.65f, 0.85f)
            "vocal" -> floatArrayOf(-0.2f, 0.1f, 0.6f, 0.5f, 0.1f)
            "rock" -> floatArrayOf(0.6f, 0.3f, -0.1f, 0.4f, 0.7f)
            "pop" -> floatArrayOf(-0.1f, 0.35f, 0.6f, 0.4f, -0.1f)
            "jazz" -> floatArrayOf(0.4f, 0.2f, 0.1f, 0.3f, 0.5f)
            "classical" -> floatArrayOf(0.5f, 0.35f, -0.1f, 0.3f, 0.55f)
            "dance" -> floatArrayOf(0.75f, 0.5f, 0.1f, 0.3f, 0.4f)
            "hip-hop", "hip hop" -> floatArrayOf(0.8f, 0.55f, 0.0f, 0.25f, 0.45f)
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        }

        val updatedBands = _effectsState.value.bands.mapIndexed { idx, band ->
            val ratio = if (idx < ratios.size) ratios[idx] else 0f
            val gain = (ratio * (range * 0.75f)).toInt().coerceIn(minGain.toInt(), maxGain.toInt()).toShort()
            try {
                eq?.setBandLevel(band.bandIndex, gain)
            } catch (e: Exception) {
                // Ignore
            }
            band.copy(currentGainMb = gain)
        }

        _effectsState.update {
            it.copy(
                bands = updatedBands,
                currentPresetIndex = -1,
                currentPresetName = name
            )
        }

        prefs?.edit()?.apply {
            putInt("preset_idx", -1)
            putString("preset_name", name)
            updatedBands.forEach { b ->
                putInt("band_gain_${b.bandIndex}", b.currentGainMb.toInt())
            }
            apply()
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        _effectsState.update { it.copy(bassBoostEnabled = enabled) }
        prefs?.edit()?.putBoolean("bb_enabled", enabled)?.apply()

        try {
            bassBoost?.enabled = _effectsState.value.masterEnabled && enabled
        } catch (e: Exception) {
            Log.w(TAG, "setBassBoostEnabled failed", e)
        }
    }

    fun setBassBoostStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        _effectsState.update { it.copy(bassBoostStrength = clamped) }
        prefs?.edit()?.putInt("bb_strength", clamped.toInt())?.apply()

        try {
            bassBoost?.setStrength(clamped)
        } catch (e: Exception) {
            Log.w(TAG, "setBassBoostStrength failed", e)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        _effectsState.update { it.copy(virtualizerEnabled = enabled) }
        prefs?.edit()?.putBoolean("virt_enabled", enabled)?.apply()

        try {
            virtualizer?.enabled = _effectsState.value.masterEnabled && enabled
        } catch (e: Exception) {
            Log.w(TAG, "setVirtualizerEnabled failed", e)
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        _effectsState.update { it.copy(virtualizerStrength = clamped) }
        prefs?.edit()?.putInt("virt_strength", clamped.toInt())?.apply()

        try {
            virtualizer?.setStrength(clamped)
        } catch (e: Exception) {
            Log.w(TAG, "setVirtualizerStrength failed", e)
        }
    }

    // --- Concert Hall Reverb Controls ---

    fun setConcertHallEnabled(enabled: Boolean) {
        val preset = if (enabled) mapIntensityToPreset(_effectsState.value.concertHallIntensity) else PresetReverb.PRESET_NONE
        _effectsState.update {
            it.copy(
                concertHallEnabled = enabled,
                currentReverbPreset = preset
            )
        }
        prefs?.edit()?.putBoolean("ch_enabled", enabled)?.apply()

        try {
            val master = _effectsState.value.masterEnabled
            val shouldEnable = master && enabled

            environmentalReverb?.let {
                it.enabled = shouldEnable
                if (enabled) {
                    applyEnvironmentalSettings(it, _effectsState.value.concertHallIntensity)
                }
            }

            presetReverb?.let {
                it.enabled = shouldEnable
                it.preset = preset
            }
        } catch (e: Exception) {
            Log.w(TAG, "setConcertHallEnabled failed", e)
        }
    }

    fun setConcertHallIntensity(intensity: Int) {
        val clamped = intensity.coerceIn(0, 100)
        val tier = when {
            clamped <= 40 -> "Low"
            clamped <= 75 -> "Medium"
            else -> "High"
        }
        val preset = if (_effectsState.value.concertHallEnabled) mapIntensityToPreset(clamped) else PresetReverb.PRESET_NONE

        _effectsState.update {
            it.copy(
                concertHallIntensity = clamped,
                concertHallTier = tier,
                currentReverbPreset = preset
            )
        }
        prefs?.edit()?.putInt("ch_intensity", clamped)?.apply()

        try {
            if (_effectsState.value.masterEnabled && _effectsState.value.concertHallEnabled) {
                environmentalReverb?.let {
                    applyEnvironmentalSettings(it, clamped)
                }
                presetReverb?.let {
                    it.preset = preset
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "setConcertHallIntensity failed", e)
        }
    }

    fun setConcertHallTier(tier: String) {
        val targetIntensity = when (tier.lowercase()) {
            "low" -> 30
            "high" -> 90
            else -> 60 // "medium"
        }
        setConcertHallIntensity(targetIntensity)
    }

    fun resetToDefaults() {
        val eq = equalizer
        val numBands = eq?.numberOfBands?.toInt() ?: _effectsState.value.bands.size

        for (i in 0 until numBands) {
            val bIdx = i.toShort()
            try {
                eq?.setBandLevel(bIdx, 0)
            } catch (e: Exception) {
                // Ignore
            }
        }

        val updatedBands = _effectsState.value.bands.map { it.copy(currentGainMb = 0) }

        _effectsState.update {
            it.copy(
                bands = updatedBands,
                currentPresetIndex = -1,
                currentPresetName = "Flat",
                bassBoostStrength = 600,
                virtualizerStrength = 500,
                concertHallIntensity = 60,
                concertHallTier = "Medium"
            )
        }

        try {
            bassBoost?.setStrength(600)
            virtualizer?.setStrength(500)
            environmentalReverb?.let { applyEnvironmentalSettings(it, 60) }
            presetReverb?.preset = mapIntensityToPreset(60)
        } catch (e: Exception) {
            // Ignore
        }

        prefs?.edit()?.apply {
            putInt("preset_idx", -1)
            putString("preset_name", "Flat")
            putInt("bb_strength", 600)
            putInt("virt_strength", 500)
            putInt("ch_intensity", 60)
            updatedBands.forEach { b ->
                putInt("band_gain_${b.bandIndex}", 0)
            }
            apply()
        }
    }

    fun releaseEffects() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (e: Throwable) { /* Ignore */ }
        equalizer = null

        try {
            bassBoost?.enabled = false
            bassBoost?.release()
        } catch (e: Throwable) { /* Ignore */ }
        bassBoost = null

        try {
            virtualizer?.enabled = false
            virtualizer?.release()
        } catch (e: Throwable) { /* Ignore */ }
        virtualizer = null

        try {
            environmentalReverb?.enabled = false
            environmentalReverb?.release()
        } catch (e: Throwable) { /* Ignore */ }
        environmentalReverb = null

        try {
            presetReverb?.enabled = false
            presetReverb?.release()
        } catch (e: Throwable) { /* Ignore */ }
        presetReverb = null

        currentAudioSessionId = -1
    }
}
