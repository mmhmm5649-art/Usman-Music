package com.usman.musicplayer.ui.components

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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.usman.musicplayer.R
import com.usman.musicplayer.data.AlbumArtworkCache
import com.usman.musicplayer.data.AppImageLoader
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.NeonPurple
import kotlin.math.abs

@Composable
fun SongAlbumArtwork(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    showGlowBorder: Boolean = true,
    targetSizePx: Int? = null
) {
    val context = LocalContext.current
    val resolvedSizePx = targetSizePx ?: when {
        cornerRadius > 20.dp -> AppImageLoader.SIZE_FULL_ARTWORK_PX
        cornerRadius > 10.dp -> AppImageLoader.SIZE_CARD_THUMB_PX
        else -> AppImageLoader.SIZE_MICRO_THUMB_PX
    }

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val borderBrush = remember(showGlowBorder) {
        if (showGlowBorder) {
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
    }

    val artUri = song?.albumArtUri?.trim()?.takeIf { it.isNotEmpty() }
    val isLocalAudioFile = song != null && song.filePath.isNotBlank() && !song.filePath.startsWith("http")

    Box(
        modifier = modifier
            .clip(shape)
            .border(if (showGlowBorder) 2.dp else 1.dp, borderBrush, shape)
            .background(Color(0xFF0F1123)),
        contentAlignment = Alignment.Center
    ) {
        if (artUri != null) {
            val imageLoader = remember(context) { AppImageLoader.get(context) }
            val imageRequest = remember(context, artUri, resolvedSizePx) {
                AppImageLoader.buildImageRequest(
                    context = context,
                    data = artUri,
                    widthPx = resolvedSizePx,
                    heightPx = resolvedSizePx,
                    crossfadeMs = 140
                )
            }
            val painter = rememberAsyncImagePainter(
                model = imageRequest,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop
            )
            val painterState = painter.state

            var embeddedFallbackBitmap by remember(song?.id, artUri, resolvedSizePx) {
                mutableStateOf<Bitmap?>(
                    if (isLocalAudioFile) {
                        AlbumArtworkCache.getCached(song!!.id, null, resolvedSizePx, resolvedSizePx)
                    } else {
                        null
                    }
                )
            }

            if (painterState is AsyncImagePainter.State.Error && isLocalAudioFile && embeddedFallbackBitmap == null) {
                LaunchedEffect(song!!.id, song.filePath, resolvedSizePx) {
                    embeddedFallbackBitmap = AlbumArtworkCache.loadArtwork(
                        context = context,
                        songId = song.id,
                        albumArtUri = null,
                        filePath = song.filePath,
                        targetWidth = resolvedSizePx,
                        targetHeight = resolvedSizePx
                    )
                }
            }

            if (painterState !is AsyncImagePainter.State.Success) {
                val fallback = embeddedFallbackBitmap
                if (fallback != null) {
                    Image(
                        bitmap = fallback.asImageBitmap(),
                        contentDescription = "Album Art for ${song?.title ?: "Track"}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ProceduralSongArtworkPlaceholder(
                        song = song,
                        cornerRadius = cornerRadius
                    )
                }
            }

            if (painterState !is AsyncImagePainter.State.Error) {
                Image(
                    painter = painter,
                    contentDescription = "Album Art for ${song?.title ?: "Track"}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (isLocalAudioFile) {
            var localBitmap by remember(song!!.id, song.filePath, resolvedSizePx) {
                mutableStateOf(
                    AlbumArtworkCache.getCached(song.id, null, resolvedSizePx, resolvedSizePx)
                )
            }

            LaunchedEffect(song!!.id, song.filePath, resolvedSizePx) {
                if (localBitmap == null) {
                    localBitmap = AlbumArtworkCache.loadArtwork(
                        context = context,
                        songId = song.id,
                        albumArtUri = null,
                        filePath = song.filePath,
                        targetWidth = resolvedSizePx,
                        targetHeight = resolvedSizePx
                    )
                }
            }

            val bmp = localBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Album Art for ${song.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ProceduralSongArtworkPlaceholder(
                    song = song,
                    cornerRadius = cornerRadius
                )
            }
        } else {
            ProceduralSongArtworkPlaceholder(
                song = song,
                cornerRadius = cornerRadius
            )
        }
    }
}

@Composable
private fun ProceduralSongArtworkPlaceholder(
    song: Song?,
    cornerRadius: Dp
) {
    val seed = (song?.title?.hashCode() ?: 0) xor (song?.artist?.hashCode() ?: 0)
    val colors = remember(seed) { generateProceduralCoverColors(seed) }

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
