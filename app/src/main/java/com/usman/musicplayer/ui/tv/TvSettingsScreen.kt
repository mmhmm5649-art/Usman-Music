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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.model.UserProfileState
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

/**
 * TV-Optimized Settings & Tools Screen for Android TV / Google TV.
 */
@Composable
fun TvSettingsScreen(
    onOpenEqualizer: () -> Unit,
    onOpenPlugins: () -> Unit,
    onRescanLibrary: () -> Unit,
    userProfile: UserProfileState = UserProfileState(),
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }

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
                title = if (userProfile.isSignedIn) {
                    "Account (${userProfile.googleDisplayName ?: "Google Account"})"
                } else {
                    "Account (Guest Mode)"
                },
                subtitle = if (userProfile.isSignedIn) {
                    "${userProfile.email ?: "Signed in with Google"} • View profile or Sign Out"
                } else {
                    "Listening as Guest • Sign in with Google"
                },
                onClick = { showProfileDialog = true },
                testTag = "tv_settings_profile_card"
            )

            TvSettingsCard(
                icon = Icons.Default.Equalizer,
                title = "Audio Equalizer & Visualizer Effects",
                subtitle = "Configure 5-band EQ, Bass Boost, Virtualizer & Spectrum visualizer",
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

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            containerColor = Color(0xFF121728),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!userProfile.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = "Google Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = SoftCyan,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (userProfile.isSignedIn) {
                                userProfile.googleDisplayName ?: "Google Account"
                            } else {
                                "Guest Mode"
                            },
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (userProfile.isSignedIn) {
                                userProfile.email ?: "Signed in with Google"
                            } else {
                                "Sign in with Google to connect your account"
                            },
                            color = SoftCyan,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            text = {
                if (!userProfile.authError.isNullOrBlank()) {
                    Text(
                        text = userProfile.authError,
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                if (userProfile.isSignedIn) {
                    OutlinedButton(
                        onClick = {
                            showProfileDialog = false
                            onSignOutClick()
                        },
                        enabled = !userProfile.isAuthInProgress,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("tv_profile_auth_button")
                    ) {
                        Text("Sign Out", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onSignInClick,
                        enabled = !userProfile.isAuthInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color(0xFF030712)
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("tv_profile_auth_button")
                    ) {
                        Text("Continue with Google", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showProfileDialog = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
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
