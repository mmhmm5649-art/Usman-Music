package com.usman.musicplayer.ui.tv

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@Composable
fun TvHomeScreen(
    uiState: MusicPlayerUiState,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val featuredSong = uiState.onlineTrendingSongs.firstOrNull()
        ?: uiState.songs.firstOrNull()
        ?: uiState.onlineIndianSongs.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero Featured Banner
        if (featuredSong != null) {
            item {
                TvFeaturedHeroBanner(
                    song = featuredSong,
                    onPlay = { onPlaySong(featuredSong) }
                )
            }
        }

        // Recently Played
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                TvSongCarousel(
                    title = "Recently Played",
                    subtitle = "Pick up where you left off",
                    songs = uiState.recentlyPlayed,
                    currentSongId = uiState.currentSong?.id,
                    isPlaying = uiState.isPlaying,
                    onSongClick = onPlaySong
                )
            }
        }

        // JioSaavn Trending Hits
        if (uiState.onlineTrendingSongs.isNotEmpty()) {
            item {
                TvSongCarousel(
                    title = "Trending on JioSaavn",
                    subtitle = "Stream in 320kbps HD Audio",
                    songs = uiState.onlineTrendingSongs,
                    currentSongId = uiState.currentSong?.id,
                    isPlaying = uiState.isPlaying,
                    onSongClick = onPlaySong
                )
            }
        }

        // Telugu & Indian Hits
        val indianHits = if (uiState.onlineIndianSongs.isNotEmpty()) uiState.onlineIndianSongs else uiState.categorySongs
        if (indianHits.isNotEmpty()) {
            item {
                TvSongCarousel(
                    title = "Indian & Telugu Top Hits",
                    subtitle = "Regional favorites & viral anthems",
                    songs = indianHits,
                    currentSongId = uiState.currentSong?.id,
                    isPlaying = uiState.isPlaying,
                    onSongClick = onPlaySong
                )
            }
        }

        // Phonk & Electronic Energy
        if (uiState.onlinePhonkSongs.isNotEmpty()) {
            item {
                TvSongCarousel(
                    title = "Phonk & Drift Beats",
                    subtitle = "Bass-boosted high adrenaline",
                    songs = uiState.onlinePhonkSongs,
                    currentSongId = uiState.currentSong?.id,
                    isPlaying = uiState.isPlaying,
                    onSongClick = onPlaySong
                )
            }
        }

        // Global Hits
        val globalHits = uiState.onlineEnglishSongs.ifEmpty { uiState.onlineGlobalSongs }
        if (globalHits.isNotEmpty()) {
            item {
                TvSongCarousel(
                    title = "Global Hits & English Anthems",
                    subtitle = "Chart-toppers from around the world",
                    songs = globalHits,
                    currentSongId = uiState.currentSong?.id,
                    isPlaying = uiState.isPlaying,
                    onSongClick = onPlaySong
                )
            }
        }

        // Local Device Library
        if (uiState.songs.isNotEmpty()) {
            item {
                TvSongCarousel(
                    title = "Local Device Music (${uiState.songs.size})",
                    subtitle = "On-device MP3 audio files",
                    songs = uiState.songs,
                    currentSongId = uiState.currentSong?.id,
                    isPlaying = uiState.isPlaying,
                    onSongClick = onPlaySong
                )
            }
        }
    }
}

@Composable
private fun TvFeaturedHeroBanner(
    song: Song,
    onPlay: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(shape)
            .border(1.dp, Color(0x3300E5FF), shape)
            .background(Color(0xFF0F172A))
    ) {
        // Background Artwork with Dark Blur
        if (!song.albumArtUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Heavy Gradient overlay for readability on TV
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF0030712),
                            Color(0xCC0B1329),
                            Color(0x990F172A),
                            Color(0x40000000)
                        )
                    )
                )
        )

        // Banner Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00E5FF))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "FEATURED SPOTLIGHT",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${song.artist} • ${song.album}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                TvFocusableButton(
                    text = "Play Track Now",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    isPrimary = true,
                    onClick = onPlay
                )
            }

            // Thumbnail on right
            if (!song.albumArtUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0x6600E5FF), RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSongCarousel(
    title: String,
    subtitle: String,
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            items(songs, key = { "tv_song_${it.id}_${it.filePath}" }) { song ->
                TvFocusableSongCard(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}
