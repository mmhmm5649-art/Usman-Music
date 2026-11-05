package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.playback.AudioEffectsManager
import com.usman.musicplayer.playback.AudioEffectsState
import com.usman.musicplayer.ui.components.GlassButtonSurface
import com.usman.musicplayer.ui.components.GlassSlider
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.theme.*

@Composable
fun EqualizerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectsState by AudioEffectsManager.effectsState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.22f),
                            ElectricBlue.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassButtonSurface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0x221E2338)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Equalizer & Audio FX",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "USMAN HD Audio Engine",
                        color = SoftCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                GlassButtonSurface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0x221E2338)
                ) {
                    IconButton(onClick = { AudioEffectsManager.resetToDefaults() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset EQ",
                            tint = NeonCyan
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Master Toggle Card
                item {
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0x261E2544)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (effectsState.masterEnabled) Brush.linearGradient(listOf(NeonCyan, ElectricBlue))
                                            else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Equalizer,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Audio Effects Engine",
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (effectsState.masterEnabled) "Active • Attached to ExoPlayer Session" else "Bypassed",
                                        color = if (effectsState.masterEnabled) NeonCyan else TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Switch(
                                checked = effectsState.masterEnabled,
                                onCheckedChange = { AudioEffectsManager.setMasterEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonPurple,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = Color(0x33FFFFFF)
                                )
                            )
                        }
                    }
                }

                // Preset Selector Chips
                item {
                    Column {
                        Text(
                            text = "EQUALIZER PRESETS",
                            color = SoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val presetList = listOf("Normal", "Bass Boost", "Pop", "Rock", "Classical", "Jazz", "Dance", "Vocal", "Treble Boost", "Custom")
                            items(presetList) { preset ->
                                val isSelected = effectsState.currentPresetName.equals(preset, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isSelected) ActivePillBrush else Brush.linearGradient(listOf(Color(0x221E2338), Color(0x111E2338)))
                                        )
                                        .clickable {
                                            val idx = effectsState.presets.indexOfFirst { it.equals(preset, ignoreCase = true) }
                                            AudioEffectsManager.selectPreset(idx.toShort(), preset)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Graphic Equalizer Frequency Bands
                item {
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0x2014182E)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Graphic Equalizer",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (!effectsState.isEqualizerSupported) {
                                    Text(
                                        text = "(Unsupported on device)",
                                        color = Color.Red.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (effectsState.bands.isEmpty()) {
                                Text(
                                    text = "Connecting hardware equalizer bands...",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                effectsState.bands.forEach { band ->
                                    val rangeMb = (band.maxGainMb - band.minGainMb).toFloat().coerceAtLeast(1f)
                                    val normalized = ((band.currentGainMb - band.minGainMb).toFloat() / rangeMb).coerceIn(0f, 1f)

                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = band.formattedFreq,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = String.format("%+.1f dB", band.currentGainDb),
                                                color = SoftCyan,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        GlassSlider(
                                            value = normalized,
                                            onValueChange = { norm ->
                                                val gainMb = (band.minGainMb + (norm * rangeMb)).toInt().toShort()
                                                AudioEffectsManager.setBandGain(band.bandIndex, gainMb)
                                            },
                                            onValueChangeFinished = {},
                                            modifier = Modifier.fillMaxWidth(),
                                            activeTrackColor = SoftPurple,
                                            inactiveTrackColor = Color(0x283A3F58),
                                            thumbColor = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bass Boost & Virtualizer Controls
                item {
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0x2014182E)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Bass Boost
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VolumeUp, null, tint = NeonMagenta, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Bass Boost", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = if (effectsState.isBassBoostSupported) "${(effectsState.bassBoostStrength / 10f).toInt()}%" else "(Not Supported)",
                                    color = if (effectsState.isBassBoostSupported) SoftCyan else Color.Red.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            if (effectsState.isBassBoostSupported) {
                                GlassSlider(
                                    value = effectsState.bassBoostStrength / 1000f,
                                    onValueChange = {
                                        AudioEffectsManager.setBassBoostEnabled(it > 0f)
                                        AudioEffectsManager.setBassBoostStrength((it * 1000f).toInt().toShort())
                                    },
                                    onValueChangeFinished = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    activeTrackColor = NeonMagenta,
                                    inactiveTrackColor = Color(0x283A3F58)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3D Spatial Virtualizer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SurroundSound, null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("3D Spatial Virtualizer", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = if (effectsState.isVirtualizerSupported) "${(effectsState.virtualizerStrength / 10f).toInt()}%" else "(Not Supported)",
                                    color = if (effectsState.isVirtualizerSupported) SoftCyan else Color.Red.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            if (effectsState.isVirtualizerSupported) {
                                GlassSlider(
                                    value = effectsState.virtualizerStrength / 1000f,
                                    onValueChange = {
                                        AudioEffectsManager.setVirtualizerEnabled(it > 0f)
                                        AudioEffectsManager.setVirtualizerStrength((it * 1000f).toInt().toShort())
                                    },
                                    onValueChangeFinished = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    activeTrackColor = ElectricBlue,
                                    inactiveTrackColor = Color(0x283A3F58)
                                )
                            }
                        }
                    }
                }

                // Concert Hall Reverb
                item {
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0x2014182E)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Concert Hall Acoustic Reverb", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Switch(
                                    checked = effectsState.concertHallEnabled,
                                    onCheckedChange = { AudioEffectsManager.setConcertHallEnabled(it) },
                                    enabled = effectsState.isReverbSupported
                                )
                            }
                            if (effectsState.isReverbSupported) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Reverb Intensity", color = TextSecondary, fontSize = 12.sp)
                                    Text("${effectsState.concertHallIntensity}% (${effectsState.concertHallTier})", color = SoftCyan, fontSize = 12.sp)
                                }
                                GlassSlider(
                                    value = effectsState.concertHallIntensity / 100f,
                                    onValueChange = {
                                        AudioEffectsManager.setConcertHallIntensity((it * 100f).toInt())
                                    },
                                    onValueChangeFinished = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    activeTrackColor = NeonPurple,
                                    inactiveTrackColor = Color(0x283A3F58)
                                )
                            } else {
                                Text(
                                    text = "Concert Hall Reverb is not supported by device hardware.",
                                    color = Color.Red.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}
