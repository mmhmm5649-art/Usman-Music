package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.model.UserProfileState
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.StudioAudioEffectsPanel
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.NeonCyan
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
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
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
                    text = "Account profile & Studio Feel audio engine",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 210.dp)
            ) {
                // Section 1: Profile
                item(key = "profile_section") {
                    SettingsSectionHeader(
                        title = "PROFILE",
                        modifier = Modifier.testTag("settings_section_profile")
                    )
                    ProfileAccountSettingsCard(
                        profile = uiState.userProfile,
                        onSignInClick = onSignInClick,
                        onSignOutClick = onSignOutClick
                    )
                }

                // Section 2: Audio Effects / Equalizer (Studio Feel Audio Engine)
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

@Composable
fun ProfileAccountSettingsCard(
    profile: UserProfileState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
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
                if (!profile.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.photoUrl,
                        contentDescription = "Google Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .testTag("profile_avatar_image")
                    )
                } else {
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
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (profile.isSignedIn) {
                            profile.googleDisplayName?.takeIf { it.isNotBlank() } ?: "Google Account"
                        } else {
                            "Guest Mode"
                        },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_account_name")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (profile.isSignedIn) {
                            profile.email ?: "Signed in with Google"
                        } else {
                            "Sign in with Google to link your profile"
                        },
                        color = if (profile.isSignedIn) SoftCyan else TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_account_subtitle")
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (profile.isSignedIn) {
                    OutlinedButton(
                        onClick = onSignOutClick,
                        enabled = !profile.isAuthInProgress,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0x18FFFFFF),
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("profile_auth_button")
                    ) {
                        if (profile.isAuthInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = SoftCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = SoftCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Sign Out", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Button(
                        onClick = onSignInClick,
                        enabled = !profile.isAuthInProgress,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color(0xFF030712)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("profile_auth_button")
                    ) {
                        if (profile.isAuthInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF030712),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = "Continue with Google",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Continue with Google",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (!profile.authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = profile.authError,
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("profile_auth_error")
                )
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
