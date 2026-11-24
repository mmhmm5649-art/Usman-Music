package com.usman.musicplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.RecommendationPreference
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.model.UserProfileState
import com.usman.musicplayer.ui.theme.SoftCyan

/**
 * TV-Optimized Settings & Tools Screen for Android TV with full feature parity:
 * Profile, Listen Together shortcut, AI Mood & Dynamic Home controls, Streaming Quality,
 * Audio Effects/Equalizer, Extensions, and Library Rescan.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TvSettingsScreen(
    onOpenEqualizer: () -> Unit,
    onOpenPlugins: () -> Unit,
    onRescanLibrary: () -> Unit,
    userProfile: UserProfileState = UserProfileState(),
    selectedStreamingQuality: StreamingQuality = StreamingQuality.HD,
    activeStreamQualityLabel: String? = null,
    activeStreamFallbackNotice: String? = null,
    onSelectStreamingQuality: (StreamingQuality) -> Unit = {},
    onOpenListenTogether: () -> Unit = {},
    listenTogetherRoomSummary: String? = null,
    isMoodAutoplayEnabled: Boolean = true,
    isDynamicHomeFeedEnabled: Boolean = true,
    recommendationPreference: RecommendationPreference = RecommendationPreference.BALANCED,
    excludeRecentlyPlayed: Boolean = true,
    onToggleMoodAutoplay: (Boolean) -> Unit = {},
    onToggleDynamicHomeFeed: (Boolean) -> Unit = {},
    onSelectRecommendationPreference: (RecommendationPreference) -> Unit = {},
    onToggleExcludeRecentlyPlayed: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, start = 24.dp, end = 24.dp, bottom = 32.dp)
    ) {
        Text(
            text = "SETTINGS & TOOLS",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TvSettingsCard(
                icon = Icons.Default.AccountCircle,
                title = "USMAN MUSIC LISTENER",
                subtitle = "Local & Streaming Audio Ready",
                onClick = {},
                testTag = "tv_settings_profile_card"
            )

            TvSettingsCard(
                icon = Icons.Default.Groups,
                title = "Listen Together & Stereo Split",
                subtitle = listenTogetherRoomSummary ?: "Create or join a synchronized listening room (up to 10 devices or 2-device Left/Right Stereo Split)",
                onClick = onOpenListenTogether,
                testTag = "tv_settings_listen_together_card"
            )

            TvAiMoodPreferencesCard(
                isMoodAutoplayEnabled = isMoodAutoplayEnabled,
                isDynamicHomeFeedEnabled = isDynamicHomeFeedEnabled,
                recommendationPreference = recommendationPreference,
                excludeRecentlyPlayed = excludeRecentlyPlayed,
                onToggleMoodAutoplay = onToggleMoodAutoplay,
                onToggleDynamicHomeFeed = onToggleDynamicHomeFeed,
                onSelectRecommendationPreference = onSelectRecommendationPreference,
                onToggleExcludeRecentlyPlayed = onToggleExcludeRecentlyPlayed
            )

            TvStreamingQualityCard(
                selectedQuality = selectedStreamingQuality,
                activeQualityLabel = activeStreamQualityLabel,
                fallbackNotice = activeStreamFallbackNotice,
                onSelectQuality = onSelectStreamingQuality
            )

            TvSettingsCard(
                icon = Icons.Default.Equalizer,
                title = "Audio Equalizer & Studio Feel Effects",
                subtitle = "Configure 5-band EQ, Bass Boost, Virtualizer & Studio Feel DSP",
                onClick = onOpenEqualizer
            )

            TvSettingsCard(
                icon = Icons.Default.Extension,
                title = "Extension & Plugin Manager",
                subtitle = "Manage installed Echo & online music providers",
                onClick = onOpenPlugins
            )

            TvSettingsCard(
                icon = Icons.Default.Refresh,
                title = "Rescan Device Music Library",
                subtitle = "Scan local storage for newly downloaded tracks",
                onClick = onRescanLibrary
            )
        }
    }
}

@Composable
private fun TvAiMoodPreferencesCard(
    isMoodAutoplayEnabled: Boolean,
    isDynamicHomeFeedEnabled: Boolean,
    recommendationPreference: RecommendationPreference,
    excludeRecentlyPlayed: Boolean,
    onToggleMoodAutoplay: (Boolean) -> Unit,
    onToggleDynamicHomeFeed: (Boolean) -> Unit,
    onSelectRecommendationPreference: (RecommendationPreference) -> Unit,
    onToggleExcludeRecentlyPlayed: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x181E2338))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(20.dp)
            .testTag("tv_settings_ai_mood_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Mood Intelligence",
                    tint = SoftCyan,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "AI Mood Intelligence & Dynamic Home",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "On-device personalization • No account required",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvOptionChip(
                label = "Smart Mood Autoplay: ${if (isMoodAutoplayEnabled) "ON" else "OFF"}",
                isSelected = isMoodAutoplayEnabled,
                onClick = { onToggleMoodAutoplay(!isMoodAutoplayEnabled) },
                modifier = Modifier.weight(1f),
                testTag = "tv_setting_mood_autoplay"
            )
            TvOptionChip(
                label = "Dynamic Home Feed: ${if (isDynamicHomeFeedEnabled) "ON" else "OFF"}",
                isSelected = isDynamicHomeFeedEnabled,
                onClick = { onToggleDynamicHomeFeed(!isDynamicHomeFeedEnabled) },
                modifier = Modifier.weight(1f),
                testTag = "tv_setting_dynamic_home"
            )
            TvOptionChip(
                label = "Exclude Recent: ${if (excludeRecentlyPlayed) "ON" else "OFF"}",
                isSelected = excludeRecentlyPlayed,
                onClick = { onToggleExcludeRecentlyPlayed(!excludeRecentlyPlayed) },
                modifier = Modifier.weight(1f),
                testTag = "tv_setting_exclude_recent"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecommendationPreference.entries.forEach { pref ->
                TvOptionChip(
                    label = pref.displayName,
                    isSelected = recommendationPreference == pref,
                    onClick = { onSelectRecommendationPreference(pref) },
                    modifier = Modifier.weight(1f),
                    testTag = "tv_setting_rec_pref_${pref.name.lowercase()}"
                )
            }
        }
    }
}

@Composable
private fun TvOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isFocused -> SoftCyan.copy(alpha = 0.28f)
                    isSelected -> SoftCyan.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.05f)
                }
            )
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = if (isFocused || isSelected) SoftCyan else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = if (isFocused || isSelected) SoftCyan else Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = SoftCyan,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TvStreamingQualityCard(
    selectedQuality: StreamingQuality,
    activeQualityLabel: String?,
    fallbackNotice: String?,
    onSelectQuality: (StreamingQuality) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x181E2338))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(20.dp)
            .testTag("tv_settings_streaming_quality_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.HighQuality,
                    contentDescription = "Streaming Quality",
                    tint = SoftCyan,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Streaming Quality",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("Selected: ${selectedQuality.displayName}")
                        if (!activeQualityLabel.isNullOrBlank()) append(" • Active: $activeQualityLabel")
                    },
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
                if (!fallbackNotice.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fallbackNotice,
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StreamingQuality.entries.forEach { quality ->
                val isSelected = quality == selectedQuality
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isFocused -> SoftCyan.copy(alpha = 0.26f)
                                isSelected -> SoftCyan.copy(alpha = 0.16f)
                                else -> Color.White.copy(alpha = 0.05f)
                            }
                        )
                        .border(
                            width = if (isFocused || isSelected) 2.dp else 1.dp,
                            color = if (isFocused || isSelected) SoftCyan else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectQuality(quality) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("tv_settings_quality_option_${quality.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = quality.displayName,
                        color = if (isSelected || isFocused) SoftCyan else Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "${quality.displayName} selected",
                            tint = SoftCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) SoftCyan else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x441E2338) else Color(0x181E2338))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SoftCyan,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
