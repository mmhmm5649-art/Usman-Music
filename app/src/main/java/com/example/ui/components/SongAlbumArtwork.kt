package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AlbumArtworkCache
import com.example.model.Song
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import kotlin.math.abs

@Composable
fun SongAlbumArtwork(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    showGlowBorder: Boolean = true
) {
    val context = LocalContext.current
    var loadedBitmap by remember(song?.id) { mutableStateOf<Bitmap?>(null) }

    // Load artwork asynchronously using AlbumArtworkCache
    LaunchedEffect(song?.id, song?.albumArtUri, song?.filePath) {
        if (song != null) {
            val targetSize = if (cornerRadius > 20.dp) 512 else 128
            loadedBitmap = AlbumArtworkCache.loadArtwork(
                context = context,
                songId = song.id,
                albumArtUri = song.albumArtUri,
                filePath = song.filePath,
                targetWidth = targetSize,
                targetHeight = targetSize
            )
        } else {
            loadedBitmap = null
        }
    }

    val shape = RoundedCornerShape(cornerRadius)

    val borderBrush = if (showGlowBorder) {
        Brush.linearGradient(
            colors = listOf(
                NeonMagenta.copy(alpha = 0.9f),
                NeonPurple.copy(alpha = 0.8f),
                NeonCyan.copy(alpha = 0.9f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(if (showGlowBorder) 2.dp else 1.dp, borderBrush, shape)
            .background(Color(0xFF0F1123)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = loadedBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album Art for ${song?.title ?: "Track"}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // When song is offline and doesn't have embedded ID3 cover, generate a deterministic
            // premium cosmic/neon gradient artwork tailored specifically to the song's title & artist
            val seed = (song?.title?.hashCode() ?: 0) xor (song?.artist?.hashCode() ?: 0)
            val colors = remember(seed) {
                generateProceduralCoverColors(seed)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = colors,
                            radius = 450f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Official Usman music icon or Music symbol
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "USMAN Music Artwork",
                    modifier = Modifier
                        .size(if (cornerRadius > 20.dp) 110.dp else 36.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    alpha = 0.9f
                )
            }
        }
    }
}

private fun generateProceduralCoverColors(seed: Int): List<Color> {
    val absSeed = abs(seed)
    val palettes = listOf(
        listOf(Color(0xFF2E0854), Color(0xFF8B008B), Color(0xFF0D0221), Color(0xFF05010E)),
        listOf(Color(0xFF003B73), Color(0xFF0074B7), Color(0xFF001F3F), Color(0xFF000814)),
        listOf(Color(0xFF590242), Color(0xFF99004C), Color(0xFF1E0118), Color(0xFF0A0008)),
        listOf(Color(0xFF004D40), Color(0xFF00B4D8), Color(0xFF051923), Color(0xFF000814)),
        listOf(Color(0xFF7209B7), Color(0xFFF72585), Color(0xFF3A0CA3), Color(0xFF0B011E))
    )
    return palettes[absSeed % palettes.size]
}
