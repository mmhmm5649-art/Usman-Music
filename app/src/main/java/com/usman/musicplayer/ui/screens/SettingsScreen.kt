package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.collectAsState
import com.usman.musicplayer.playback.AudioEffectsManager
import com.usman.musicplayer.playback.EqualizerBand
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.SoftPurple
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.R
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@Composable
fun SettingsScreen(
    uiState: MusicPlayerUiState,
    onRescanClick: () -> Unit,
    onOpenExtensions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var highQualityVisualizer by remember { mutableStateOf(true) }
    var glassBlurEffects by remember { mutableStateOf(true) }
    var bassBoostEnhancement by remember { mutableStateOf(true) }
    var ignoreShortAudioFiles by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Settings",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Customize USMAN audio & visuals",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp)
            ) {
                // Section 1: Theme & Visuals
                item {
                    SettingsSectionHeader(title = "THEME & VISUALS")
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x2614182E)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsToggleRow(
                                title = "Liquid Glass & Glow Effects",
                                subtitle = "Enable iOS-inspired translucent panels",
                                icon = Icons.Default.Palette,
                                iconTint = NeonCyan,
                                isChecked = glassBlurEffects,
                                onCheckedChange = { glassBlurEffects = it }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            SettingsToggleRow(
                                title = "Rainbow Audio Spectrum",
                                subtitle = "Reflective laser visualizer on Now Playing",
                                icon = Icons.Default.GraphicEq,
                                iconTint = NeonPurple,
                                isChecked = highQualityVisualizer,
                                onCheckedChange = { highQualityVisualizer = it }
                            )
                        }
                    }
                }

                // Section 2: Real-Time Audio Effects & Graphic Equalizer
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(title = "AUDIO EFFECTS")
                    AudioEffectsSection()
                }

                // Section 3: Sound Effects (Bass Boost, Concert Hall, Virtualizer)
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(title = "SOUND EFFECTS")
                    SoundEffectsSection()
                }

                // Section 4: Library Scanning Settings
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(title = "LIBRARY SCANNING")
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x2614182E)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsToggleRow(
                                title = "Ignore Short Audio (< 30s)",
                                subtitle = "Filter out voice notes and ringtones",
                                icon = Icons.Default.Security,
                                iconTint = ElectricBlue,
                                isChecked = ignoreShortAudioFiles,
                                onCheckedChange = { ignoreShortAudioFiles = it }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onRescanClick)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Scan Music Library Now",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${uiState.songs.size} offline tracks indexed",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Section: Music Extensions & GitHub Plugins
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(title = "MUSIC EXTENSIONS & PLUGINS")
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x2614182E)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Row 1: Manage Extensions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenExtensions)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Manage Music Extensions",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "GitHub & Bloomee ecosystem • Active streaming sources",
                                        color = SoftCyan,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Row 2: Import Provider Package
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenExtensions)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = null,
                                    tint = SoftPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Import Provider Package",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Browse device storage for .eapk, .epk, .json, .zip or APK packages",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Section 5: App Information
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(title = "APP INFORMATION")
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x2614182E)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "USMAN MUSIC PLAYER",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Phase 1: Foundation & Premium UI",
                                    color = NeonCyan,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Version 1.0.0 • Offline Music Player",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeonPurple,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color(0x33282C44)
            )
        )
    }
}

@Composable
fun AudioEffectsSection() {
    val effectsState by AudioEffectsManager.effectsState.collectAsState()

    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = if (effectsState.masterEnabled) NeonCyan else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Master Audio Processing",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (effectsState.masterEnabled) "Active • Real-time hardware DSP" else "Bypassed • Raw audio stream",
                        color = if (effectsState.masterEnabled) SoftCyan else TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = effectsState.masterEnabled,
                    onCheckedChange = { AudioEffectsManager.setMasterEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Graphic Equalizer Toggle & Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Graphic Equalizer",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (effectsState.isEqualizerSupported) "Preset: ${effectsState.currentPresetName}" else "Unavailable on current session",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Reset Button
                OutlinedButton(
                    onClick = { AudioEffectsManager.resetToDefaults() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x18FFFFFF),
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp),
                        tint = SoftCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reset", fontSize = 11.sp)
                }

                Switch(
                    checked = effectsState.equalizerEnabled && effectsState.masterEnabled,
                    enabled = effectsState.masterEnabled && effectsState.isEqualizerSupported,
                    onCheckedChange = { AudioEffectsManager.setEqualizerEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            // Presets Horizontal Selector
            if (effectsState.presets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    effectsState.presets.forEachIndexed { index, name ->
                        val isSelected = effectsState.currentPresetName == name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0xFF7C3AED) else Color(0x221E2338)
                                )
                                .clickable(enabled = effectsState.masterEnabled && effectsState.equalizerEnabled) {
                                    AudioEffectsManager.selectPreset(index.toShort(), name)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Real-Time Frequency Band Sliders
            if (effectsState.bands.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "FREQUENCY BANDS (dB)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                effectsState.bands.forEach { band ->
                    EqualizerBandRow(
                        band = band,
                        enabled = effectsState.masterEnabled && effectsState.equalizerEnabled,
                        onGainChange = { gainMb ->
                            AudioEffectsManager.setBandGain(band.bandIndex, gainMb)
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun EqualizerBandRow(
    band: EqualizerBand,
    enabled: Boolean,
    onGainChange: (Short) -> Unit
) {
    val gainDb = band.currentGainDb
    val sign = if (gainDb > 0) "+" else ""
    val formattedGain = String.format("%s%.1f dB", sign, gainDb)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = band.formattedFreq,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(68.dp)
        )

        Slider(
            value = band.currentGainMb.toFloat(),
            onValueChange = { onGainChange(it.toInt().toShort()) },
            valueRange = band.minGainMb.toFloat()..band.maxGainMb.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) NeonCyan else TextMuted,
                activeTrackColor = if (enabled) SoftPurple else Color(0x33282C44),
                inactiveTrackColor = Color(0x221E2338)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formattedGain,
            color = if (enabled) (if (gainDb > 0) NeonCyan else if (gainDb < 0) SoftPurple else TextSecondary) else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(62.dp)
        )
    }
}

@Composable
fun SoundEffectsSection() {
    val effectsState by AudioEffectsManager.effectsState.collectAsState()

    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 1. Bass Boost
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speaker,
                    contentDescription = null,
                    tint = if (effectsState.bassBoostEnabled) NeonMagenta else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bass Boost",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (effectsState.isBassBoostSupported) {
                            if (effectsState.bassBoostEnabled) "Intensity: ${effectsState.bassBoostStrength / 10}%" else "Off"
                        } else "Unsupported by audio hardware",
                        color = if (effectsState.bassBoostEnabled) SoftCyan else TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = effectsState.bassBoostEnabled && effectsState.masterEnabled,
                    enabled = effectsState.masterEnabled && effectsState.isBassBoostSupported,
                    onCheckedChange = { AudioEffectsManager.setBassBoostEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonMagenta,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            if (effectsState.bassBoostEnabled && effectsState.isBassBoostSupported) {
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = effectsState.bassBoostStrength.toFloat(),
                    onValueChange = { AudioEffectsManager.setBassBoostStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = effectsState.masterEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonMagenta,
                        activeTrackColor = NeonMagenta,
                        inactiveTrackColor = Color(0x221E2338)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Concert Hall (Authentic Acoustic Reverb)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (effectsState.concertHallEnabled) NeonPurple else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Concert Hall",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (effectsState.concertHallEnabled && effectsState.isReverbSupported) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x337C3AED))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = effectsState.concertHallTier,
                                    color = NeonPurple,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = if (effectsState.isReverbSupported) {
                            if (effectsState.concertHallEnabled) {
                                when (effectsState.concertHallTier) {
                                    "Low" -> "Low: Subtle room ambience & early reflections"
                                    "High" -> "High: Spacious, immersive hall reverb & rich tail"
                                    else -> "Medium: Natural concert hall & warm acoustics"
                                }
                            } else "Authentic acoustic hall simulation"
                        } else "Unsupported by audio hardware",
                        color = if (effectsState.concertHallEnabled) SoftCyan else TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = effectsState.concertHallEnabled && effectsState.masterEnabled,
                    enabled = effectsState.masterEnabled && effectsState.isReverbSupported,
                    onCheckedChange = { AudioEffectsManager.setConcertHallEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            if (effectsState.concertHallEnabled && effectsState.isReverbSupported) {
                Spacer(modifier = Modifier.height(10.dp))

                // 3-tier Preset Pills: Low, Medium, High
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("Low", "Subtle Room", 30),
                        Triple("Medium", "Natural Hall", 60),
                        Triple("High", "Spacious Hall", 90)
                    ).forEach { (tier, subtitle, targetVal) ->
                        val isSelected = effectsState.concertHallTier.equals(tier, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFF7C3AED) else Color(0x221E2338)
                                )
                                .clickable(enabled = effectsState.masterEnabled) {
                                    AudioEffectsManager.setConcertHallIntensity(targetVal)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = tier,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = subtitle,
                                    color = if (isSelected) Color(0xFFE9D5FF) else TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Real-Time Smooth Intensity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reverb Intensity",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${effectsState.concertHallIntensity}%",
                        color = NeonPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Slider(
                    value = effectsState.concertHallIntensity.toFloat(),
                    onValueChange = { AudioEffectsManager.setConcertHallIntensity(it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = effectsState.masterEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurple,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = Color(0x221E2338)
                    )
                )

                // Acoustic characteristics badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1A7C3AED))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Smooth early reflections • Warm decay tail • Clean vocal presence",
                        color = Color(0xFFD8B4FE),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Virtualizer / Spatial Audio
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SurroundSound,
                    contentDescription = null,
                    tint = if (effectsState.virtualizerEnabled) ElectricBlue else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Virtualizer (Spatial Audio)",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (effectsState.isVirtualizerSupported) {
                            if (effectsState.virtualizerEnabled) "Width: ${effectsState.virtualizerStrength / 10}%" else "Off"
                        } else "Unsupported by audio hardware",
                        color = if (effectsState.virtualizerEnabled) SoftCyan else TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = effectsState.virtualizerEnabled && effectsState.masterEnabled,
                    enabled = effectsState.masterEnabled && effectsState.isVirtualizerSupported,
                    onCheckedChange = { AudioEffectsManager.setVirtualizerEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ElectricBlue,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            if (effectsState.virtualizerEnabled && effectsState.isVirtualizerSupported) {
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = effectsState.virtualizerStrength.toFloat(),
                    onValueChange = { AudioEffectsManager.setVirtualizerStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = effectsState.masterEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue,
                        inactiveTrackColor = Color(0x221E2338)
                    )
                )
            }
        }
    }
}

