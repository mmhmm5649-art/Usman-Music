package com.usman.musicplayer.ui.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AppDestination
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.SoftCyan

/**
 * TV Navigation Rail for Android TV / Google TV.
 * Provides easy D-pad navigation across destinations (Home, Search, Library, Listen Together, Settings).
 */
@Composable
fun TvNavigationRail(
    currentDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    hasCurrentSong: Boolean,
    onOpenListenTogether: () -> Unit = {},
    listenTogetherRoomCode: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(104.dp)
            .background(Color(0xFF0D121F))
            .padding(vertical = 20.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SoftCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "USMAN MUSIC",
                    tint = SoftCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Nav Destinations
            TvNavRailItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentDestination == AppDestination.HOME,
                onClick = { onNavigate(AppDestination.HOME) },
                testTag = "tv_rail_home"
            )

            TvNavRailItem(
                icon = Icons.Default.Search,
                label = "Search",
                isSelected = currentDestination == AppDestination.SEARCH,
                onClick = { onNavigate(AppDestination.SEARCH) },
                testTag = "tv_rail_search"
            )

            TvNavRailItem(
                icon = Icons.Default.LibraryMusic,
                label = "Library",
                isSelected = currentDestination == AppDestination.LIBRARY,
                onClick = { onNavigate(AppDestination.LIBRARY) },
                testTag = "tv_rail_library"
            )

            TvNavRailItem(
                icon = Icons.Default.Groups,
                label = if (!listenTogetherRoomCode.isNullOrBlank()) "Live" else "Together",
                isSelected = !listenTogetherRoomCode.isNullOrBlank(),
                accentColor = if (!listenTogetherRoomCode.isNullOrBlank()) Color(0xFF34D399) else SoftCyan,
                onClick = onOpenListenTogether,
                testTag = "tv_rail_listen_together"
            )

            TvNavRailItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = currentDestination == AppDestination.NEW, // Settings destination
                onClick = { onNavigate(AppDestination.NEW) },
                testTag = "tv_rail_settings"
            )
        }

        if (hasCurrentSong) {
            TvNavRailItem(
                icon = Icons.Default.PlayArrow,
                label = "Player",
                isSelected = false,
                accentColor = SoftCyan,
                onClick = onOpenNowPlaying,
                testTag = "tv_rail_player"
            )
        }
    }
}

@Composable
private fun TvNavRailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    accentColor: Color = SoftCyan,
    testTag: String? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvNavScale_$label"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) accentColor else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isFocused -> accentColor.copy(alpha = 0.35f)
                    isSelected -> Color(0x331E2338)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused || isSelected) accentColor else Color(0xFF94A3B8),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * TV Song Card Component with clear D-pad focus highlight, scale animation, artwork, song info,
 * and optional 3-dot options button for full feature parity with Phone UI.
 */
@Composable
fun TvSongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 160.dp,
    cardHeight: Dp = 214.dp,
    onMoreOptions: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvSongCardScale_${song.id}"
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) SoftCyan else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x441E2338) else Color(0x181E2338))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            SongAlbumArtwork(
                song = song,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp,
                showGlowBorder = false,
                targetSizePx = com.usman.musicplayer.data.AppImageLoader.SIZE_CARD_THUMB_PX
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onMoreOptions != null) {
                var moreFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .onFocusChanged { moreFocused = it.isFocused }
                        .clip(CircleShape)
                        .background(if (moreFocused) SoftCyan.copy(alpha = 0.35f) else Color.Transparent)
                        .border(
                            width = if (moreFocused) 1.5.dp else 0.dp,
                            color = if (moreFocused) SoftCyan else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable(onClick = onMoreOptions)
                        .testTag("tv_song_more_${song.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Song options for ${song.title}",
                        tint = if (moreFocused) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
