package com.usman.musicplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.ArtistDetailsData
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.GlassBorderSubtle
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun ArtistDetailsScreen(
    artistSummary: OnlineArtistResult?,
    artistDetails: ArtistDetailsData?,
    isLoading: Boolean,
    error: String?,
    currentSong: Song?,
    isPlaying: Boolean,
    isTv: Boolean,
    onBack: () -> Unit,
    onRetry: (OnlineArtistResult) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onOpenAlbum: (AlbumSearchResult) -> Unit,
    onOpenPlaylist: (CuratedPlaylist) -> Unit,
    onSaveMixToLibrary: (String, List<Song>, (Boolean, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showAllPopularSongs by remember(artistSummary?.name, artistDetails?.id) { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val playAllFocusRequester = remember { FocusRequester() }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3000)
            feedbackMessage = null
        }
    }

    LaunchedEffect(isTv, artistDetails?.popularSongs?.isNotEmpty()) {
        if (isTv && artistDetails?.popularSongs?.isNotEmpty() == true) {
            runCatching { playAllFocusRequester.requestFocus() }
        }
    }

    val artistName = artistDetails?.name ?: artistSummary?.name ?: "Artist"
    val artworkUri = artistDetails?.artworkUri ?: artistSummary?.artworkUri
    val languages = artistDetails?.languages?.takeIf { it.isNotEmpty() } ?: artistSummary?.languages.orEmpty()
    val popularSongs = artistDetails?.popularSongs.orEmpty()
    val latestReleases = artistDetails?.latestReleases.orEmpty()
    val albums = artistDetails?.albums.orEmpty()
    val playlists = artistDetails?.playlists.orEmpty()
    val allPlayableSongs = artistDetails?.allPlayableSongs.orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .testTag("artist_details_screen")
    ) {
        // Atmospheric gradient header backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0E7490).copy(alpha = 0.34f),
                            NeonPurple.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("artist_details_lazy_column"),
            contentPadding = PaddingValues(
                start = if (isTv) 32.dp else 20.dp,
                end = if (isTv) 32.dp else 20.dp,
                top = 12.dp,
                bottom = 170.dp
            )
        ) {
            // Top bar with Back + Provider Pill
            item(key = "artist_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isBackFocused) SoftCyan.copy(alpha = 0.32f) else Color(0x281E2338))
                            .border(
                                width = if (isBackFocused) 2.dp else 1.dp,
                                color = if (isBackFocused) SoftCyan else GlassBorderSubtle,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(onClick = onBack)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("artist_details_back_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Back",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x3338BDF8))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "USMAN MUSIC",
                            color = Color(0xFFBAE6FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Hero Header (Circular Photo + Name + Languages + Real Discovered Counts)
            item(key = "artist_hero") {
                if (isTv) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtistCircularHeroPhoto(
                            artworkUri = artworkUri,
                            artistName = artistName,
                            size = 180
                        )
                        ArtistHeroMetadata(
                            name = artistName,
                            languages = languages,
                            subtitle = artistDetails?.subtitle ?: artistSummary?.subtitle,
                            songCount = allPlayableSongs.size,
                            albumCount = albums.size,
                            playlistCount = playlists.size,
                            centered = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ArtistCircularHeroPhoto(
                            artworkUri = artworkUri,
                            artistName = artistName,
                            size = 156
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ArtistHeroMetadata(
                            name = artistName,
                            languages = languages,
                            subtitle = artistDetails?.subtitle ?: artistSummary?.subtitle,
                            songCount = allPlayableSongs.size,
                            albumCount = albums.size,
                            playlistCount = playlists.size,
                            centered = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Buttons: Play All, Shuffle, Save Mix
            item(key = "artist_actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = if (isTv) Arrangement.spacedBy(14.dp) else Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtistActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Play All",
                        enabled = allPlayableSongs.isNotEmpty(),
                        isPrimary = true,
                        testTag = "artist_play_all_button",
                        modifier = Modifier.focusRequester(playAllFocusRequester),
                        onClick = { onPlayAll(allPlayableSongs) }
                    )
                    ArtistActionButton(
                        icon = Icons.Default.Shuffle,
                        label = "Shuffle",
                        enabled = allPlayableSongs.isNotEmpty(),
                        isPrimary = false,
                        testTag = "artist_shuffle_button",
                        onClick = { onShuffleAll(allPlayableSongs) }
                    )
                    ArtistActionButton(
                        icon = Icons.Default.BookmarkAdd,
                        label = "Save Mix",
                        enabled = allPlayableSongs.isNotEmpty(),
                        isPrimary = false,
                        testTag = "artist_save_mix_button",
                        onClick = {
                            onSaveMixToLibrary("$artistName Top Hits", allPlayableSongs) { _, msg ->
                                feedbackMessage = msg
                            }
                        }
                    )
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                item(key = "artist_feedback") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x3300E5FF))
                            .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = feedbackMessage.orEmpty(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            when {
                isLoading && allPlayableSongs.isEmpty() && albums.isEmpty() && playlists.isEmpty() -> {
                    item(key = "artist_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = NeonCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Loading $artistName...",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                error != null && allPlayableSongs.isEmpty() && albums.isEmpty() && playlists.isEmpty() -> {
                    item(key = "artist_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = error,
                                color = TextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            if (artistSummary != null) {
                                ArtistActionButton(
                                    icon = Icons.Default.Refresh,
                                    label = "Retry",
                                    enabled = true,
                                    isPrimary = false,
                                    testTag = "artist_retry_button",
                                    onClick = { onRetry(artistSummary) }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // 1. Popular Songs
                    if (popularSongs.isNotEmpty()) {
                        item(key = "header_popular_songs") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Popular Songs",
                                    color = TextPrimary,
                                    fontSize = if (isTv) 22.sp else 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (popularSongs.size > 8) {
                                    TextButton(
                                        onClick = { showAllPopularSongs = !showAllPopularSongs },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = if (showAllPopularSongs) "Show less" else "Show all (${popularSongs.size})",
                                            color = SoftCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        val displayedSongs = if (showAllPopularSongs) popularSongs else popularSongs.take(8)
                        itemsIndexed(
                            items = displayedSongs,
                            key = { idx, s -> "artist_pop_${idx}_${s.id}_${s.filePath}" }
                        ) { index, song ->
                            val isCurrent = currentSong?.id == song.id
                            ArtistSongRow(
                                index = index + 1,
                                song = song,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isTv = isTv,
                                onPlay = { onPlaySong(song, popularSongs) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onDownload = {
                                    onDownloadSong(song)
                                    feedbackMessage = "Downloading \"${song.title}\""
                                }
                            )
                        }
                    }

                    // 2. Latest Releases
                    if (latestReleases.isNotEmpty()) {
                        item(key = "header_latest_releases") {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Latest Releases",
                                color = TextPrimary,
                                fontSize = if (isTv) 22.sp else 19.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        item(key = "carousel_latest_releases") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(
                                    items = latestReleases,
                                    key = { "artist_lat_${it.id}_${it.filePath}" }
                                ) { song ->
                                    ArtistReleaseCard(
                                        song = song,
                                        isCurrent = currentSong?.id == song.id,
                                        isTv = isTv,
                                        onPlay = { onPlaySong(song, latestReleases) }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Albums
                    if (albums.isNotEmpty()) {
                        item(key = "header_artist_albums") {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Albums",
                                color = TextPrimary,
                                fontSize = if (isTv) 22.sp else 19.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        item(key = "carousel_artist_albums") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(albums, key = { "artist_alb_${it.id}" }) { album ->
                                    ArtistAlbumCard(
                                        album = album,
                                        isTv = isTv,
                                        onClick = { onOpenAlbum(album) }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Available Playlists
                    if (playlists.isNotEmpty()) {
                        item(key = "header_artist_playlists") {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Playlists",
                                color = TextPrimary,
                                fontSize = if (isTv) 22.sp else 19.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        item(key = "carousel_artist_playlists") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(playlists, key = { "artist_pl_${it.id}" }) { playlist ->
                                    ArtistPlaylistCard(
                                        playlist = playlist,
                                        isTv = isTv,
                                        onClick = { onOpenPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCircularHeroPhoto(
    artworkUri: String?,
    artistName: String,
    size: Int
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                )
            )
            .border(2.dp, SoftCyan.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = artistName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = artistName,
                tint = SoftCyan,
                modifier = Modifier.size((size / 2).dp)
            )
        }
    }
}

@Composable
private fun ArtistHeroMetadata(
    name: String,
    languages: List<String>,
    subtitle: String?,
    songCount: Int,
    albumCount: Int,
    playlistCount: Int,
    centered: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = name,
            color = TextPrimary,
            fontSize = if (centered) 26.sp else 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        val cleanSubtitle = subtitle
            ?.replace(Regex("(?i)\\bjiosaavn(\\s*artist)?\\b"), "")
            ?.trim(' ', '•', '-')
            ?.takeIf { it.isNotBlank() }
        val badgeParts = buildList {
            if (languages.isNotEmpty()) add(languages.joinToString(" • "))
            else if (!cleanSubtitle.isNullOrBlank()) add(cleanSubtitle)
            else add("Featured Artist")
        }
        Text(
            text = badgeParts.joinToString(" • "),
            color = SoftCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )

        val statsParts = buildList {
            if (songCount > 0) add("$songCount full tracks")
            if (albumCount > 0) add("$albumCount albums")
            if (playlistCount > 0) add("$playlistCount playlists")
        }
        if (statsParts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statsParts.joinToString(" • "),
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            )
        }
    }
}

@Composable
private fun ArtistActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isPrimary: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val bg = when {
        !enabled -> Color(0x1A1E2338)
        isFocused -> NeonCyan
        isPrimary -> NeonCyan
        else -> Color(0xFF1A2033)
    }
    val fg = when {
        !enabled -> TextMuted
        isFocused || isPrimary -> Color(0xFF040710)
        else -> TextPrimary
    }

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else GlassBorderSubtle,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
        Text(text = label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ArtistSongRow(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isTv: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit
) {
    var rowFocused by remember { mutableStateOf(false) }
    var favFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    rowFocused -> Color(0x441E293B)
                    isCurrent -> Color(0x2800E5FF)
                    else -> Color(0x1614182E)
                }
            )
            .border(
                width = if (rowFocused) 2.dp else 0.dp,
                color = if (rowFocused) SoftCyan else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .onFocusChanged { rowFocused = it.isFocused }
                .clickable(onClick = onPlay)
                .testTag("artist_song_row_${song.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    Icon(Icons.Default.Equalizer, contentDescription = "Playing", tint = NeonCyan, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        text = index.toString(),
                        color = if (isCurrent) NeonCyan else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            SongAlbumArtwork(
                song = song,
                modifier = Modifier.size(48.dp),
                cornerRadius = 10.dp,
                showGlowBorder = isCurrent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent) NeonCyan else TextPrimary,
                    fontSize = if (isTv) 15.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val albumInfo = song.album.takeIf { it.isNotBlank() && !it.equals("Unknown Album", true) } ?: song.artist
                Text(
                    text = "$albumInfo • ${song.formattedDuration}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(48.dp)
                .onFocusChanged { favFocused = it.isFocused }
                .clip(CircleShape)
                .background(if (favFocused) SoftCyan.copy(alpha = 0.25f) else Color.Transparent)
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Remove Favourite" else "Add Favourite",
                tint = if (song.isFavorite) NeonMagenta else TextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }

        if (!isTv) {
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${song.title}",
                    tint = SoftCyan,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistReleaseCard(
    song: Song,
    isCurrent: Boolean,
    isTv: Boolean,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val width = if (isTv) 168.dp else 146.dp
    Column(
        modifier = Modifier
            .width(width)
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.dp else 0.dp, if (isFocused) SoftCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x331E293B) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(if (isTv) 6.dp else 0.dp)
            .testTag("artist_latest_card_${song.id}")
    ) {
        Box(modifier = Modifier.size(if (isTv) 156.dp else 146.dp)) {
            SongAlbumArtwork(
                song = song,
                cornerRadius = 14.dp,
                showGlowBorder = isCurrent,
                modifier = Modifier.fillMaxSize()
            )
            if (!song.releaseYear.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(song.releaseYear, color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            color = if (isCurrent) NeonCyan else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.album,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistAlbumCard(
    album: AlbumSearchResult,
    isTv: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardSize = if (isTv) 160.dp else 150.dp
    Column(
        modifier = Modifier
            .width(cardSize)
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.dp else 0.dp, if (isFocused) SoftCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x331E293B) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(if (isTv) 6.dp else 0.dp)
            .testTag("artist_album_card_${album.id}")
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF181D2C))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!album.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = album.artworkUri,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Album, contentDescription = null, tint = SoftCyan, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.year ?: "Album",
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistPlaylistCard(
    playlist: CuratedPlaylist,
    isTv: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardSize = if (isTv) 164.dp else 152.dp
    Column(
        modifier = Modifier
            .width(cardSize)
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.dp else 0.dp, if (isFocused) SoftCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x331E293B) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(if (isTv) 6.dp else 0.dp)
            .testTag("artist_playlist_card_${playlist.id}")
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B)))),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.coverArtUri.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverArtUri,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(42.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NeonCyan),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF040710), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.description,
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
