package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.model.UserProfileState
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.StudioAudioEffectsPanel
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    uiState: MusicPlayerUiState,
    onRescanClick: () -> Unit = {},
    onOpenExtensions: () -> Unit = {},
    onToggleProvider: (String, Boolean) -> Unit = { _, _ -> },
    onSelectStreamingQuality: (StreamingQuality) -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                    text = "Profile, Streaming Quality & Studio Feel audio engine",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_screen_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp)
            ) {
                // Section 1: Profile
                item(key = "profile_section") {
                    SettingsSectionHeader(
                        title = "PROFILE",
                        modifier = Modifier.testTag("settings_section_profile")
                    )
                    ProfileAccountSettingsCard(
                        profile = uiState.userProfile
                    )
                }

                // Section 2: Streaming Quality
                item(key = "streaming_quality_section") {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(
                        title = "STREAMING QUALITY",
                        modifier = Modifier.testTag("settings_section_streaming_quality")
                    )
                    StreamingQualitySettingsCard(
                        selectedQuality = uiState.selectedStreamingQuality,
                        activeQualityLabel = uiState.activeStreamQualityLabel,
                        fallbackNotice = uiState.activeStreamFallbackNotice,
                        isSwitching = uiState.isStreamingQualitySwitching,
                        onSelectQuality = onSelectStreamingQuality
                    )
                }

                // Section 3: Audio Effects / Equalizer (Studio Feel Audio Engine)
                item(key = "equalizer_section") {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsSectionHeader(
                        title = "AUDIO EFFECTS / EQUALIZER",
                        modifier = Modifier.testTag("settings_section_equalizer")
                    )
                    AudioEffectsSection()
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun ProfileAccountSettingsCard(
    profile: UserProfileState
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_settings_card"),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x2614182E)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x2600E5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile Avatar",
                        tint = SoftCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "USMAN MUSIC LISTENER",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_account_name")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Local & Streaming Audio Ready",
                        color = SoftCyan,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_account_subtitle")
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingQualitySettingsCard(
    selectedQuality: StreamingQuality,
    activeQualityLabel: String?,
    fallbackNotice: String?,
    isSwitching: Boolean,
    onSelectQuality: (StreamingQuality) -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_streaming_quality_card"),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0x2614182E)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Online Audio Quality",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Applies to online streaming & Listen Together (local MP3 files unaffected)",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
                if (isSwitching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SoftCyan
                    )
                } else if (!activeQualityLabel.isNullOrBlank()) {
                    Text(
                        text = activeQualityLabel,
                        color = SoftCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("settings_active_quality_label")
                    )
                }
            }

            if (!fallbackNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = fallbackNotice,
                    color = Color(0xFFFDE68A),
                    fontSize = 11.5.sp,
                    modifier = Modifier.testTag("settings_quality_fallback_notice")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamingQuality.entries.forEach { quality ->
                    val isSelected = quality == selectedQuality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) Color(0x2E00E5FF) else Color.White.copy(alpha = 0.04f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) SoftCyan.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectQuality(quality) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("settings_quality_option_${quality.key}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quality.displayName,
                                color = if (isSelected) SoftCyan else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = quality.description,
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = SoftCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun AudioEffectsSection() {
    StudioAudioEffectsPanel()
}
