package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MusicPlayerUiState

@Composable
fun SettingsScreen(
    uiState: MusicPlayerUiState,
    onRescanClick: () -> Unit,
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp)
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

                // Section 2: Audio Engine Settings
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(title = "AUDIO ENGINE")
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x2614182E)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsToggleRow(
                                title = "Bass & Audio Clarity Boost",
                                subtitle = "Hardware equalizer audio optimization",
                                icon = Icons.Default.Audiotrack,
                                iconTint = NeonMagenta,
                                isChecked = bassBoostEnhancement,
                                onCheckedChange = { bassBoostEnhancement = it }
                            )
                        }
                    }
                }

                // Section 3: Library Scanning Settings
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

                // Section 4: App Information
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
