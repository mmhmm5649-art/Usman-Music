package com.usman.musicplayer.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.R
import com.usman.musicplayer.data.repository.ExternalServiceType
import com.usman.musicplayer.data.repository.MusicGenreCategory
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.RainbowAudioVisualizer
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.*
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MusicPlayerUiState,
    onNavigateToSearch: () -> Unit,
    onSearchCategory: (String) -> Unit,
    onNavigateToLibrary: (LibraryTab) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenService: (Context, Song, ExternalServiceType) -> Unit,
    onTabGenreSelected: (String) -> Unit,
    onSelectCategory: (MusicGenreCategory) -> Unit,
    onClearCategory: () -> Unit,
    onRescanClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
    onRetryTelugu: () -> Unit = {},
    onRetryJamendo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeCuratedSheet by remember { mutableStateOf<CuratedPlaylist?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Deep Atmospheric Ambient Lighting (Cinematic Glow)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.15f),
                            ElectricBlue.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 180.dp)
        ) {
            when (uiState.activeHomeGenreTab) {
                "New Releases" -> {
                    item { SectionHeader(title = "New Releases", subtitle = "Fresh tracks from the global catalog") }
                    items(uiState.onlineGlobalSongs.chunked(2)) { pair ->
                        Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { song ->
                                Column(modifier = Modifier.weight(1f).clickable { onPlaySong(song) }) {
                                    SongAlbumArtwork(song = song, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth().height(160.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(song.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 1. Premium Home Header
                    item {
                        HomeHeader(onProfileClick = onRescanClick)
                    }

                    // 2. Top Picks for You (Hero Carousel)
                    item {
                        TopPicksCarousel(
                            songs = uiState.onlineTrendingSongs,
                            onPlay = onPlaySong
                        )
                    }

                    // 3. Recently Played (Dynamic History)
                    if (uiState.recentlyPlayed.isNotEmpty()) {
                        item {
                            RecentlyPlayedSection(
                                songs = uiState.recentlyPlayed,
                                currentSong = uiState.currentSong,
                                onPlay = onPlaySong
                            )
                        }
                    }

                    // 4. Personalized Section: Playlists Made for You
                    item {
                        MadeForYouSection(
                            curatedPlaylists = uiState.curatedPlaylists,
                            userPlaylists = uiState.playlists,
                            onPlaylistClick = { playlist ->
                                activeCuratedSheet = playlist
                            },
                            onPlaySong = onPlaySong
                        )
                    }

                    // 5. Telugu Music (Fix infinite loading, add retry & playable tracks)
                    item {
                        SectionHeader(title = "Telugu Music", subtitle = "Latest Tollywood hits & classics")
                        MusicGenreCarousel(
                            songs = uiState.onlineTeluguSongs,
                            isLoading = uiState.isTeluguLoading,
                            errorMessage = uiState.onlineTeluguError,
                            onPlay = onPlaySong,
                            onRetry = onRetryTelugu
                        )
                    }

                    // 6. Hindi / Bollywood
                    item {
                        SectionHeader(title = "Hindi / Bollywood", subtitle = "Trending chartbusters from Mumbai")
                        MusicGenreCarousel(
                            songs = if (uiState.onlineBollywoodSongs.isNotEmpty()) uiState.onlineBollywoodSongs else uiState.onlineIndianSongs,
                            isLoading = uiState.isOnlineLoading && uiState.onlineBollywoodSongs.isEmpty(),
                            onPlay = onPlaySong
                        )
                    }

                    // 7. Phonk Station
                    item {
                        SectionHeader(title = "Phonk Station", subtitle = "Drift, Brazilian & Aggressive bass")
                        MusicGenreCarousel(
                            songs = uiState.onlinePhonkSongs,
                            isLoading = uiState.isOnlineLoading && uiState.onlinePhonkSongs.isEmpty(),
                            onPlay = onPlaySong
                        )
                    }

                    // 8. Jamendo Royalty-Free & Creative Commons Catalog
                    item {
                        SectionHeader(
                            title = "Jamendo CC Catalog",
                            subtitle = "Full-length royalty-free & Creative Commons independent music"
                        )
                        MusicGenreCarousel(
                            songs = uiState.onlineJamendoSongs,
                            isLoading = uiState.isJamendoLoading,
                            errorMessage = uiState.onlineJamendoError,
                            onPlay = onPlaySong,
                            onRetry = onRetryJamendo
                        )
                    }

                    // 9. English Hits
                    item {
                        SectionHeader(title = "English Hits", subtitle = "Global Pop, Hip-Hop & R&B")
                        MusicGenreCarousel(
                            songs = uiState.onlineEnglishSongs,
                            isLoading = uiState.isOnlineLoading && uiState.onlineEnglishSongs.isEmpty(),
                            onPlay = onPlaySong
                        )
                    }

                    // 10. Stations for You
                    item {
                        StationsSection(
                            teluguSongs = uiState.onlineTeluguSongs,
                            bollywoodSongs = uiState.onlineBollywoodSongs,
                            globalSongs = uiState.onlineTrendingSongs,
                            onPlay = onPlaySong
                        )
                    }

                    // 11. New Releases
                    item {
                        SectionHeader(title = "New Releases", subtitle = "Fresh tracks from the global catalog")
                        MusicGenreCarousel(
                            songs = uiState.onlineGlobalSongs.take(15),
                            isLoading = uiState.isOnlineLoading && uiState.onlineGlobalSongs.isEmpty(),
                            onPlay = onPlaySong
                        )
                    }
                }
            }

            // Visualizer Banner (Bottom of Feed)
            item {
                Spacer(modifier = Modifier.height(24.dp))
                HeroPlayerBanner(
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    onBannerClick = onNavigateToNowPlaying
                )
            }
        }

        // Curated Playlist Detail Bottom Sheet
        if (activeCuratedSheet != null) {
            CuratedPlaylistDetailSheet(
                playlist = activeCuratedSheet!!,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                onDismiss = { activeCuratedSheet = null },
                onPlaySong = { song ->
                    onPlaySong(song)
                }
            )
        }
    }
}

@Composable
private fun HomeHeader(onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Home",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Discover your next favorite track",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier
                .size(40.dp)
                .background(Color(0x1AFFFFFF), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profile",
                tint = SoftCyan,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TopPicksCarousel(songs: List<Song>, onPlay: (Song) -> Unit) {
    val topPicks = remember {
        listOf(
            Triple("Top Picks", "Curated for your vibe", R.drawable.ic_top_picks_1),
            Triple("Global Hits", "The world's most played", R.drawable.ic_global_hits),
            Triple("Phonk Vibes", "Ultimate night ride mix", R.drawable.ic_phonk_vibes)
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(topPicks) { pick ->
            TopPickCard(
                title = pick.first,
                subtitle = pick.second,
                imageRes = pick.third,
                onClick = {
                    if (songs.isNotEmpty()) onPlay(songs.random())
                }
            )
        }
    }
}

@Composable
private fun TopPickCard(
    title: String,
    subtitle: String,
    imageRes: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .shadow(12.dp, RoundedCornerShape(24.dp))
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 100f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RecentlyPlayedSection(
    songs: List<Song>,
    currentSong: Song?,
    onPlay: (Song) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        SectionHeader(title = "Recently Played", subtitle = "Pick up where you left off")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(songs.take(10)) { song ->
                val isCurrent = currentSong?.id == song.id
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .clickable { onPlay(song) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(110.dp)) {
                        SongAlbumArtwork(
                            song = song,
                            cornerRadius = 18.dp,
                            showGlowBorder = isCurrent
                        )
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Equalizer, null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = song.title,
                        color = if (isCurrent) NeonCyan else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MadeForYouSection(
    curatedPlaylists: List<CuratedPlaylist>,
    userPlaylists: List<com.usman.musicplayer.model.Playlist>,
    onPlaylistClick: (CuratedPlaylist) -> Unit,
    onPlaySong: (Song) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        SectionHeader(title = "Made For You", subtitle = "Personalized playlists and mixes")

        if (curatedPlaylists.isEmpty()) {
            LoadingRowPlaceholder()
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(curatedPlaylists) { playlist ->
                    Column(
                        modifier = Modifier
                            .width(160.dp)
                            .clickable { onPlaylistClick(playlist) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0x221E2544))
                                .shadow(8.dp, RoundedCornerShape(22.dp))
                        ) {
                            if (!playlist.coverArtUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = playlist.coverArtUri,
                                    contentDescription = playlist.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.QueueMusic, null, tint = NeonCyan, modifier = Modifier.size(48.dp))
                                }
                            }

                            // Glass Play Button Overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playlist.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = playlist.description,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicGenreCarousel(
    songs: List<Song>,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onPlay: (Song) -> Unit,
    onRetry: () -> Unit = {}
) {
    if (isLoading && songs.isEmpty()) {
        LoadingRowPlaceholder()
        return
    }

    if (errorMessage != null && songs.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(errorMessage, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    if (songs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text("No songs available in this section.", color = TextMuted, fontSize = 12.sp)
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(songs.take(15)) { song ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onPlay(song) }
            ) {
                Box(modifier = Modifier.size(140.dp)) {
                    SongAlbumArtwork(song = song, cornerRadius = 20.dp)
                    if (song.playbackType == PlaybackType.PREVIEW) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("30s", color = SoftCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    song.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StationsSection(
    teluguSongs: List<Song>,
    bollywoodSongs: List<Song>,
    globalSongs: List<Song>,
    onPlay: (Song) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        SectionHeader(title = "Stations for You", subtitle = "Radio mixes tailored to your taste")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val stations = listOf(
                Triple("Telugu Hits Station", R.drawable.ic_telugu_hits, teluguSongs),
                Triple("Bollywood Radio", R.drawable.ic_top_picks_1, bollywoodSongs),
                Triple("Global Discover Radio", R.drawable.ic_global_hits, globalSongs)
            )
            items(stations) { station ->
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .clickable {
                            if (station.third.isNotEmpty()) onPlay(station.third.random())
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = station.second),
                        contentDescription = station.first,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(station.first, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuratedPlaylistDetailSheet(
    playlist: CuratedPlaylist,
    currentSong: Song?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF10F1322),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x337C3AED))
                ) {
                    if (!playlist.coverArtUri.isNullOrBlank()) {
                        AsyncImage(
                            model = playlist.coverArtUri,
                            contentDescription = playlist.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.QueueMusic, null, tint = NeonCyan, modifier = Modifier.size(36.dp).align(Alignment.Center))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(playlist.description, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
                    Text("${playlist.songs.size} available tracks", color = SoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (playlist.songs.isNotEmpty()) onPlaySong(playlist.songs.first())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (playlist.songs.isNotEmpty()) onPlaySong(playlist.songs.random())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x331E2544), contentColor = NeonCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0x22FFFFFF))
            Spacer(modifier = Modifier.height(12.dp))

            if (playlist.songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No tracks in this playlist.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlist.songs) { song ->
                        val isCurrent = currentSong?.id == song.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPlaySong(song) }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongAlbumArtwork(song = song, modifier = Modifier.size(44.dp), cornerRadius = 12.dp)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = if (isCurrent) NeonCyan else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent && isPlaying) {
                                Icon(Icons.Default.Equalizer, "Playing", tint = NeonCyan, modifier = Modifier.size(18.dp))
                            } else {
                                Text(song.formattedDuration, color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun LoadingRowPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NeonPurple, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
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
            .padding(horizontal = 24.dp)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(28.dp), spotColor = Color(0x66000000))
            .clickable(onClick = onBannerClick)
            .testTag("hero_player_banner"),
        shape = RoundedCornerShape(28.dp),
        backgroundColor = Color(0x33171A2E),
        borderBrush = LiquidGlassBorderBrush
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(64.dp)
                ) {
                    SongAlbumArtwork(
                        song = currentSong,
                        cornerRadius = 16.dp,
                        showGlowBorder = isPlaying
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isPlaying) "NOW PLAYING" else "USMAN MUSIC",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = currentSong?.title ?: "Select a song or explore online",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = currentSong?.artist ?: "Global streaming & offline library",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) NeonCyan else NeonPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                        contentDescription = "Playback Status",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            RainbowAudioVisualizer(
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x26000000))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}
