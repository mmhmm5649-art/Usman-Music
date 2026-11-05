package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LibraryTab
import com.example.model.Song
import com.example.ui.components.GlassButtonSurface
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.RainbowAudioVisualizer
import com.example.ui.components.SongAlbumArtwork
import com.example.ui.components.UsmanHeader
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassBorderBrush
import com.example.ui.theme.LiquidGlassButtonBorder
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SoftCyan
import com.example.ui.theme.SoftPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MusicPlayerUiState

@Composable
fun HomeScreen(
    uiState: MusicPlayerUiState,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: (LibraryTab) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRescanClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Apple Music-inspired soft radial ambient lighting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.20f),
                            ElectricBlue.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        radius = 600f
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Official USMAN Header with Branding & Search
            item {
                UsmanHeader(
                    onSearchClick = onNavigateToSearch,
                    onMenuClick = onRescanClick
                )
            }

            // Hero Liquid Glass Visualizer Banner
            item {
                HeroPlayerBanner(
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    onBannerClick = onNavigateToNowPlaying
                )
            }

            // Quick Access Library Hub Cards
            item {
                Spacer(modifier = Modifier.height(14.dp))
                QuickLibraryShortcuts(
                    songCount = uiState.songs.size,
                    albumCount = uiState.albums.size,
                    artistCount = uiState.artists.size,
                    folderCount = uiState.folders.size,
                    onShortcutClick = onNavigateToLibrary
                )
            }

            // Recently Played Section (shown only when user has actually played songs)
            if (uiState.recentlyPlayed.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Recently Played",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.recentlyPlayed, key = { "recent_${it.id}_${it.filePath}" }) { song ->
                            RecentSongCard(
                                song = song,
                                isCurrent = uiState.currentSong?.id == song.id,
                                onClick = { onPlaySong(song) }
                            )
                        }
                    }
                }
            }

            // Device Songs Section Header - placed directly below category shortcuts without empty blank gaps
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Songs",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.songs.size} tracks",
                        color = SoftCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (uiState.songs.isEmpty()) {
                item {
                    EmptySongsScanPrompt(
                        hasPermission = uiState.hasStoragePermission,
                        isScanning = uiState.isScanning,
                        onRequestPermission = onRequestPermissionClick,
                        onRescan = onRescanClick
                    )
                }
            } else {
                items(uiState.songs.take(20), key = { "home_song_${it.id}_${it.filePath}" }) { song ->
                    HomeSongRow(
                        song = song,
                        isCurrent = uiState.currentSong?.id == song.id,
                        isPlaying = uiState.isPlaying && uiState.currentSong?.id == song.id,
                        onClick = { onPlaySong(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPlayerBanner(
    currentSong: Song?,
    isPlaying: Boolean,
    onBannerClick: () -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(28.dp), spotColor = Color(0x66000000))
            .clickable(onClick = onBannerClick)
            .testTag("hero_player_banner"),
        shape = RoundedCornerShape(28.dp),
        backgroundColor = Color(0x3514192E),
        borderBrush = LiquidGlassBorderBrush,
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large thumbnail or official logo
                SongAlbumArtwork(
                    song = currentSong,
                    modifier = Modifier.size(68.dp),
                    cornerRadius = 18.dp,
                    showGlowBorder = true
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) SoftCyan else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "NOW PLAYING" else "USMAN PLAYER READY",
                            color = if (isPlaying) SoftCyan else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = currentSong?.title ?: "Select a song from library",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = currentSong?.artist ?: "Offline Music Player",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-time Rainbow visualizer stream
            RainbowAudioVisualizer(
                isPlaying = isPlaying,
                barCount = 36,
                height = 54.dp
            )
        }
    }
}

@Composable
private fun QuickLibraryShortcuts(
    songCount: Int,
    albumCount: Int,
    artistCount: Int,
    folderCount: Int,
    onShortcutClick: (LibraryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShortcutChip(
            title = "All Songs",
            count = songCount,
            accentColor = SoftCyan,
            modifier = Modifier.weight(1f),
            onClick = { onShortcutClick(LibraryTab.ALL_SONGS) }
        )
        ShortcutChip(
            title = "Albums",
            count = albumCount,
            accentColor = SoftPurple,
            modifier = Modifier.weight(1f),
            onClick = { onShortcutClick(LibraryTab.ALBUMS) }
        )
        ShortcutChip(
            title = "Artists",
            count = artistCount,
            accentColor = NeonMagenta,
            modifier = Modifier.weight(1f),
            onClick = { onShortcutClick(LibraryTab.ARTISTS) }
        )
        ShortcutChip(
            title = "Folders",
            count = folderCount,
            accentColor = ElectricBlue,
            modifier = Modifier.weight(1f),
            onClick = { onShortcutClick(LibraryTab.FOLDERS) }
        )
    }
}

@Composable
private fun ShortcutChip(
    title: String,
    count: Int,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassButtonSurface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("shortcut_$title"),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color(0x2214182E),
        borderBrush = LiquidGlassButtonBorder,
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                color = accentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentSongCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .width(134.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("recent_card_${song.id}"),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = if (isCurrent) Color(0x381E2448) else Color(0x22121528),
        borderBrush = LiquidGlassBorderBrush,
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            SongAlbumArtwork(
                song = song,
                modifier = Modifier
                    .size(114.dp)
                    .clip(RoundedCornerShape(14.dp)),
                cornerRadius = 14.dp,
                showGlowBorder = isCurrent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = song.title,
                color = if (isCurrent) SoftCyan else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeSongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("home_song_row_${song.id}"),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = if (isCurrent) Color(0x351E2448) else Color(0x1814182E),
        borderBrush = if (isCurrent) LiquidGlassBorderBrush else LiquidGlassButtonBorder,
        borderWidth = 1.dp,
        showTopSpecularGleam = isCurrent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongAlbumArtwork(
                song = song,
                modifier = Modifier.size(46.dp),
                cornerRadius = 12.dp,
                showGlowBorder = isCurrent
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent) SoftCyan else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.formattedDuration}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Playing",
                    tint = SoftCyan,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = song.formattedDuration,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun EmptySongsScanPrompt(
    hasPermission: Boolean,
    isScanning: Boolean,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit
) {
    LiquidGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x2814182E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = SoftCyan,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (!hasPermission) "Local Storage Access Required" else "No Music Files Found",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (!hasPermission) {
                    "Grant permission to read audio files from your device storage."
                } else {
                    "USMAN scans your offline audio files (MP3, FLAC, WAV, M4A). Add audio files to Music folder and refresh."
                },
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { if (!hasPermission) onRequestPermission() else onRescan() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoftPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("scan_permission_btn")
            ) {
                Icon(
                    imageVector = if (!hasPermission) Icons.Default.Folder else Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) "Scanning..." else if (!hasPermission) "Grant Audio Permission" else "Scan Music Library"
                )
            }
        }
    }
}
