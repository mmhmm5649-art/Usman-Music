package com.usman.musicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.RecommendationPreference
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
    onToggleMoodAutoplay: (Boolean) -> Unit = {},
    onToggleDynamicHomeFeed: (Boolean) -> Unit = {},
    onSelectRecommendationPreference: (RecommendationPreference) -> Unit = {},
    onToggleExcludeRecentlyPlayed: (Boolean) -> Unit = {},
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
                // Section 1: Profile (Listener Profile + Streaming Quality Preference Card)
                item(key = "profile_section") {
                    SettingsSectionHeader(
                        title = "PROFILE",
                        modifier = Modifier.testTag("settings_section_profile")
                    )
                    ProfileAccountSettingsCard(
                        profile = uiState.userProfile
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StreamingQualitySettingsCard(
                        selectedQuality = uiState.selectedStreamingQuality,
                        activeQualityLabel = uiState.activeStreamQualityLabel,
                        fallbackNotice = uiState.activeStreamFallbackNotice,
                        isSwitching = uiState.isStreamingQualitySwitching,
                        onSelectQuality = onSelectStreamingQuality
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    RecommendationPreferencesCard(
                        isMoodAutoplayEnabled = uiState.isMoodAutoplayEnabled,
                        isDynamicHomeFeedEnabled = uiState.isDynamicHomeFeedEnabled,
                        recommendationPreference = uiState.recommendationPreference,
                        excludeRecentlyPlayed = uiState.excludeRecentlyPlayed,
                        onToggleMoodAutoplay = onToggleMoodAutoplay,
                        onToggleDynamicHomeFeed = onToggleDynamicHomeFeed,
                        onSelectRecommendationPreference = onSelectRecommendationPreference,
                        onToggleExcludeRecentlyPlayed = onToggleExcludeRecentlyPlayed
                    )
                }

                // Section 2: Audio Effects / Equalizer (Studio Feel Audio Engine)
                item(key = "equalizer_section") {
                    Spacer(modifier = Modifier.height(22.dp))
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    SoftCyan.copy(alpha = 0.28f),
                                    Color(0xFF3B82F6).copy(alpha = 0.22f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = SoftCyan.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = "Streaming Quality",
                        tint = SoftCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Streaming Quality",
                        color = TextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Selected: ${selectedQuality.displayName}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("settings_selected_quality_summary")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isSwitching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SoftCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = SoftCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Switching",
                            color = SoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (!activeQualityLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SoftCyan.copy(alpha = 0.16f))
                            .border(1.dp, SoftCyan.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeQualityLabel,
                            color = SoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("settings_active_quality_label")
                        )
                    }
                }
            }

            if (!fallbackNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x26F59E0B))
                        .border(1.dp, Color(0x66F59E0B), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Quality Fallback Notice",
                        tint = Color(0xFFFDE68A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fallbackNotice,
                        color = Color(0xFFFDE68A),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("settings_quality_fallback_notice")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamingQuality.entries.forEach { quality ->
                    val isSelected = quality == selectedQuality
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0x2E00E5FF) else Color.White.copy(alpha = 0.04f),
                        animationSpec = tween(durationMillis = 180),
                        label = "qualityRowBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) SoftCyan.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.09f),
                        animationSpec = tween(durationMillis = 180),
                        label = "qualityRowBorder"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(containerColor)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelectQuality(quality) },
                                role = Role.RadioButton
                            )
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
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = quality.description,
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SoftCyan else Color.Transparent)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) SoftCyan else Color.White.copy(alpha = 0.35f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "${quality.displayName} selected",
                                    tint = Color.Black,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .testTag("settings_quality_selected_indicator_${quality.key}")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Local MP3 playback is unaffected. Each phone in Listen Together chooses its own quality independently.",
                color = TextSecondary.copy(alpha = 0.85f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun RecommendationPreferencesCard(
    isMoodAutoplayEnabled: Boolean,
    isDynamicHomeFeedEnabled: Boolean,
    recommendationPreference: RecommendationPreference,
    excludeRecentlyPlayed: Boolean,
    onToggleMoodAutoplay: (Boolean) -> Unit,
    onToggleDynamicHomeFeed: (Boolean) -> Unit,
    onSelectRecommendationPreference: (RecommendationPreference) -> Unit,
    onToggleExcludeRecentlyPlayed: (Boolean) -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_ai_mood_card"),
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Mood Intelligence",
                        tint = SoftCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Mood & Dynamic Feed",
                        color = TextPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "On-device personalization • No account required",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsToggleRow(
                title = "Smart Mood Autoplay",
                subtitle = "Queue mood-matched full tracks when a song ends",
                checked = isMoodAutoplayEnabled,
                onCheckedChange = onToggleMoodAutoplay,
                testTag = "settings_mood_autoplay_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleRow(
                title = "Dynamic Home Feed",
                subtitle = "Rotate mixes, songs & section order each session",
                checked = isDynamicHomeFeedEnabled,
                onCheckedChange = onToggleDynamicHomeFeed,
                testTag = "settings_dynamic_home_switch"
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsToggleRow(
                title = "Exclude Recently Played",
                subtitle = "Avoid repeating songs you just listened to",
                checked = excludeRecentlyPlayed,
                onCheckedChange = onToggleExcludeRecentlyPlayed,
                testTag = "settings_exclude_recent_switch"
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Recommendation Style",
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecommendationPreference.entries.forEach { pref ->
                    val isSelected = pref == recommendationPreference
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(shape)
                            .background(if (isSelected) Color(0x2E00E5FF) else Color.White.copy(alpha = 0.04f))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) SoftCyan.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.1f),
                                shape = shape
                            )
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelectRecommendationPreference(pref) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .testTag("settings_rec_pref_${pref.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pref.displayName,
                            color = if (isSelected) SoftCyan else TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = SoftCyan,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier.testTag(testTag)
        )
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
