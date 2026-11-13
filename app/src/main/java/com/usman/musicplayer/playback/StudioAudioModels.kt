package com.usman.musicplayer.playback

/**
 * Data model for a single frequency band in the 10-band Studio Graphic Equalizer.
 */
data class EqualizerBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val formattedFreq: String,
    val minGainMb: Short = -1200,
    val maxGainMb: Short = 1200,
    val currentGainMb: Short = 0
) {
    val currentGainDb: Float
        get() = currentGainMb / 100f

    val minGainDb: Float
        get() = minGainMb / 100f

    val maxGainDb: Float
        get() = maxGainMb / 100f
}

/**
 * 10-Band EQ preset definition (built-in or user custom).
 */
data class StudioEqPreset(
    val id: String,
    val name: String,
    val gainsDb: List<Float>,
    val preampDb: Float = 0f,
    val isCustom: Boolean = false
)

/**
 * Deep & Punchy multi-stage Bass Engine state.
 * Separates sub-bass warmth, deep bass depth, and tight kick punch without muddying vocals.
 */
data class BassEngineState(
    val enabled: Boolean = false,
    val deepBass: Int = 35,          // 0..100 (low-end depth 45-65Hz)
    val punchyBass: Int = 40,        // 0..100 (tight kick attack at bassFrequencyHz + anti-mud cut)
    val subBass: Int = 25,           // 0..100 (35-45Hz sub-harmonic fullness)
    val bassGainDb: Float = 0f,      // -6f..+12f dB
    val bassImpact: Int = 35,        // 0..100 (dynamic transient punch shaper)
    val bassFrequencyHz: Int = 80,   // 45..140 Hz (punch focus frequency)
    val bassTightness: Int = 70      // 0..100 (higher = tighter Q & faster decay, clean vocals)
)

/**
 * Concert Hall Reverb presets calibrated for spacious acoustics without vocal mud.
 */
enum class ConcertHallPreset(
    val displayName: String,
    val hallSize: Int,
    val reverbAmount: Int,
    val roomDepth: Int,
    val earlyReflections: Int,
    val decayTimeSec: Float,
    val wetDryMix: Int,
    val stereoWidth: Int
) {
    SMALL_HALL("Small Hall", 35, 38, 30, 52, 1.15f, 20, 62),
    GRAND_CONCERT_HALL("Grand Concert Hall", 72, 65, 68, 64, 2.25f, 28, 82),
    ROYAL_AUDITORIUM("Royal Auditorium", 60, 54, 58, 70, 1.85f, 24, 76),
    LIVE_STAGE("Live Stage", 48, 48, 42, 74, 1.45f, 22, 78),
    CATHEDRAL("Cathedral", 94, 86, 88, 60, 3.60f, 36, 92)
}

data class ConcertHallState(
    val enabled: Boolean = false,
    val presetName: String = ConcertHallPreset.GRAND_CONCERT_HALL.displayName,
    val hallSize: Int = ConcertHallPreset.GRAND_CONCERT_HALL.hallSize,          // 0..100
    val reverbAmount: Int = ConcertHallPreset.GRAND_CONCERT_HALL.reverbAmount,  // 0..100
    val roomDepth: Int = ConcertHallPreset.GRAND_CONCERT_HALL.roomDepth,        // 0..100
    val earlyReflections: Int = ConcertHallPreset.GRAND_CONCERT_HALL.earlyReflections, // 0..100
    val decayTimeSec: Float = ConcertHallPreset.GRAND_CONCERT_HALL.decayTimeSec, // 0.5f..4.5f
    val wetDryMix: Int = ConcertHallPreset.GRAND_CONCERT_HALL.wetDryMix,        // 0..100
    val stereoWidth: Int = ConcertHallPreset.GRAND_CONCERT_HALL.stereoWidth     // 0..100
)

/**
 * Mono-compatible stereo virtualization & spatial stage engine.
 */
data class SurroundSpatialState(
    val enabled: Boolean = false,
    val intensity: Int = 55,         // 0..100 overall depth/width multiplier
    val virtualSurround: Int = 50,   // 0..100 interaural cross-ambience
    val stereoWidth: Int = 62,       // 0..100 (50 = natural, >50 wider, <50 narrower; sub stays mono)
    val spatialDepth: Int = 45,      // 0..100 front-back cues
    val sound3D: Int = 45,           // 0..100 pinna spatial contour
    val stereoExpansion: Int = 50,   // 0..100 upper-mid stereo spread
    val immersiveMode: Boolean = false
)

/**
 * Crystal clarity & vocal definition DSP state.
 */
data class CrystalSoundState(
    val enabled: Boolean = false,
    val vocalClarity: Int = 45,         // 0..100 (2.4kHz vocal focus)
    val crystalTreble: Int = 35,        // 0..100 (11.5kHz air shelf)
    val detailEnhancer: Int = 40,       // 0..100 (5.2kHz micro-detail presence)
    val highFrequencyControl: Int = 55, // 0..100 (50 = neutral, <50 softer, >50 brighter)
    val dynamicRange: Int = 55,         // 0..100 (50 = neutral, >50 crisp transients, <50 night level)
    val softClarity: Int = 50           // 0..100 (reduces harsh 3.5kHz sibilance/glare)
)

/**
 * Additional studio utilities, loudness, balance, preamp, output gain, and anti-clipping limiter.
 */
data class ProUtilitiesState(
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0,       // 0..1000
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0,     // 0..1000
    val loudnessEnabled: Boolean = false,
    val loudnessGain: Int = 35,             // 0..100 (ISO low-volume contour)
    val balance: Float = 0f,                // -1f (Left) .. 0f (Center) .. +1f (Right)
    val limiterEnabled: Boolean = true,     // Enabled by default to prevent digital clipping
    val limiterThresholdDb: Float = -0.5f,  // -6.0f .. -0.1f dBFS
    val preampDb: Float = 0f,               // -12f .. +12f dB
    val outputGainDb: Float = 0f            // -12f .. +6f dB
)

/**
 * Full one-tap Studio Quick Preset combining EQ + Bass + Concert Hall + Surround + Crystal + Utilities.
 */
data class StudioQuickPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val eqPresetName: String,
    val eqStrengthPercent: Int,
    val bassEngine: BassEngineState,
    val concertHall: ConcertHallState,
    val surround: SurroundSpatialState,
    val crystalSound: CrystalSoundState,
    val utilities: ProUtilitiesState,
    val isCustom: Boolean = false
)

/**
 * Honest capability and real-time audio session telemetry status.
 */
data class AudioEffectAvailabilityStatus(
    val audioSessionId: Int = -1,
    val sampleRateHz: Int = 44100,
    val channelCount: Int = 2,
    val encodingName: String = "PCM 16-bit",
    val dspEngineActive: Boolean = false,
    val outputModeDescription: String = "Studio PCM DSP • Stereo Virtualization (Mono-Compatible)",
    val hardwareEqualizerSupported: Boolean = false,
    val hardwareBassBoostSupported: Boolean = false,
    val hardwareVirtualizerSupported: Boolean = false,
    val hardwareEnvReverbSupported: Boolean = false,
    val hardwarePresetReverbSupported: Boolean = false,
    val hardwareLoudnessEnhancerSupported: Boolean = false,
    val hardwareDynamicsSupported: Boolean = false,
    val peakReductionDb: Float = 0f
)

/**
 * Complete unified state for the Studio Feel Audio Engine.
 */
data class AudioEffectsState(
    val masterEnabled: Boolean = true,
    val equalizerEnabled: Boolean = true,
    val isEqualizerSupported: Boolean = true,
    val bands: List<EqualizerBand> = StudioEffectDefaults.defaultBands(),
    val presets: List<String> = StudioEffectDefaults.builtInEqPresets.map { it.name },
    val currentPresetIndex: Short = 0,
    val currentPresetName: String = "Studio Reference",
    val minGainMb: Short = -1200,
    val maxGainMb: Short = 1200,
    val eqStrengthPercent: Int = 100, // 0..150%
    val customEqPresets: List<StudioEqPreset> = emptyList(),

    // Multi-module Studio Engine state
    val bassEngine: BassEngineState = BassEngineState(),
    val concertHall: ConcertHallState = ConcertHallState(),
    val surround: SurroundSpatialState = SurroundSpatialState(),
    val crystalSound: CrystalSoundState = CrystalSoundState(),
    val utilities: ProUtilitiesState = ProUtilitiesState(),

    // One-tap Studio Quick Presets
    val activeQuickPresetId: String = "balanced_reference",
    val activeQuickPresetName: String = "Balanced Reference",
    val customQuickPresets: List<StudioQuickPreset> = emptyList(),

    // Honest AudioFX + DSP availability & session telemetry
    val availability: AudioEffectAvailabilityStatus = AudioEffectAvailabilityStatus(),

    // Backward-compatible mirrors for existing callers/tests
    val isBassBoostSupported: Boolean = true,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0,

    val isVirtualizerSupported: Boolean = true,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0,

    val isReverbSupported: Boolean = true,
    val concertHallEnabled: Boolean = false,
    val concertHallIntensity: Int = 60,
    val concertHallTier: String = "Medium",
    val isEnvironmentalReverbActive: Boolean = true,
    val currentReverbPreset: Short = 0
)

object StudioEffectDefaults {
    val BAND_FREQUENCIES_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun formatFreq(hz: Int): String = if (hz >= 1000) {
        val k = hz / 1000
        "${k}k"
    } else {
        "${hz}Hz"
    }

    fun defaultBands(gainsDb: List<Float> = List(10) { 0f }): List<EqualizerBand> =
        BAND_FREQUENCIES_HZ.mapIndexed { index, hz ->
            val gainMb = ((gainsDb.getOrNull(index) ?: 0f) * 100f)
                .toInt()
                .coerceIn(-1200, 1200)
                .toShort()
            EqualizerBand(
                bandIndex = index.toShort(),
                centerFreqHz = hz,
                formattedFreq = formatFreq(hz),
                minGainMb = -1200,
                maxGainMb = 1200,
                currentGainMb = gainMb
            )
        }

    // 10-band curves (31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz)
    val builtInEqPresets: List<StudioEqPreset> = listOf(
        StudioEqPreset("studio_reference", "Studio Reference", listOf(0.5f, 0.5f, 0f, -0.5f, 0f, 0f, 0.5f, 1.0f, 1.0f, 0.5f)),
        StudioEqPreset("deep_bass", "Deep Bass", listOf(6.5f, 5.5f, 3.0f, 0.5f, -0.5f, 0f, 0.5f, 1.5f, 2.0f, 1.5f), preampDb = -2.0f),
        StudioEqPreset("punchy_bass", "Punchy Bass", listOf(3.0f, 5.5f, 4.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.0f, 1.5f, 1.0f), preampDb = -1.5f),
        StudioEqPreset("pure_vocal", "Pure Vocal", listOf(-2.0f, -1.5f, -0.5f, 0.5f, 2.0f, 3.5f, 4.0f, 2.5f, 1.5f, 0.5f)),
        StudioEqPreset("crystal_clear", "Crystal Clear", listOf(0.5f, 0.5f, 0f, -1.0f, 0f, 1.5f, 3.0f, 4.5f, 5.0f, 4.0f), preampDb = -1.0f),
        StudioEqPreset("concert", "Concert", listOf(3.5f, 3.0f, 2.0f, 0.5f, -0.5f, 0f, 1.5f, 2.5f, 3.5f, 3.0f), preampDb = -1.0f),
        StudioEqPreset("dance", "Dance", listOf(5.0f, 4.5f, 2.5f, -1.0f, 0f, 1.0f, 2.5f, 3.5f, 4.0f, 3.0f), preampDb = -1.5f),
        StudioEqPreset("cinematic", "Cinematic", listOf(5.5f, 4.0f, 2.0f, 0f, -0.5f, 1.0f, 2.5f, 3.0f, 3.5f, 2.5f), preampDb = -1.5f),
        StudioEqPreset("classical", "Classical", listOf(2.5f, 2.0f, 1.0f, 0f, -0.5f, 0f, 1.0f, 2.5f, 3.0f, 3.5f)),
        StudioEqPreset("rock", "Rock", listOf(4.0f, 3.0f, 1.5f, -1.5f, -1.0f, 0.5f, 2.5f, 3.5f, 4.0f, 3.0f), preampDb = -1.0f),
        StudioEqPreset("pop", "Pop", listOf(1.5f, 2.5f, 2.0f, 0f, -0.5f, 1.0f, 2.5f, 3.0f, 2.0f, 1.0f)),
        StudioEqPreset("acoustic", "Acoustic", listOf(1.5f, 1.5f, 1.0f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f, 2.5f, 2.0f)),
        StudioEqPreset("custom", "Custom", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    )

    val builtInQuickPresets: List<StudioQuickPreset> = listOf(
        StudioQuickPreset(
            id = "studio_feel",
            name = "Studio Feel",
            subtitle = "Warm punch, clear vocals & spacious polish",
            eqPresetName = "Studio Reference",
            eqStrengthPercent = 100,
            bassEngine = BassEngineState(enabled = true, deepBass = 42, punchyBass = 48, subBass = 30, bassGainDb = 1.5f, bassImpact = 45, bassFrequencyHz = 78, bassTightness = 78),
            concertHall = ConcertHallState(enabled = true, presetName = "Small Hall", hallSize = 35, reverbAmount = 32, roomDepth = 30, earlyReflections = 48, decayTimeSec = 1.15f, wetDryMix = 16, stereoWidth = 65),
            surround = SurroundSpatialState(enabled = true, intensity = 52, virtualSurround = 45, stereoWidth = 62, spatialDepth = 45, sound3D = 42, stereoExpansion = 48, immersiveMode = false),
            crystalSound = CrystalSoundState(enabled = true, vocalClarity = 52, crystalTreble = 42, detailEnhancer = 48, highFrequencyControl = 56, dynamicRange = 58, softClarity = 55),
            utilities = ProUtilitiesState(limiterEnabled = true, limiterThresholdDb = -0.5f, preampDb = -0.5f)
        ),
        StudioQuickPreset(
            id = "pure_concert_hall",
            name = "Pure Concert Hall",
            subtitle = "Grand symphonic depth with clean center vocals",
            eqPresetName = "Concert",
            eqStrengthPercent = 95,
            bassEngine = BassEngineState(enabled = true, deepBass = 30, punchyBass = 28, subBass = 25, bassGainDb = 0.5f, bassImpact = 30, bassFrequencyHz = 72, bassTightness = 75),
            concertHall = ConcertHallState(enabled = true, presetName = "Grand Concert Hall", hallSize = 78, reverbAmount = 70, roomDepth = 72, earlyReflections = 68, decayTimeSec = 2.4f, wetDryMix = 30, stereoWidth = 86),
            surround = SurroundSpatialState(enabled = true, intensity = 65, virtualSurround = 62, stereoWidth = 72, spatialDepth = 68, sound3D = 55, stereoExpansion = 60, immersiveMode = true),
            crystalSound = CrystalSoundState(enabled = true, vocalClarity = 60, crystalTreble = 40, detailEnhancer = 42, highFrequencyControl = 52, dynamicRange = 62, softClarity = 64),
            utilities = ProUtilitiesState(limiterEnabled = true, limiterThresholdDb = -0.5f, preampDb = -1.0f)
        ),
        StudioQuickPreset(
            id = "deep_punchy_bass",
            name = "Deep Punchy Bass",
            subtitle = "Chest-thumping sub & tight kick impact",
            eqPresetName = "Punchy Bass",
            eqStrengthPercent = 105,
            bassEngine = BassEngineState(enabled = true, deepBass = 78, punchyBass = 82, subBass = 68, bassGainDb = 4.0f, bassImpact = 76, bassFrequencyHz = 76, bassTightness = 82),
            concertHall = ConcertHallState(enabled = false),
            surround = SurroundSpatialState(enabled = true, intensity = 42, virtualSurround = 35, stereoWidth = 58, spatialDepth = 35, sound3D = 30, stereoExpansion = 40, immersiveMode = false),
            crystalSound = CrystalSoundState(enabled = true, vocalClarity = 54, crystalTreble = 38, detailEnhancer = 45, highFrequencyControl = 54, dynamicRange = 60, softClarity = 52),
            utilities = ProUtilitiesState(bassBoostEnabled = true, bassBoostStrength = 650, limiterEnabled = true, limiterThresholdDb = -0.6f, preampDb = -2.0f)
        ),
        StudioQuickPreset(
            id = "crystal_clear_hd",
            name = "Crystal Clear HD",
            subtitle = "Sparkling air, crisp instruments & zero harshness",
            eqPresetName = "Crystal Clear",
            eqStrengthPercent = 100,
            bassEngine = BassEngineState(enabled = true, deepBass = 22, punchyBass = 35, subBass = 15, bassGainDb = 0f, bassImpact = 35, bassFrequencyHz = 82, bassTightness = 85),
            concertHall = ConcertHallState(enabled = false),
            surround = SurroundSpatialState(enabled = true, intensity = 40, virtualSurround = 32, stereoWidth = 58, spatialDepth = 38, sound3D = 35, stereoExpansion = 48, immersiveMode = false),
            crystalSound = CrystalSoundState(enabled = true, vocalClarity = 74, crystalTreble = 72, detailEnhancer = 70, highFrequencyControl = 64, dynamicRange = 65, softClarity = 72),
            utilities = ProUtilitiesState(limiterEnabled = true, limiterThresholdDb = -0.5f, preampDb = -1.0f)
        ),
        StudioQuickPreset(
            id = "3d_surround",
            name = "3D Surround",
            subtitle = "Wide mono-safe soundstage & spatial depth",
            eqPresetName = "Studio Reference",
            eqStrengthPercent = 100,
            bassEngine = BassEngineState(enabled = true, deepBass = 36, punchyBass = 42, subBass = 25, bassGainDb = 1.0f, bassImpact = 40, bassFrequencyHz = 80, bassTightness = 75),
            concertHall = ConcertHallState(enabled = true, presetName = "Royal Auditorium", hallSize = 55, reverbAmount = 48, roomDepth = 52, earlyReflections = 65, decayTimeSec = 1.65f, wetDryMix = 20, stereoWidth = 84),
            surround = SurroundSpatialState(enabled = true, intensity = 84, virtualSurround = 82, stereoWidth = 82, spatialDepth = 78, sound3D = 76, stereoExpansion = 78, immersiveMode = true),
            crystalSound = CrystalSoundState(enabled = true, vocalClarity = 58, crystalTreble = 48, detailEnhancer = 54, highFrequencyControl = 56, dynamicRange = 58, softClarity = 60),
            utilities = ProUtilitiesState(virtualizerEnabled = true, virtualizerStrength = 780, limiterEnabled = true, limiterThresholdDb = -0.5f, preampDb = -1.0f)
        ),
        StudioQuickPreset(
            id = "vocal_focus",
            name = "Vocal Focus",
            subtitle = "Intimate upfront vocals & acoustic clarity",
            eqPresetName = "Pure Vocal",
            eqStrengthPercent = 100,
            bassEngine = BassEngineState(enabled = false),
            concertHall = ConcertHallState(enabled = true, presetName = "Small Hall", hallSize = 30, reverbAmount = 26, roomDepth = 25, earlyReflections = 42, decayTimeSec = 0.95f, wetDryMix = 12, stereoWidth = 55),
            surround = SurroundSpatialState(enabled = false),
            crystalSound = CrystalSoundState(enabled = true, vocalClarity = 85, crystalTreble = 45, detailEnhancer = 58, highFrequencyControl = 52, dynamicRange = 52, softClarity = 68),
            utilities = ProUtilitiesState(limiterEnabled = true, limiterThresholdDb = -0.5f)
        ),
        StudioQuickPreset(
            id = "cinematic_experience",
            name = "Cinematic Experience",
            subtitle = "Theater sub weight, wide hall & dramatic impact",
            eqPresetName = "Cinematic",
            eqStrengthPercent = 110,
            bassEngine = BassEngineState(enabled = true, deepBass = 70, punchyBass = 65, subBass = 72, bassGainDb = 3.0f, bassImpact = 70, bassFrequencyHz = 70, bassTightness = 72),
            concertHall = ConcertHallState(enabled = true, presetName = "Grand Concert Hall", hallSize = 84, reverbAmount = 66, roomDepth = 76, earlyReflections = 62, decayTimeSec = 2.5f, wetDryMix = 26, stereoWidth = 88),
            surround = SurroundSpatialState(enabled = true, intensity = 78, virtualSurround = 74, stereoWidth = 78, spatialDepth = 76, sound3D = 72, stereoExpansion = 70, immersiveMode = true),
            crystalSound = CrystalSoundState(enabled = true, vocalClarity = 64, crystalTreble = 54, detailEnhancer = 60, highFrequencyControl = 56, dynamicRange = 72, softClarity = 58),
            utilities = ProUtilitiesState(limiterEnabled = true, limiterThresholdDb = -0.6f, preampDb = -1.5f)
        ),
        StudioQuickPreset(
            id = "balanced_reference",
            name = "Balanced Reference",
            subtitle = "Clean, uncolored studio monitoring baseline",
            eqPresetName = "Studio Reference",
            eqStrengthPercent = 100,
            bassEngine = BassEngineState(enabled = false),
            concertHall = ConcertHallState(enabled = false),
            surround = SurroundSpatialState(enabled = false),
            crystalSound = CrystalSoundState(enabled = false),
            utilities = ProUtilitiesState(limiterEnabled = true, limiterThresholdDb = -0.5f)
        )
    )
}
