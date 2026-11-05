package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Song
import com.example.ui.theme.LiquidGlassBorderBrush
import com.example.ui.theme.PlayPauseButtonBrush
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CompactNowPlayingBar(
    currentSong: Song?,
    isPlaying: Boolean,
    onBarClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x66000000))
            .clickable(onClick = onBarClick)
            .testTag("compact_now_playing_bar"),
        shape = RoundedCornerShape(26.dp),
        borderBrush = LiquidGlassBorderBrush,
        backgroundColor = Color(0x3514192E),
        borderWidth = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song or App Icon Thumbnail in Glass Frame
            if (currentSong != null) {
                SongAlbumArtwork(
                    song = currentSong,
                    modifier = Modifier.size(46.dp),
                    cornerRadius = 14.dp,
                    showGlowBorder = false
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "USMAN Logo",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title and Artist / Status
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentSong?.title ?: "USMAN MUSIC PLAYER",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentSong?.artist ?: "No song playing",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Previous Button
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("compact_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = TextPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Floating Play/Pause Glass Orb Button with subtle specular reflection
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PlayPauseButtonBrush)
                    .clickable(onClick = onPlayPauseToggle)
                    .testTag("compact_play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Next Button
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("compact_next_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = TextPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
