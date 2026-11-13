package com.usman.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.playback.AudioEffectsManager
import com.usman.musicplayer.playback.AudioEffectsState
import com.usman.musicplayer.playback.ConcertHallPreset
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.SoftPurple
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

@Composable
fun StudioBassEngineCard(state: AudioEffectsState) {
    val bass = state.bassEngine
    val active = state.masterEnabled && bass.enabled

    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_bass_card"),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Speaker, null, tint = NeonMagenta, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deep Punchy Bass Engine",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (active) "Crossover ${bass.bassFrequencyHz}Hz • Vocal Anti-Mud Active" else "Separate low-end & punch transient shaper",
                        color = if (active) SoftCyan else TextMuted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = active,
                    enabled = state.masterEnabled,
                    onCheckedChange = { AudioEffectsManager.setBassEngineEnabled(it) },
                    modifier = Modifier.testTag("bass_engine_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonMagenta,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            StudioPercentSliderRow(
                label = "Deep Bass (Sub-Warmth Depth)",
                valueText = "${bass.deepBass}%",
                value = bass.deepBass.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonMagenta,
                onValueChange = { AudioEffectsManager.setDeepBass(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Punchy Bass (Tight Kick Focus)",
                valueText = "${bass.punchyBass}%",
                value = bass.punchyBass.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonCyan,
                onValueChange = { AudioEffectsManager.setPunchyBass(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Sub Bass (35–48Hz Extension)",
                valueText = "${bass.subBass}%",
                value = bass.subBass.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonPurple,
                onValueChange = { AudioEffectsManager.setSubBass(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Bass Gain",
                valueText = String.format("%+.1f dB", bass.bassGainDb),
                value = bass.bassGainDb,
                valueRange = -6f..12f,
                enabled = active,
                accentColor = NeonMagenta,
                onValueChange = { AudioEffectsManager.setBassGainDb(it) }
            )
            StudioPercentSliderRow(
                label = "Bass Impact (Transient Attack)",
                valueText = "${bass.bassImpact}%",
                value = bass.bassImpact.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = ElectricBlue,
                onValueChange = { AudioEffectsManager.setBassImpact(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Bass Frequency",
                valueText = "${bass.bassFrequencyHz} Hz",
                value = bass.bassFrequencyHz.toFloat(),
                valueRange = 45f..140f,
                enabled = active,
                accentColor = SoftCyan,
                onValueChange = { AudioEffectsManager.setBassFrequencyHz(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Bass Tightness (Q & Anti-Mud)",
                valueText = "${bass.bassTightness}%",
                value = bass.bassTightness.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = SoftPurple,
                onValueChange = { AudioEffectsManager.setBassTightness(it.toInt()) }
            )
        }
    }
}

@Composable
fun StudioConcertHallCard(state: AudioEffectsState) {
    val hall = state.concertHall
    val active = state.masterEnabled && hall.enabled

    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_hall_card"),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, null, tint = NeonPurple, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pure Concert Hall Effect",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (active) "${hall.presetName} • Decay ${String.format("%.2fs", hall.decayTimeSec)}" else "Acoustic Schroeder hall with vocal-safe HPF",
                        color = if (active) SoftCyan else TextMuted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = active,
                    enabled = state.masterEnabled,
                    onCheckedChange = { AudioEffectsManager.setConcertHallEnabled(it) },
                    modifier = Modifier.testTag("concert_hall_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5 Concert Hall Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConcertHallPreset.entries.forEach { preset ->
                    val isSelected = hall.presetName.equals(preset.displayName, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF7C3AED) else Color(0x221E2338))
                            .clickable(enabled = state.masterEnabled) {
                                AudioEffectsManager.selectConcertHallPreset(preset)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("hall_preset_${preset.name.lowercase()}")
                    ) {
                        Text(
                            text = preset.displayName,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            StudioPercentSliderRow(
                label = "Hall Size",
                valueText = "${hall.hallSize}%",
                value = hall.hallSize.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonPurple,
                onValueChange = { AudioEffectsManager.setHallSize(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Reverb Amount",
                valueText = "${hall.reverbAmount}%",
                value = hall.reverbAmount.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = SoftPurple,
                onValueChange = { AudioEffectsManager.setReverbAmount(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Room Depth (Pre-Delay Stage)",
                valueText = "${hall.roomDepth}%",
                value = hall.roomDepth.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonCyan,
                onValueChange = { AudioEffectsManager.setRoomDepth(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Early Reflections",
                valueText = "${hall.earlyReflections}%",
                value = hall.earlyReflections.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = SoftCyan,
                onValueChange = { AudioEffectsManager.setEarlyReflections(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Decay Time",
                valueText = String.format("%.2f s", hall.decayTimeSec),
                value = hall.decayTimeSec,
                valueRange = 0.5f..4.5f,
                enabled = active,
                accentColor = NeonPurple,
                onValueChange = { AudioEffectsManager.setDecayTimeSec(it) }
            )
            StudioPercentSliderRow(
                label = "Wet / Dry Mix",
                valueText = "${hall.wetDryMix}%",
                value = hall.wetDryMix.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = ElectricBlue,
                onValueChange = { AudioEffectsManager.setWetDryMix(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Stereo Width",
                valueText = "${hall.stereoWidth}%",
                value = hall.stereoWidth.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonCyan,
                onValueChange = { AudioEffectsManager.setHallStereoWidth(it.toInt()) }
            )
        }
    }
}

@Composable
fun StudioSurroundCard(state: AudioEffectsState) {
    val sp = state.surround
    val active = state.masterEnabled && sp.enabled

    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_surround_card"),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SurroundSound, null, tint = ElectricBlue, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "3D Surround & Spatial Audio",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stereo Virtualization • Mono-Compatible Bass",
                        color = if (active) SoftCyan else TextMuted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = active,
                    enabled = state.masterEnabled,
                    onCheckedChange = { AudioEffectsManager.setSurroundEnabled(it) },
                    modifier = Modifier.testTag("surround_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ElectricBlue,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            StudioPercentSliderRow(
                label = "Spatial Intensity",
                valueText = "${sp.intensity}%",
                value = sp.intensity.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonCyan,
                onValueChange = { AudioEffectsManager.setSurroundIntensity(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Virtual Surround",
                valueText = "${sp.virtualSurround}%",
                value = sp.virtualSurround.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = ElectricBlue,
                onValueChange = { AudioEffectsManager.setVirtualSurround(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Stereo Width",
                valueText = "${sp.stereoWidth}%",
                value = sp.stereoWidth.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = SoftCyan,
                onValueChange = { AudioEffectsManager.setSurroundStereoWidth(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Spatial Depth",
                valueText = "${sp.spatialDepth}%",
                value = sp.spatialDepth.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonPurple,
                onValueChange = { AudioEffectsManager.setSpatialDepth(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "3D Sound Stage",
                valueText = "${sp.sound3D}%",
                value = sp.sound3D.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonCyan,
                onValueChange = { AudioEffectsManager.set3DSound(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Stereo Expansion",
                valueText = "${sp.stereoExpansion}%",
                value = sp.stereoExpansion.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = ElectricBlue,
                onValueChange = { AudioEffectsManager.setStereoExpansion(it.toInt()) }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Immersive Mode",
                        color = if (active) TextPrimary else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Enveloping stage contour while keeping mono compatibility",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = sp.immersiveMode,
                    enabled = active,
                    onCheckedChange = { AudioEffectsManager.setImmersiveMode(it) },
                    modifier = Modifier.testTag("surround_immersive_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }
        }
    }
}

@Composable
fun StudioCrystalSoundCard(state: AudioEffectsState) {
    val crystal = state.crystalSound
    val active = state.masterEnabled && crystal.enabled

    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_crystal_card"),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = SoftCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HD Crystal Sound Engine",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Clean vocal definition & smooth highs without harshness",
                        color = if (active) SoftCyan else TextMuted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = active,
                    enabled = state.masterEnabled,
                    onCheckedChange = { AudioEffectsManager.setCrystalEnabled(it) },
                    modifier = Modifier.testTag("crystal_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            StudioPercentSliderRow(
                label = "Vocal Clarity",
                valueText = "${crystal.vocalClarity}%",
                value = crystal.vocalClarity.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonCyan,
                onValueChange = { AudioEffectsManager.setVocalClarity(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Crystal Treble",
                valueText = "${crystal.crystalTreble}%",
                value = crystal.crystalTreble.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = SoftCyan,
                onValueChange = { AudioEffectsManager.setCrystalTreble(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Detail Enhancer",
                valueText = "${crystal.detailEnhancer}%",
                value = crystal.detailEnhancer.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = ElectricBlue,
                onValueChange = { AudioEffectsManager.setDetailEnhancer(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "High Frequency Control",
                valueText = "${crystal.highFrequencyControl}%",
                value = crystal.highFrequencyControl.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = SoftPurple,
                onValueChange = { AudioEffectsManager.setHighFrequencyControl(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Dynamic Range",
                valueText = "${crystal.dynamicRange}%",
                value = crystal.dynamicRange.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = NeonPurple,
                onValueChange = { AudioEffectsManager.setDynamicRange(it.toInt()) }
            )
            StudioPercentSliderRow(
                label = "Soft Clarity (Anti-Harshness)",
                valueText = "${crystal.softClarity}%",
                value = crystal.softClarity.toFloat(),
                valueRange = 0f..100f,
                enabled = active,
                accentColor = SoftCyan,
                onValueChange = { AudioEffectsManager.setSoftClarity(it.toInt()) }
            )
        }
    }
}

@Composable
fun StudioProUtilitiesCard(state: AudioEffectsState) {
    val util = state.utilities
    val enabled = state.masterEnabled

    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_pro_card"),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Master Output, Balance & Dynamics",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bass Boost toggle + strength
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bass Boost", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = util.bassBoostEnabled && enabled,
                    enabled = enabled,
                    onCheckedChange = { AudioEffectsManager.setBassBoostEnabled(it) }
                )
            }
            if (util.bassBoostEnabled) {
                StudioPercentSliderRow(
                    label = "Bass Boost Strength",
                    valueText = "${util.bassBoostStrength / 10}%",
                    value = util.bassBoostStrength.toFloat(),
                    valueRange = 0f..1000f,
                    enabled = enabled,
                    accentColor = NeonMagenta,
                    onValueChange = { AudioEffectsManager.setBassBoostStrength(it.toInt().toShort()) }
                )
            }

            // Virtualizer toggle + strength
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Virtualizer", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = util.virtualizerEnabled && enabled,
                    enabled = enabled,
                    onCheckedChange = { AudioEffectsManager.setVirtualizerEnabled(it) }
                )
            }
            if (util.virtualizerEnabled) {
                StudioPercentSliderRow(
                    label = "Virtualizer Strength",
                    valueText = "${util.virtualizerStrength / 10}%",
                    value = util.virtualizerStrength.toFloat(),
                    valueRange = 0f..1000f,
                    enabled = enabled,
                    accentColor = ElectricBlue,
                    onValueChange = { AudioEffectsManager.setVirtualizerStrength(it.toInt().toShort()) }
                )
            }

            // Loudness Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Loudness Control", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Equal-loudness low & high contour", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = util.loudnessEnabled && enabled,
                    enabled = enabled,
                    onCheckedChange = { AudioEffectsManager.setLoudnessEnabled(it) },
                    modifier = Modifier.testTag("loudness_switch")
                )
            }
            if (util.loudnessEnabled) {
                StudioPercentSliderRow(
                    label = "Loudness Gain",
                    valueText = "${util.loudnessGain}%",
                    value = util.loudnessGain.toFloat(),
                    valueRange = 0f..100f,
                    enabled = enabled,
                    accentColor = SoftCyan,
                    onValueChange = { AudioEffectsManager.setLoudnessGain(it.toInt()) }
                )
            }

            // Balance L / R
            val balLabel = when {
                util.balance < -0.04f -> "L ${(kotlin.math.abs(util.balance) * 100).toInt()}%"
                util.balance > 0.04f -> "R ${(kotlin.math.abs(util.balance) * 100).toInt()}%"
                else -> "Center"
            }
            StudioPercentSliderRow(
                label = "Balance Left / Right",
                valueText = balLabel,
                value = util.balance,
                valueRange = -1f..1f,
                enabled = enabled,
                accentColor = SoftCyan,
                onValueChange = { AudioEffectsManager.setBalance(it) }
            )

            // Preamp & Output Gain
            StudioPercentSliderRow(
                label = "Input Preamp",
                valueText = String.format("%+.1f dB", util.preampDb),
                value = util.preampDb,
                valueRange = -12f..12f,
                enabled = enabled,
                accentColor = NeonCyan,
                onValueChange = { AudioEffectsManager.setPreampDb(it) }
            )
            StudioPercentSliderRow(
                label = "Output Gain",
                valueText = String.format("%+.1f dB", util.outputGainDb),
                value = util.outputGainDb,
                valueRange = -12f..6f,
                enabled = enabled,
                accentColor = NeonPurple,
                onValueChange = { AudioEffectsManager.setOutputGainDb(it) }
            )

            // Peak Limiter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anti-Clipping Peak Limiter", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Protects playback from distortion", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = util.limiterEnabled && enabled,
                    enabled = enabled,
                    onCheckedChange = { AudioEffectsManager.setLimiterEnabled(it) },
                    modifier = Modifier.testTag("limiter_switch")
                )
            }
            if (util.limiterEnabled) {
                StudioPercentSliderRow(
                    label = "Limiter Ceiling",
                    valueText = String.format("%.1f dBFS", util.limiterThresholdDb),
                    value = util.limiterThresholdDb,
                    valueRange = -6.0f..-0.1f,
                    enabled = enabled,
                    accentColor = NeonCyan,
                    onValueChange = { AudioEffectsManager.setLimiterThresholdDb(it) }
                )
            }
        }
    }
}
