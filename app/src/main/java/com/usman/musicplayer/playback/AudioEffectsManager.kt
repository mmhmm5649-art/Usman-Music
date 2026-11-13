package com.usman.musicplayer.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Unified Studio Feel Audio Effects Engine Manager.
 *
 * Architecture:
 * - Real-time Media3 [StudioFeelAudioProcessor] attached inside ExoPlayer's DefaultAudioSink,
 *   guaranteeing genuine PCM processing for 10-band EQ, Deep/Punchy Bass, Schroeder Concert Hall,
 *   mono-safe 3D Surround, Crystal Clarity, Loudness, Balance, Preamp/Output Gain, and Peak Limiter.
 * - Zero conflicting native Equalizer/BassBoost/Virtualizer/Reverb handles and zero duplicate sessions.
 * - Zero artificial delays during audio session attachment; thread-safe, lock-free audio thread updates.
 * - Honest capability detection via AudioEffect.queryEffects() alongside live DSP telemetry.
 */
@OptIn(UnstableApi::class)
object AudioEffectsManager {

    private const val TAG = "AudioEffectsManager"
    private const val PREFS_NAME = "usman_studio_audio_effects_prefs"
    private const val KEY_STATE_JSON = "studio_state_v1"
    private const val KEY_CUSTOM_EQ_PRESETS = "custom_eq_presets_v1"
    private const val KEY_CUSTOM_QUICK_PRESETS = "custom_quick_presets_v1"

    private val sessionLock = Any()
    private val effectsScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    @Volatile
    private var probedHardwareEffectTypes: Set<UUID>? = null

    private val _effectsState = MutableStateFlow(AudioEffectsState())
    val effectsState: StateFlow<AudioEffectsState> = _effectsState.asStateFlow()

    private val audioProcessor = StudioFeelAudioProcessor { sampleRateHz, channelCount, encodingName, active ->
        _effectsState.update { state ->
            state.copy(
                availability = state.availability.copy(
                    sampleRateHz = if (sampleRateHz > 0) sampleRateHz else state.availability.sampleRateHz,
                    channelCount = if (channelCount > 0) channelCount else state.availability.channelCount,
                    encodingName = encodingName,
                    dspEngineActive = active,
                    outputModeDescription = buildOutputModeDescription(state, channelCount, active)
                )
            )
        }
    }

    fun getAudioProcessor(): StudioFeelAudioProcessor = audioProcessor

    fun init(context: Context) {
        synchronized(sessionLock) {
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadSavedStateLocked()
            }
        }
        audioProcessor.updateState(_effectsState.value)
    }

    /**
     * Attaches cleanly to an active Media3 ExoPlayer audio session ID without artificial delays.
     * Deduplicates repeated attachments for the same session ID.
     */
    fun attachAudioSession(context: Context, audioSessionId: Int) {
        init(context)
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return

        synchronized(sessionLock) {
            if (currentAudioSessionId == audioSessionId) {
                return
            }
            currentAudioSessionId = audioSessionId
        }

        // Push active state immediately to the live DSP processor
        audioProcessor.updateState(_effectsState.value)

        // Probe hardware AudioEffect capabilities off the main thread without blocking
        effectsScope.launch {
            val supported = querySupportedHardwareEffects()
            val envUuid = AudioEffect.EFFECT_TYPE_ENV_REVERB
            val presetRevUuid = AudioEffect.EFFECT_TYPE_PRESET_REVERB
            val loudUuid = UUID.fromString("0bed4300-ddd6-11db-8f34-0002a5d5c51b") // LoudnessEnhancer
            val dynUuid = UUID.fromString("7261676f-6d75-7369-6364-28e2fd3ac39e")  // DynamicsProcessing

            _effectsState.update { state ->
                val hwEnv = envUuid in supported
                val hwPresetRev = presetRevUuid in supported
                state.copy(
                    isEnvironmentalReverbActive = hwEnv,
                    availability = state.availability.copy(
                        audioSessionId = audioSessionId,
                        hardwareEqualizerSupported = AudioEffect.EFFECT_TYPE_EQUALIZER in supported,
                        hardwareBassBoostSupported = AudioEffect.EFFECT_TYPE_BASS_BOOST in supported,
                        hardwareVirtualizerSupported = AudioEffect.EFFECT_TYPE_VIRTUALIZER in supported,
                        hardwareEnvReverbSupported = hwEnv,
                        hardwarePresetReverbSupported = hwPresetRev,
                        hardwareLoudnessEnhancerSupported = loudUuid in supported,
                        hardwareDynamicsSupported = if (Build.VERSION.SDK_INT >= 28) dynUuid in supported else false,
                        peakReductionDb = audioProcessor.getLastPeakReductionDb(),
                        outputModeDescription = buildOutputModeDescription(
                            state,
                            state.availability.channelCount,
                            state.availability.dspEngineActive
                        )
                    )
                )
            }
        }
    }

    fun refreshTelemetry() {
        _effectsState.update { state ->
            state.copy(
                availability = state.availability.copy(
                    peakReductionDb = audioProcessor.getLastPeakReductionDb()
                )
            )
        }
    }

    fun releaseEffects() {
        synchronized(sessionLock) {
            currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        }
        audioProcessor.flush()
        _effectsState.update { state ->
            state.copy(
                availability = state.availability.copy(
                    audioSessionId = -1,
                    dspEngineActive = false,
                    peakReductionDb = 0f
                )
            )
        }
    }

    private fun querySupportedHardwareEffects(): Set<UUID> {
        probedHardwareEffectTypes?.let { return it }
        val discovered = try {
            AudioEffect.queryEffects()?.mapNotNull { it?.type }?.toSet() ?: emptySet()
        } catch (t: Throwable) {
            Log.d(TAG, "AudioEffect.queryEffects unavailable: ${t.message}")
            emptySet()
        }
        probedHardwareEffectTypes = discovered
        return discovered
    }

    private fun buildOutputModeDescription(
        state: AudioEffectsState,
        channels: Int,
        dspActive: Boolean
    ): String {
        val chLabel = when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            else -> "${channels}ch"
        }
        val engineLabel = if (dspActive) "Studio PCM DSP Active" else "Studio DSP Ready"
        val spatialNote = if (state.surround.enabled || state.utilities.virtualizerEnabled) {
            " • Stereo Virtualization (Mono-Safe)"
        } else ""
        return "$engineLabel • $chLabel$spatialNote"
    }

    // --- State Update & Persistence Helper ---

    private inline fun mutateState(
        markCustomQuick: Boolean = false,
        crossinline transform: (AudioEffectsState) -> AudioEffectsState
    ) {
        var snapshot: AudioEffectsState? = null
        _effectsState.update { prev ->
            val updated = transform(prev)
            val synced = syncMirrorFields(
                if (markCustomQuick && !updated.activeQuickPresetId.startsWith("custom_")) {
                    updated.copy(
                        activeQuickPresetId = "custom",
                        activeQuickPresetName = "Custom Studio"
                    )
                } else {
                    updated
                }
            )
            snapshot = synced
            synced
        }
        snapshot?.let {
            audioProcessor.updateState(it)
            persistState(it)
        }
    }

    private fun syncMirrorFields(state: AudioEffectsState): AudioEffectsState {
        val chInt = state.concertHall.wetDryMix.coerceIn(0, 100)
        val tier = when {
            chInt <= 22 -> "Low"
            chInt <= 32 -> "Medium"
            else -> "High"
        }
        val allEqNames = StudioEffectDefaults.builtInEqPresets.map { it.name } +
            state.customEqPresets.map { it.name }
        return state.copy(
            presets = allEqNames,
            bassBoostEnabled = state.utilities.bassBoostEnabled,
            bassBoostStrength = state.utilities.bassBoostStrength,
            virtualizerEnabled = state.surround.enabled || state.utilities.virtualizerEnabled,
            virtualizerStrength = if (state.surround.enabled) {
                (state.surround.intensity * 10).coerceIn(0, 1000).toShort()
            } else {
                state.utilities.virtualizerStrength
            },
            concertHallEnabled = state.concertHall.enabled,
            concertHallIntensity = ((state.concertHall.wetDryMix / 45f) * 100f).toInt().coerceIn(0, 100),
            concertHallTier = tier,
            availability = state.availability.copy(
                outputModeDescription = buildOutputModeDescription(
                    state,
                    state.availability.channelCount,
                    state.availability.dspEngineActive
                )
            )
        )
    }

    // --- Master & Equalizer Controls ---

    fun setMasterEnabled(enabled: Boolean) {
        mutateState { it.copy(masterEnabled = enabled) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) { it.copy(equalizerEnabled = enabled) }
    }

    fun setEqStrengthPercent(strengthPercent: Int) {
        val clamped = strengthPercent.coerceIn(0, 150)
        mutateState(markCustomQuick = true) { it.copy(eqStrengthPercent = clamped) }
    }

    fun setPreampDb(preampDb: Float) {
        val clamped = preampDb.coerceIn(-12f, 12f)
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(preampDb = clamped))
        }
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        val idx = bandIndex.toInt()
        mutateState(markCustomQuick = true) { state ->
            val clamped = gainMb.coerceIn(state.minGainMb, state.maxGainMb)
            val updatedBands = state.bands.map { band ->
                if (band.bandIndex.toInt() == idx) band.copy(currentGainMb = clamped) else band
            }
            val customIdx = state.presets.indexOfFirst { it.equals("Custom", ignoreCase = true) }
            state.copy(
                bands = updatedBands,
                currentPresetIndex = customIdx.toShort(),
                currentPresetName = "Custom"
            )
        }
    }

    fun selectPreset(presetIndex: Short, presetName: String) {
        val allPresets = StudioEffectDefaults.builtInEqPresets + _effectsState.value.customEqPresets
        val target = allPresets.firstOrNull { it.name.equals(presetName, ignoreCase = true) }
            ?: allPresets.getOrNull(presetIndex.toInt())

        if (target == null) {
            applyFallbackEqCurve(presetName)
            return
        }

        mutateState(markCustomQuick = true) { state ->
            val updatedBands = state.bands.mapIndexed { i, band ->
                val gainDb = target.gainsDb.getOrElse(i) { 0f }
                val gainMb = (gainDb * 100f).toInt().coerceIn(state.minGainMb.toInt(), state.maxGainMb.toInt()).toShort()
                band.copy(currentGainMb = gainMb)
            }
            val resolvedIdx = state.presets.indexOfFirst { it.equals(target.name, ignoreCase = true) }
            state.copy(
                equalizerEnabled = true,
                bands = updatedBands,
                currentPresetIndex = resolvedIdx.toShort(),
                currentPresetName = target.name,
                utilities = state.utilities.copy(preampDb = target.preampDb)
            )
        }
    }

    private fun applyFallbackEqCurve(name: String) {
        val gains = when (name.lowercase()) {
            "normal", "flat" -> List(10) { 0f }
            "bass boost" -> listOf(6f, 5f, 3f, 0.5f, 0f, 0f, 0.5f, 1f, 1f, 0.5f)
            "treble boost" -> listOf(-1f, -0.5f, 0f, 0.5f, 1f, 2f, 3.5f, 5f, 6f, 5.5f)
            else -> List(10) { 0f }
        }
        mutateState(markCustomQuick = true) { state ->
            val updatedBands = state.bands.mapIndexed { i, band ->
                val gainMb = (gains[i] * 100f).toInt().coerceIn(state.minGainMb.toInt(), state.maxGainMb.toInt()).toShort()
                band.copy(currentGainMb = gainMb)
            }
            state.copy(
                bands = updatedBands,
                currentPresetIndex = -1,
                currentPresetName = name
            )
        }
    }

    fun saveCustomEqPreset(rawName: String): Boolean {
        val cleanName = rawName.trim()
        if (cleanName.isBlank()) return false
        val current = _effectsState.value
        val gains = current.bands.map { it.currentGainDb }
        val existingIdx = current.customEqPresets.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val preset = if (existingIdx >= 0) {
            current.customEqPresets[existingIdx].copy(
                name = cleanName,
                gainsDb = gains,
                preampDb = current.utilities.preampDb,
                isCustom = true
            )
        } else {
            StudioEqPreset(
                id = "custom_eq_${System.currentTimeMillis()}",
                name = cleanName,
                gainsDb = gains,
                preampDb = current.utilities.preampDb,
                isCustom = true
            )
        }
        mutateState { state ->
            val updatedCustom = if (existingIdx >= 0) {
                state.customEqPresets.toMutableList().apply { set(existingIdx, preset) }
            } else {
                state.customEqPresets + preset
            }
            val synced = state.copy(customEqPresets = updatedCustom, currentPresetName = preset.name)
            val newIdx = (StudioEffectDefaults.builtInEqPresets.map { it.name } + updatedCustom.map { it.name })
                .indexOfFirst { it.equals(preset.name, ignoreCase = true) }
            synced.copy(currentPresetIndex = newIdx.toShort())
        }
        persistCustomEqPresets(_effectsState.value.customEqPresets)
        return true
    }

    fun renameCustomEqPreset(presetId: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return false
        var renamed = false
        mutateState { state ->
            val target = state.customEqPresets.firstOrNull { it.id == presetId } ?: return@mutateState state
            renamed = true
            val updatedCustom = state.customEqPresets.map {
                if (it.id == presetId) it.copy(name = cleanName) else it
            }
            val wasSelected = state.currentPresetName.equals(target.name, ignoreCase = true)
            state.copy(
                customEqPresets = updatedCustom,
                currentPresetName = if (wasSelected) cleanName else state.currentPresetName
            )
        }
        if (renamed) {
            persistCustomEqPresets(_effectsState.value.customEqPresets)
        }
        return renamed
    }

    fun deleteCustomEqPreset(presetId: String): Boolean {
        var deleted = false
        mutateState { state ->
            val target = state.customEqPresets.firstOrNull { it.id == presetId } ?: return@mutateState state
            deleted = true
            val updatedCustom = state.customEqPresets.filterNot { it.id == presetId }
            val wasSelected = state.currentPresetName.equals(target.name, ignoreCase = true)
            state.copy(
                customEqPresets = updatedCustom,
                currentPresetName = if (wasSelected) "Custom" else state.currentPresetName,
                currentPresetIndex = if (wasSelected) -1 else state.currentPresetIndex
            )
        }
        if (deleted) {
            persistCustomEqPresets(_effectsState.value.customEqPresets)
        }
        return deleted
    }

    // --- Deep Punchy Bass Engine Controls ---

    fun setBassEngineEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) { it.copy(bassEngine = it.bassEngine.copy(enabled = enabled)) }
    }

    fun setDeepBass(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(bassEngine = it.bassEngine.copy(deepBass = value.coerceIn(0, 100)))
        }
    }

    fun setPunchyBass(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(bassEngine = it.bassEngine.copy(punchyBass = value.coerceIn(0, 100)))
        }
    }

    fun setSubBass(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(bassEngine = it.bassEngine.copy(subBass = value.coerceIn(0, 100)))
        }
    }

    fun setBassGainDb(gainDb: Float) {
        mutateState(markCustomQuick = true) {
            it.copy(bassEngine = it.bassEngine.copy(bassGainDb = gainDb.coerceIn(-6f, 12f)))
        }
    }

    fun setBassImpact(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(bassEngine = it.bassEngine.copy(bassImpact = value.coerceIn(0, 100)))
        }
    }

    fun setBassFrequencyHz(freqHz: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(bassEngine = it.bassEngine.copy(bassFrequencyHz = freqHz.coerceIn(45, 140)))
        }
    }

    fun setBassTightness(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(bassEngine = it.bassEngine.copy(bassTightness = value.coerceIn(0, 100)))
        }
    }

    // --- Pure Concert Hall Reverb Controls ---

    fun setConcertHallEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) { it.copy(concertHall = it.concertHall.copy(enabled = enabled)) }
    }

    fun selectConcertHallPreset(preset: ConcertHallPreset) {
        mutateState(markCustomQuick = true) {
            it.copy(
                concertHall = ConcertHallState(
                    enabled = true,
                    presetName = preset.displayName,
                    hallSize = preset.hallSize,
                    reverbAmount = preset.reverbAmount,
                    roomDepth = preset.roomDepth,
                    earlyReflections = preset.earlyReflections,
                    decayTimeSec = preset.decayTimeSec,
                    wetDryMix = preset.wetDryMix,
                    stereoWidth = preset.stereoWidth
                )
            )
        }
    }

    fun setHallSize(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(concertHall = it.concertHall.copy(hallSize = value.coerceIn(0, 100), presetName = "Custom"))
        }
    }

    fun setReverbAmount(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(concertHall = it.concertHall.copy(reverbAmount = value.coerceIn(0, 100), presetName = "Custom"))
        }
    }

    fun setRoomDepth(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(concertHall = it.concertHall.copy(roomDepth = value.coerceIn(0, 100), presetName = "Custom"))
        }
    }

    fun setEarlyReflections(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(concertHall = it.concertHall.copy(earlyReflections = value.coerceIn(0, 100), presetName = "Custom"))
        }
    }

    fun setDecayTimeSec(seconds: Float) {
        mutateState(markCustomQuick = true) {
            it.copy(concertHall = it.concertHall.copy(decayTimeSec = seconds.coerceIn(0.5f, 4.5f), presetName = "Custom"))
        }
    }

    fun setWetDryMix(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(concertHall = it.concertHall.copy(wetDryMix = value.coerceIn(0, 100), presetName = "Custom"))
        }
    }

    fun setHallStereoWidth(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(concertHall = it.concertHall.copy(stereoWidth = value.coerceIn(0, 100), presetName = "Custom"))
        }
    }

    fun setConcertHallIntensity(intensity: Int) {
        val clamped = intensity.coerceIn(0, 100)
        val t = clamped / 100f
        mutateState(markCustomQuick = true) {
            it.copy(
                concertHall = it.concertHall.copy(
                    hallSize = (30 + t * 65).toInt(),
                    reverbAmount = (25 + t * 65).toInt(),
                    roomDepth = (25 + t * 65).toInt(),
                    earlyReflections = (40 + t * 40).toInt(),
                    decayTimeSec = 1.0f + t * 2.4f,
                    wetDryMix = (12 + t * 30).toInt(),
                    stereoWidth = (55 + t * 38).toInt(),
                    presetName = when {
                        clamped <= 40 -> ConcertHallPreset.SMALL_HALL.displayName
                        clamped <= 75 -> ConcertHallPreset.GRAND_CONCERT_HALL.displayName
                        else -> ConcertHallPreset.CATHEDRAL.displayName
                    }
                )
            )
        }
    }

    fun setConcertHallTier(tier: String) {
        when (tier.lowercase()) {
            "low" -> selectConcertHallPreset(ConcertHallPreset.SMALL_HALL)
            "high" -> selectConcertHallPreset(ConcertHallPreset.CATHEDRAL)
            else -> selectConcertHallPreset(ConcertHallPreset.GRAND_CONCERT_HALL)
        }
    }

    // --- 3D Surround & Mono-Safe Spatial Audio Controls ---

    fun setSurroundEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) { it.copy(surround = it.surround.copy(enabled = enabled)) }
    }

    fun setSurroundIntensity(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(surround = it.surround.copy(intensity = value.coerceIn(0, 100)))
        }
    }

    fun setVirtualSurround(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(surround = it.surround.copy(virtualSurround = value.coerceIn(0, 100)))
        }
    }

    fun setSurroundStereoWidth(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(surround = it.surround.copy(stereoWidth = value.coerceIn(0, 100)))
        }
    }

    fun setSpatialDepth(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(surround = it.surround.copy(spatialDepth = value.coerceIn(0, 100)))
        }
    }

    fun set3DSound(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(surround = it.surround.copy(sound3D = value.coerceIn(0, 100)))
        }
    }

    fun setStereoExpansion(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(surround = it.surround.copy(stereoExpansion = value.coerceIn(0, 100)))
        }
    }

    fun setImmersiveMode(enabled: Boolean) {
        mutateState(markCustomQuick = true) {
            it.copy(surround = it.surround.copy(immersiveMode = enabled))
        }
    }

    // --- HD Crystal Sound Engine Controls ---

    fun setCrystalEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) { it.copy(crystalSound = it.crystalSound.copy(enabled = enabled)) }
    }

    fun setVocalClarity(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(crystalSound = it.crystalSound.copy(vocalClarity = value.coerceIn(0, 100)))
        }
    }

    fun setCrystalTreble(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(crystalSound = it.crystalSound.copy(crystalTreble = value.coerceIn(0, 100)))
        }
    }

    fun setDetailEnhancer(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(crystalSound = it.crystalSound.copy(detailEnhancer = value.coerceIn(0, 100)))
        }
    }

    fun setHighFrequencyControl(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(crystalSound = it.crystalSound.copy(highFrequencyControl = value.coerceIn(0, 100)))
        }
    }

    fun setDynamicRange(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(crystalSound = it.crystalSound.copy(dynamicRange = value.coerceIn(0, 100)))
        }
    }

    fun setSoftClarity(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(crystalSound = it.crystalSound.copy(softClarity = value.coerceIn(0, 100)))
        }
    }

    // --- Additional Pro Utilities ---

    fun setBassBoostEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(bassBoostEnabled = enabled))
        }
    }

    fun setBassBoostStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(bassBoostStrength = clamped))
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) {
            it.copy(
                surround = it.surround.copy(enabled = enabled),
                utilities = it.utilities.copy(virtualizerEnabled = enabled)
            )
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        mutateState(markCustomQuick = true) {
            it.copy(
                surround = it.surround.copy(intensity = (clamped / 10).coerceIn(0, 100)),
                utilities = it.utilities.copy(virtualizerStrength = clamped)
            )
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(loudnessEnabled = enabled))
        }
    }

    fun setLoudnessGain(value: Int) {
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(loudnessGain = value.coerceIn(0, 100)))
        }
    }

    fun setBalance(balance: Float) {
        val clamped = balance.coerceIn(-1f, 1f)
        val snapped = if (kotlin.math.abs(clamped) < 0.04f) 0f else clamped
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(balance = snapped))
        }
    }

    fun setLimiterEnabled(enabled: Boolean) {
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(limiterEnabled = enabled))
        }
    }

    fun setLimiterThresholdDb(thresholdDb: Float) {
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(limiterThresholdDb = thresholdDb.coerceIn(-6.0f, -0.1f)))
        }
    }

    fun setOutputGainDb(gainDb: Float) {
        mutateState(markCustomQuick = true) {
            it.copy(utilities = it.utilities.copy(outputGainDb = gainDb.coerceIn(-12f, 6f)))
        }
    }

    // --- Premium One-Tap Quick Presets ---

    fun selectQuickPreset(presetId: String) {
        val current = _effectsState.value
        val allQuick = StudioEffectDefaults.builtInQuickPresets + current.customQuickPresets
        val preset = allQuick.firstOrNull { it.id == presetId } ?: return

        val eqTarget = (StudioEffectDefaults.builtInEqPresets + current.customEqPresets)
            .firstOrNull { it.name.equals(preset.eqPresetName, ignoreCase = true) }
            ?: StudioEffectDefaults.builtInEqPresets.first()

        mutateState { state ->
            val updatedBands = state.bands.mapIndexed { i, band ->
                val gainDb = eqTarget.gainsDb.getOrElse(i) { 0f }
                val gainMb = (gainDb * 100f).toInt().coerceIn(state.minGainMb.toInt(), state.maxGainMb.toInt()).toShort()
                band.copy(currentGainMb = gainMb)
            }
            val eqIdx = state.presets.indexOfFirst { it.equals(eqTarget.name, ignoreCase = true) }
            state.copy(
                masterEnabled = true,
                equalizerEnabled = true,
                bands = updatedBands,
                currentPresetIndex = eqIdx.toShort(),
                currentPresetName = eqTarget.name,
                eqStrengthPercent = preset.eqStrengthPercent,
                bassEngine = preset.bassEngine,
                concertHall = preset.concertHall,
                surround = preset.surround,
                crystalSound = preset.crystalSound,
                utilities = preset.utilities,
                activeQuickPresetId = preset.id,
                activeQuickPresetName = preset.name
            )
        }
    }

    fun saveCustomQuickPreset(rawName: String): Boolean {
        val cleanName = rawName.trim()
        if (cleanName.isBlank()) return false
        val current = _effectsState.value
        val presetId = "custom_${System.currentTimeMillis()}"
        // Also ensure EQ curve matches current bands
        saveCustomEqPreset("$cleanName EQ")
        val eqName = "$cleanName EQ"

        val customPreset = StudioQuickPreset(
            id = presetId,
            name = cleanName,
            subtitle = "User Custom Studio Profile",
            eqPresetName = eqName,
            eqStrengthPercent = current.eqStrengthPercent,
            bassEngine = current.bassEngine,
            concertHall = current.concertHall,
            surround = current.surround,
            crystalSound = current.crystalSound,
            utilities = current.utilities,
            isCustom = true
        )

        mutateState { state ->
            state.copy(
                customQuickPresets = state.customQuickPresets + customPreset,
                activeQuickPresetId = customPreset.id,
                activeQuickPresetName = customPreset.name
            )
        }
        persistCustomQuickPresets(_effectsState.value.customQuickPresets)
        return true
    }

    fun deleteCustomQuickPreset(presetId: String): Boolean {
        var removed = false
        mutateState { state ->
            if (state.customQuickPresets.none { it.id == presetId }) return@mutateState state
            removed = true
            val filtered = state.customQuickPresets.filterNot { it.id == presetId }
            val wasActive = state.activeQuickPresetId == presetId
            state.copy(
                customQuickPresets = filtered,
                activeQuickPresetId = if (wasActive) "custom" else state.activeQuickPresetId,
                activeQuickPresetName = if (wasActive) "Custom Studio" else state.activeQuickPresetName
            )
        }
        if (removed) {
            persistCustomQuickPresets(_effectsState.value.customQuickPresets)
        }
        return removed
    }

    fun resetToDefaults() {
        mutateState { state ->
            AudioEffectsState(
                masterEnabled = true,
                equalizerEnabled = true,
                bands = StudioEffectDefaults.defaultBands(),
                currentPresetIndex = 0,
                currentPresetName = "Studio Reference",
                eqStrengthPercent = 100,
                customEqPresets = state.customEqPresets,
                customQuickPresets = state.customQuickPresets,
                activeQuickPresetId = "balanced_reference",
                activeQuickPresetName = "Balanced Reference",
                availability = state.availability
            )
        }
    }

    // --- JSON Serialization for SharedPreferences ---

    private fun loadSavedStateLocked() {
        val p = prefs ?: return
        val customEq = loadCustomEqPresets(p)
        val customQuick = loadCustomQuickPresets(p)
        val rawJson = p.getString(KEY_STATE_JSON, null)
        if (rawJson.isNullOrBlank()) {
            _effectsState.update {
                syncMirrorFields(it.copy(customEqPresets = customEq, customQuickPresets = customQuick))
            }
            return
        }

        try {
            val obj = JSONObject(rawJson)
            val bandsJson = obj.optJSONArray("bandsMb")
            val bands = StudioEffectDefaults.defaultBands().mapIndexed { i, b ->
                val gainMb = bandsJson?.optInt(i, b.currentGainMb.toInt())
                    ?.coerceIn(b.minGainMb.toInt(), b.maxGainMb.toInt())
                    ?.toShort() ?: b.currentGainMb
                b.copy(currentGainMb = gainMb)
            }

            val bassObj = obj.optJSONObject("bassEngine")
            val bassEngine = if (bassObj != null) {
                BassEngineState(
                    enabled = bassObj.optBoolean("enabled", false),
                    deepBass = bassObj.optInt("deepBass", 35),
                    punchyBass = bassObj.optInt("punchyBass", 40),
                    subBass = bassObj.optInt("subBass", 25),
                    bassGainDb = bassObj.optDouble("bassGainDb", 0.0).toFloat(),
                    bassImpact = bassObj.optInt("bassImpact", 35),
                    bassFrequencyHz = bassObj.optInt("bassFrequencyHz", 80),
                    bassTightness = bassObj.optInt("bassTightness", 70)
                )
            } else BassEngineState()

            val hallObj = obj.optJSONObject("concertHall")
            val concertHall = if (hallObj != null) {
                ConcertHallState(
                    enabled = hallObj.optBoolean("enabled", false),
                    presetName = hallObj.optString("presetName", ConcertHallPreset.GRAND_CONCERT_HALL.displayName),
                    hallSize = hallObj.optInt("hallSize", ConcertHallPreset.GRAND_CONCERT_HALL.hallSize),
                    reverbAmount = hallObj.optInt("reverbAmount", ConcertHallPreset.GRAND_CONCERT_HALL.reverbAmount),
                    roomDepth = hallObj.optInt("roomDepth", ConcertHallPreset.GRAND_CONCERT_HALL.roomDepth),
                    earlyReflections = hallObj.optInt("earlyReflections", ConcertHallPreset.GRAND_CONCERT_HALL.earlyReflections),
                    decayTimeSec = hallObj.optDouble("decayTimeSec", ConcertHallPreset.GRAND_CONCERT_HALL.decayTimeSec.toDouble()).toFloat(),
                    wetDryMix = hallObj.optInt("wetDryMix", ConcertHallPreset.GRAND_CONCERT_HALL.wetDryMix),
                    stereoWidth = hallObj.optInt("stereoWidth", ConcertHallPreset.GRAND_CONCERT_HALL.stereoWidth)
                )
            } else ConcertHallState()

            val surObj = obj.optJSONObject("surround")
            val surround = if (surObj != null) {
                SurroundSpatialState(
                    enabled = surObj.optBoolean("enabled", false),
                    intensity = surObj.optInt("intensity", 55),
                    virtualSurround = surObj.optInt("virtualSurround", 50),
                    stereoWidth = surObj.optInt("stereoWidth", 62),
                    spatialDepth = surObj.optInt("spatialDepth", 45),
                    sound3D = surObj.optInt("sound3D", 45),
                    stereoExpansion = surObj.optInt("stereoExpansion", 50),
                    immersiveMode = surObj.optBoolean("immersiveMode", false)
                )
            } else SurroundSpatialState()

            val cryObj = obj.optJSONObject("crystalSound")
            val crystal = if (cryObj != null) {
                CrystalSoundState(
                    enabled = cryObj.optBoolean("enabled", false),
                    vocalClarity = cryObj.optInt("vocalClarity", 45),
                    crystalTreble = cryObj.optInt("crystalTreble", 35),
                    detailEnhancer = cryObj.optInt("detailEnhancer", 40),
                    highFrequencyControl = cryObj.optInt("highFrequencyControl", 55),
                    dynamicRange = cryObj.optInt("dynamicRange", 55),
                    softClarity = cryObj.optInt("softClarity", 50)
                )
            } else CrystalSoundState()

            val utilObj = obj.optJSONObject("utilities")
            val utilities = if (utilObj != null) {
                ProUtilitiesState(
                    bassBoostEnabled = utilObj.optBoolean("bassBoostEnabled", false),
                    bassBoostStrength = utilObj.optInt("bassBoostStrength", 0).toShort(),
                    virtualizerEnabled = utilObj.optBoolean("virtualizerEnabled", false),
                    virtualizerStrength = utilObj.optInt("virtualizerStrength", 0).toShort(),
                    loudnessEnabled = utilObj.optBoolean("loudnessEnabled", false),
                    loudnessGain = utilObj.optInt("loudnessGain", 35),
                    balance = utilObj.optDouble("balance", 0.0).toFloat(),
                    limiterEnabled = utilObj.optBoolean("limiterEnabled", true),
                    limiterThresholdDb = utilObj.optDouble("limiterThresholdDb", -0.5).toFloat(),
                    preampDb = utilObj.optDouble("preampDb", 0.0).toFloat(),
                    outputGainDb = utilObj.optDouble("outputGainDb", 0.0).toFloat()
                )
            } else ProUtilitiesState()

            val restored = AudioEffectsState(
                masterEnabled = obj.optBoolean("masterEnabled", true),
                equalizerEnabled = obj.optBoolean("equalizerEnabled", true),
                bands = bands,
                currentPresetIndex = obj.optInt("currentPresetIndex", 0).toShort(),
                currentPresetName = obj.optString("currentPresetName", "Studio Reference"),
                eqStrengthPercent = obj.optInt("eqStrengthPercent", 100),
                customEqPresets = customEq,
                bassEngine = bassEngine,
                concertHall = concertHall,
                surround = surround,
                crystalSound = crystal,
                utilities = utilities,
                activeQuickPresetId = obj.optString("activeQuickPresetId", "balanced_reference"),
                activeQuickPresetName = obj.optString("activeQuickPresetName", "Balanced Reference"),
                customQuickPresets = customQuick
            )
            _effectsState.value = syncMirrorFields(restored)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore saved Studio audio state; using defaults", e)
            _effectsState.update {
                syncMirrorFields(it.copy(customEqPresets = customEq, customQuickPresets = customQuick))
            }
        }
    }

    private fun persistState(state: AudioEffectsState) {
        val p = prefs ?: return
        try {
            val root = JSONObject().apply {
                put("masterEnabled", state.masterEnabled)
                put("equalizerEnabled", state.equalizerEnabled)
                put("currentPresetIndex", state.currentPresetIndex.toInt())
                put("currentPresetName", state.currentPresetName)
                put("eqStrengthPercent", state.eqStrengthPercent)
                put("activeQuickPresetId", state.activeQuickPresetId)
                put("activeQuickPresetName", state.activeQuickPresetName)
                put("bandsMb", JSONArray().apply { state.bands.forEach { put(it.currentGainMb.toInt()) } })
                put("bassEngine", encodeBassEngine(state.bassEngine))
                put("concertHall", encodeConcertHall(state.concertHall))
                put("surround", encodeSurround(state.surround))
                put("crystalSound", encodeCrystal(state.crystalSound))
                put("utilities", encodeUtilities(state.utilities))
            }
            p.edit().putString(KEY_STATE_JSON, root.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist Studio audio state", e)
        }
    }

    private fun persistCustomEqPresets(presets: List<StudioEqPreset>) {
        val p = prefs ?: return
        val arr = JSONArray()
        presets.forEach { preset ->
            arr.put(JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("preampDb", preset.preampDb.toDouble())
                put("gainsDb", JSONArray().apply { preset.gainsDb.forEach { put(it.toDouble()) } })
            })
        }
        p.edit().putString(KEY_CUSTOM_EQ_PRESETS, arr.toString()).apply()
    }

    private fun loadCustomEqPresets(p: SharedPreferences): List<StudioEqPreset> {
        val raw = p.getString(KEY_CUSTOM_EQ_PRESETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val gainsArr = o.optJSONArray("gainsDb")
                val gains = (0 until 10).map { g -> gainsArr?.optDouble(g, 0.0)?.toFloat() ?: 0f }
                StudioEqPreset(
                    id = o.optString("id", "custom_eq_$i"),
                    name = o.optString("name", "Custom $i"),
                    gainsDb = gains,
                    preampDb = o.optDouble("preampDb", 0.0).toFloat(),
                    isCustom = true
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistCustomQuickPresets(presets: List<StudioQuickPreset>) {
        val p = prefs ?: return
        val arr = JSONArray()
        presets.forEach { q ->
            arr.put(JSONObject().apply {
                put("id", q.id)
                put("name", q.name)
                put("subtitle", q.subtitle)
                put("eqPresetName", q.eqPresetName)
                put("eqStrengthPercent", q.eqStrengthPercent)
                put("bassEngine", encodeBassEngine(q.bassEngine))
                put("concertHall", encodeConcertHall(q.concertHall))
                put("surround", encodeSurround(q.surround))
                put("crystalSound", encodeCrystal(q.crystalSound))
                put("utilities", encodeUtilities(q.utilities))
            })
        }
        p.edit().putString(KEY_CUSTOM_QUICK_PRESETS, arr.toString()).apply()
    }

    private fun loadCustomQuickPresets(p: SharedPreferences): List<StudioQuickPreset> {
        val raw = p.getString(KEY_CUSTOM_QUICK_PRESETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val b = o.optJSONObject("bassEngine")
                val h = o.optJSONObject("concertHall")
                val s = o.optJSONObject("surround")
                val c = o.optJSONObject("crystalSound")
                val u = o.optJSONObject("utilities")
                StudioQuickPreset(
                    id = o.optString("id", "custom_$i"),
                    name = o.optString("name", "Custom Studio $i"),
                    subtitle = o.optString("subtitle", "User Custom Studio Profile"),
                    eqPresetName = o.optString("eqPresetName", "Studio Reference"),
                    eqStrengthPercent = o.optInt("eqStrengthPercent", 100),
                    bassEngine = if (b != null) BassEngineState(
                        enabled = b.optBoolean("enabled", false),
                        deepBass = b.optInt("deepBass", 35),
                        punchyBass = b.optInt("punchyBass", 40),
                        subBass = b.optInt("subBass", 25),
                        bassGainDb = b.optDouble("bassGainDb", 0.0).toFloat(),
                        bassImpact = b.optInt("bassImpact", 35),
                        bassFrequencyHz = b.optInt("bassFrequencyHz", 80),
                        bassTightness = b.optInt("bassTightness", 70)
                    ) else BassEngineState(),
                    concertHall = if (h != null) ConcertHallState(
                        enabled = h.optBoolean("enabled", false),
                        presetName = h.optString("presetName", "Grand Concert Hall"),
                        hallSize = h.optInt("hallSize", 72),
                        reverbAmount = h.optInt("reverbAmount", 65),
                        roomDepth = h.optInt("roomDepth", 68),
                        earlyReflections = h.optInt("earlyReflections", 64),
                        decayTimeSec = h.optDouble("decayTimeSec", 2.25).toFloat(),
                        wetDryMix = h.optInt("wetDryMix", 28),
                        stereoWidth = h.optInt("stereoWidth", 82)
                    ) else ConcertHallState(),
                    surround = if (s != null) SurroundSpatialState(
                        enabled = s.optBoolean("enabled", false),
                        intensity = s.optInt("intensity", 55),
                        virtualSurround = s.optInt("virtualSurround", 50),
                        stereoWidth = s.optInt("stereoWidth", 62),
                        spatialDepth = s.optInt("spatialDepth", 45),
                        sound3D = s.optInt("sound3D", 45),
                        stereoExpansion = s.optInt("stereoExpansion", 50),
                        immersiveMode = s.optBoolean("immersiveMode", false)
                    ) else SurroundSpatialState(),
                    crystalSound = if (c != null) CrystalSoundState(
                        enabled = c.optBoolean("enabled", false),
                        vocalClarity = c.optInt("vocalClarity", 45),
                        crystalTreble = c.optInt("crystalTreble", 35),
                        detailEnhancer = c.optInt("detailEnhancer", 40),
                        highFrequencyControl = c.optInt("highFrequencyControl", 55),
                        dynamicRange = c.optInt("dynamicRange", 55),
                        softClarity = c.optInt("softClarity", 50)
                    ) else CrystalSoundState(),
                    utilities = if (u != null) ProUtilitiesState(
                        bassBoostEnabled = u.optBoolean("bassBoostEnabled", false),
                        bassBoostStrength = u.optInt("bassBoostStrength", 0).toShort(),
                        virtualizerEnabled = u.optBoolean("virtualizerEnabled", false),
                        virtualizerStrength = u.optInt("virtualizerStrength", 0).toShort(),
                        loudnessEnabled = u.optBoolean("loudnessEnabled", false),
                        loudnessGain = u.optInt("loudnessGain", 35),
                        balance = u.optDouble("balance", 0.0).toFloat(),
                        limiterEnabled = u.optBoolean("limiterEnabled", true),
                        limiterThresholdDb = u.optDouble("limiterThresholdDb", -0.5).toFloat(),
                        preampDb = u.optDouble("preampDb", 0.0).toFloat(),
                        outputGainDb = u.optDouble("outputGainDb", 0.0).toFloat()
                    ) else ProUtilitiesState(),
                    isCustom = true
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeBassEngine(b: BassEngineState) = JSONObject().apply {
        put("enabled", b.enabled)
        put("deepBass", b.deepBass)
        put("punchyBass", b.punchyBass)
        put("subBass", b.subBass)
        put("bassGainDb", b.bassGainDb.toDouble())
        put("bassImpact", b.bassImpact)
        put("bassFrequencyHz", b.bassFrequencyHz)
        put("bassTightness", b.bassTightness)
    }

    private fun encodeConcertHall(h: ConcertHallState) = JSONObject().apply {
        put("enabled", h.enabled)
        put("presetName", h.presetName)
        put("hallSize", h.hallSize)
        put("reverbAmount", h.reverbAmount)
        put("roomDepth", h.roomDepth)
        put("earlyReflections", h.earlyReflections)
        put("decayTimeSec", h.decayTimeSec.toDouble())
        put("wetDryMix", h.wetDryMix)
        put("stereoWidth", h.stereoWidth)
    }

    private fun encodeSurround(s: SurroundSpatialState) = JSONObject().apply {
        put("enabled", s.enabled)
        put("intensity", s.intensity)
        put("virtualSurround", s.virtualSurround)
        put("stereoWidth", s.stereoWidth)
        put("spatialDepth", s.spatialDepth)
        put("sound3D", s.sound3D)
        put("stereoExpansion", s.stereoExpansion)
        put("immersiveMode", s.immersiveMode)
    }

    private fun encodeCrystal(c: CrystalSoundState) = JSONObject().apply {
        put("enabled", c.enabled)
        put("vocalClarity", c.vocalClarity)
        put("crystalTreble", c.crystalTreble)
        put("detailEnhancer", c.detailEnhancer)
        put("highFrequencyControl", c.highFrequencyControl)
        put("dynamicRange", c.dynamicRange)
        put("softClarity", c.softClarity)
    }

    private fun encodeUtilities(u: ProUtilitiesState) = JSONObject().apply {
        put("bassBoostEnabled", u.bassBoostEnabled)
        put("bassBoostStrength", u.bassBoostStrength.toInt())
        put("virtualizerEnabled", u.virtualizerEnabled)
        put("virtualizerStrength", u.virtualizerStrength.toInt())
        put("loudnessEnabled", u.loudnessEnabled)
        put("loudnessGain", u.loudnessGain)
        put("balance", u.balance.toDouble())
        put("limiterEnabled", u.limiterEnabled)
        put("limiterThresholdDb", u.limiterThresholdDb.toDouble())
        put("preampDb", u.preampDb.toDouble())
        put("outputGainDb", u.outputGainDb.toDouble())
    }
}
