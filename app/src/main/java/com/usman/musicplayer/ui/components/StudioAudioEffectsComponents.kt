package com.usman.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.playback.AudioEffectsManager
import com.usman.musicplayer.playback.AudioEffectsState
import com.usman.musicplayer.playback.EqualizerBand
import com.usman.musicplayer.playback.StudioEffectDefaults
import com.usman.musicplayer.playback.StudioEqPreset
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.SoftPurple
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

enum class StudioEffectTab(val label: String, val tag: String) {
    EQ("10-Band EQ", "studio_tab_eq"),
    BASS("Deep Bass", "studio_tab_bass"),
    HALL("Concert Hall", "studio_tab_hall"),
    SURROUND("3D Surround", "studio_tab_surround"),
    CRYSTAL("Crystal HD", "studio_tab_crystal"),
    PRO("Pro Controls", "studio_tab_pro")
}

@Composable
fun StudioAudioEffectsPanel(
    modifier: Modifier = Modifier
) {
    val state by AudioEffectsManager.effectsState.collectAsState()
    var activeTab by remember { mutableStateOf(StudioEffectTab.EQ) }
    var showSaveQuickDialog by remember { mutableStateOf(false) }
    var showAvailabilityDetails by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Master Audio Processing + Output / Availability Card
        StudioMasterHeaderCard(
            state = state,
            showAvailabilityDetails = showAvailabilityDetails,
            onToggleAvailabilityDetails = { showAvailabilityDetails = !showAvailabilityDetails },
            onToggleMaster = { AudioEffectsManager.setMasterEnabled(it) },
            onResetAll = { AudioEffectsManager.resetToDefaults() }
        )

        // 2. Premium 8-Preset Quick Selector + Custom Quick Save
        StudioQuickPresetsStrip(
            state = state,
            onSelectQuickPreset = { AudioEffectsManager.selectQuickPreset(it) },
            onDeleteCustomQuickPreset = { AudioEffectsManager.deleteCustomQuickPreset(it) },
            onOpenSaveQuickPreset = { showSaveQuickDialog = true }
        )

        // 3. Module Selector Tabs (D-pad & Touch friendly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioEffectTab.entries.forEach { tab ->
                StudioFocusablePill(
                    label = tab.label,
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    modifier = Modifier.testTag(tab.tag)
                )
            }
        }

        // 4. Active Module Content
        when (activeTab) {
            StudioEffectTab.EQ -> StudioEqualizerModuleCard(state = state)
            StudioEffectTab.BASS -> StudioBassEngineCard(state = state)
            StudioEffectTab.HALL -> StudioConcertHallCard(state = state)
            StudioEffectTab.SURROUND -> StudioSurroundCard(state = state)
            StudioEffectTab.CRYSTAL -> StudioCrystalSoundCard(state = state)
            StudioEffectTab.PRO -> StudioProUtilitiesCard(state = state)
        }
    }

    if (showSaveQuickDialog) {
        PresetNameInputDialog(
            title = "Save Studio Quick Preset",
            initialName = "${state.activeQuickPresetName.removePrefix("Custom ").trim().ifBlank { "My Studio" }} Mix",
            confirmLabel = "Save",
            onDismiss = { showSaveQuickDialog = false },
            onConfirm = { name ->
                AudioEffectsManager.saveCustomQuickPreset(name)
                showSaveQuickDialog = false
            }
        )
    }
}

@Composable
private fun StudioMasterHeaderCard(
    state: AudioEffectsState,
    showAvailabilityDetails: Boolean,
    onToggleAvailabilityDetails: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onResetAll: () -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_master_card"),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x2A12182E)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.masterEnabled) Brush.linearGradient(listOf(NeonCyan, ElectricBlue))
                            else Brush.linearGradient(listOf(Color(0x28FFFFFF), Color(0x14FFFFFF)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = if (state.masterEnabled) Color(0xFF030712) else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Master Audio Processing",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.masterEnabled) {
                            "${state.activeQuickPresetName} • ${state.availability.outputModeDescription}"
                        } else {
                            "Bypassed • Direct unmodified playback"
                        },
                        color = if (state.masterEnabled) SoftCyan else TextMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = state.masterEnabled,
                    onCheckedChange = onToggleMaster,
                    modifier = Modifier.testTag("studio_master_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable(onClick = onToggleAvailabilityDetails)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("studio_availability_toggle"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Effect Availability Status",
                        tint = SoftCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.availability.audioSessionId > 0) {
                            "Session #${state.availability.audioSessionId} • ${state.availability.sampleRateHz / 1000}kHz"
                        } else {
                            "Studio DSP Ready • Limiter ${String.format("%.1f", state.utilities.limiterThresholdDb)} dB"
                        },
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = onResetAll,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x18FFFFFF),
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("studio_reset_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset All Audio Effects",
                        tint = SoftCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (showAvailabilityDetails) {
                Spacer(modifier = Modifier.height(10.dp))
                val av = state.availability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1E0D1222))
                        .padding(12.dp)
                        .testTag("studio_availability_details")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ENGINE & DEVICE CAPABILITY STATUS",
                            color = SoftCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "• Studio PCM DSP (Media3 AudioSink): ${if (av.dspEngineActive) "Processing ${av.encodingName} (${av.sampleRateHz} Hz)" else "Standby (engages on play)"}",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "• Active DSP Modules: 10-Band EQ, Deep/Punchy Bass, Concert Hall Reverb, Mono-Safe Spatial, Crystal Clarity, Balance, Limiter",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "• Hardware AudioFX Probe: EQ=${yesNo(av.hardwareEqualizerSupported)}, Bass=${yesNo(av.hardwareBassBoostSupported)}, Virt=${yesNo(av.hardwareVirtualizerSupported)}, EnvRev=${yesNo(av.hardwareEnvReverbSupported)}, Loudness=${yesNo(av.hardwareLoudnessEnhancerSupported)}",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

private fun yesNo(flag: Boolean): String = if (flag) "Yes" else "DSP Fallback"

@Composable
private fun StudioQuickPresetsStrip(
    state: AudioEffectsState,
    onSelectQuickPreset: (String) -> Unit,
    onDeleteCustomQuickPreset: (String) -> Unit,
    onOpenSaveQuickPreset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STUDIO QUICK PRESETS",
                color = SoftCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2400E5FF))
                    .clickable(onClick = onOpenSaveQuickPreset)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("studio_save_quick_preset_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = "Save Custom Studio Preset",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Mix", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allQuick = StudioEffectDefaults.builtInQuickPresets + state.customQuickPresets
            allQuick.forEach { preset ->
                val isSelected = state.activeQuickPresetId == preset.id
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) Color(0xFF7C3AED) else Color(0x241A2036)
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = when {
                                isFocused -> NeonCyan
                                isSelected -> SoftPurple
                                else -> Color(0x22FFFFFF)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelectQuickPreset(preset.id) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("quick_preset_${preset.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = preset.name,
                            color = if (isSelected) Color.White else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                        Text(
                            text = preset.subtitle,
                            color = if (isSelected) Color(0xFFE9D5FF) else TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    if (preset.isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Custom Studio Preset",
                            tint = Color(0xFFFF8A8A),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDeleteCustomQuickPreset(preset.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioEqualizerModuleCard(state: AudioEffectsState) {
    var showSaveEqDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StudioEqPreset?>(null) }

    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_eq_card"),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x28121626)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Graphic Equalizer",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "10-Band Studio EQ • Preset: ${state.currentPresetName}",
                        color = SoftCyan,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = { showSaveEqDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("eq_save_custom_preset_button")
                ) {
                    Icon(Icons.Default.BookmarkAdd, "Save Custom EQ Preset", tint = NeonCyan)
                }
                Switch(
                    checked = state.equalizerEnabled && state.masterEnabled,
                    enabled = state.masterEnabled,
                    onCheckedChange = { AudioEffectsManager.setEqualizerEnabled(it) },
                    modifier = Modifier.testTag("eq_enable_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPurple,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0x33282C44)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frequency Response Graph Canvas
            EqualizerResponseGraph(
                bands = state.bands,
                strengthPercent = state.eqStrengthPercent,
                enabled = state.masterEnabled && state.equalizerEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .testTag("eq_response_graph")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 13 Built-In + Custom User EQ Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allPresets = StudioEffectDefaults.builtInEqPresets + state.customEqPresets
                allPresets.forEachIndexed { idx, preset ->
                    val isSelected = state.currentPresetName.equals(preset.name, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF7C3AED) else Color(0x221E2338))
                            .clickable(enabled = state.masterEnabled) {
                                AudioEffectsManager.selectPreset(idx.toShort(), preset.name)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("eq_preset_${preset.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (preset.isCustom) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename Preset",
                                tint = SoftCyan,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { renameTarget = preset }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Preset",
                                tint = Color(0xFFFF8A8A),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { AudioEffectsManager.deleteCustomEqPreset(preset.id) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Preamp Gain & Equalizer Strength Sliders
            StudioPercentSliderRow(
                label = "Preamp Gain",
                valueText = String.format("%+.1f dB", state.utilities.preampDb),
                value = state.utilities.preampDb,
                valueRange = -12f..12f,
                enabled = state.masterEnabled,
                accentColor = SoftCyan,
                onValueChange = { AudioEffectsManager.setPreampDb(it) }
            )

            StudioPercentSliderRow(
                label = "Equalizer Strength",
                valueText = "${state.eqStrengthPercent}%",
                value = state.eqStrengthPercent.toFloat(),
                valueRange = 0f..150f,
                enabled = state.masterEnabled && state.equalizerEnabled,
                accentColor = NeonPurple,
                onValueChange = { AudioEffectsManager.setEqStrengthPercent(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "10-BAND FREQUENCY RESPONSE (-12 dB TO +12 dB)",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            state.bands.forEach { band ->
                EqualizerBandSliderRow(
                    band = band,
                    enabled = state.masterEnabled && state.equalizerEnabled,
                    onGainChange = { gainMb -> AudioEffectsManager.setBandGain(band.bandIndex, gainMb) }
                )
            }
        }
    }

    if (showSaveEqDialog) {
        PresetNameInputDialog(
            title = "Save Custom EQ Preset",
            initialName = "My EQ",
            confirmLabel = "Save",
            onDismiss = { showSaveEqDialog = false },
            onConfirm = { name ->
                AudioEffectsManager.saveCustomEqPreset(name)
                showSaveEqDialog = false
            }
        )
    }

    renameTarget?.let { target ->
        PresetNameInputDialog(
            title = "Rename Custom EQ Preset",
            initialName = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                AudioEffectsManager.renameCustomEqPreset(target.id, newName)
                renameTarget = null
            }
        )
    }
}

@Composable
private fun EqualizerResponseGraph(
    bands: List<EqualizerBand>,
    strengthPercent: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = if (enabled) NeonCyan else TextMuted
    val fillTop = if (enabled) NeonPurple.copy(alpha = 0.28f) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x28090D1A))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val midY = h * 0.5f

            // Zero dB baseline
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.5f
            )

            if (bands.size >= 2) {
                val scale = (strengthPercent / 100f)
                val stepX = w / (bands.size - 1).toFloat()
                val pts = bands.mapIndexed { idx, band ->
                    val effDb = (band.currentGainDb * scale).coerceIn(-12f, 12f)
                    val normY = 0.5f - (effDb / 26f)
                    Offset(idx * stepX, normY * h)
                }

                val curvePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts[i]
                        val p1 = pts[i + 1]
                        val cx = (p0.x + p1.x) * 0.5f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                val areaPath = Path().apply {
                    addPath(curvePath)
                    lineTo(pts.last().x, h)
                    lineTo(pts.first().x, h)
                    close()
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(listOf(fillTop, Color.Transparent))
                )
                drawPath(
                    path = curvePath,
                    color = activeColor,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                pts.forEach { p ->
                    drawCircle(color = Color.White, radius = 5.5f, center = p)
                    drawCircle(color = activeColor, radius = 3.2f, center = p)
                }
            }
        }
    }
}

@Composable
private fun EqualizerBandSliderRow(
    band: EqualizerBand,
    enabled: Boolean,
    onGainChange: (Short) -> Unit
) {
    val gainDb = band.currentGainDb
    val formattedGain = String.format("%+.1f dB", gainDb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = band.formattedFreq,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp)
        )
        Slider(
            value = band.currentGainMb.toFloat(),
            onValueChange = { onGainChange(it.toInt().toShort()) },
            valueRange = band.minGainMb.toFloat()..band.maxGainMb.toFloat(),
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .testTag("eq_band_slider_${band.bandIndex}"),
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) NeonCyan else TextMuted,
                activeTrackColor = if (enabled) SoftPurple else Color(0x33282C44),
                inactiveTrackColor = Color(0x221E2338)
            )
        )
        Text(
            text = formattedGain,
            color = if (enabled) (if (gainDb > 0) NeonCyan else if (gainDb < 0) SoftPurple else TextSecondary) else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
internal fun StudioFocusablePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NeonCyan else Color(0x221E2338))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White
                    selected -> NeonCyan
                    else -> Color(0x24FFFFFF)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF030712) else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
internal fun StudioPercentSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (enabled) TextPrimary else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                color = if (enabled) accentColor else TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) accentColor else TextMuted,
                activeTrackColor = if (enabled) accentColor else Color(0x33282C44),
                inactiveTrackColor = Color(0x221E2338)
            )
        )
    }
}

@Composable
internal fun PresetNameInputDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121728),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Preset Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color(0xFF030712)
                )
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
