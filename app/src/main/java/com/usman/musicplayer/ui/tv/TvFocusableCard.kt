package com.usman.musicplayer.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.usman.musicplayer.model.Song

val TvFocusCyan = Color(0xFF00E5FF)
val TvCardBackground = Color(0xFF131B2E)
val TvSurfaceOverlay = Color(0x33FFFFFF)

/**
 * Android TV / Google TV Focusable Song Card.
 * Designed for 10-foot viewing with D-pad navigation:
 * - Enlarges on focus (1.08x scale)
 * - Neon cyan glow border and shadow
 * - Clear title and artist contrast
 * - Direct click/enter response
 */
@Composable
fun TvFocusableSongCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 190.dp,
    cardHeight: Dp = 230.dp
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "tvCardScale"
    )

    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isFocused) 16f else 4f
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else if (isCurrent) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> TvFocusCyan
                    isCurrent -> Color(0xFF38BDF8)
                    else -> Color(0x22FFFFFF)
                },
                shape = shape
            )
            .clip(shape)
            .background(TvCardBackground)
            .clickable(onClick = onClick)
            .focusable()
            .padding(8.dp)
    ) {
        // Artwork Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B1120))
        ) {
            val artUri = song.albumArtUri
            if (!artUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Dark gradient overlay at bottom of art
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                            startY = 70f
                        )
                    )
            )

            // Playing Badge or Quality Tag
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xE600E5FF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Playing",
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PLAYING",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            } else {
                // Audio Quality or Source Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (song.isOnline) "320 KBPS" else "LOCAL",
                        color = if (song.isOnline) Color(0xFF38BDF8) else Color(0xFFA7F3D0),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Provider Tag in top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = song.sourceProvider?.uppercase() ?: "ONLINE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Song Title
        Text(
            text = song.title,
            color = if (isFocused || isCurrent) TvFocusCyan else Color.White,
            fontSize = 13.sp,
            fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Artist & Album
        Text(
            text = song.artist,
            color = if (isFocused) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Focusable button for Android TV controls and settings items.
 */
@Composable
fun TvFocusableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    isPrimary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tvButtonScale"
    )

    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) TvFocusCyan else if (isPrimary) Color(0xFF0284C7) else Color(0x33FFFFFF),
                shape = shape
            )
            .clip(shape)
            .background(
                when {
                    isFocused -> Color(0xFF0C2442)
                    isPrimary -> Color(0xFF0369A1)
                    else -> Color(0xFF1E293B)
                }
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (isFocused) TvFocusCyan else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
