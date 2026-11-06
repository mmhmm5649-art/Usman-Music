package com.usman.musicplayer.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.data.repository.ExternalServiceType
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.GlassBorderSubtle
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.SearchSource

@Composable
fun SearchScreen(
    uiState: MusicPlayerUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchSourceChange: (SearchSource) -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenService: (Context, Song, ExternalServiceType) -> Unit,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onFolderClick: (MusicFolder) -> Unit = {},
    onTriggerOnlineSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val query = uiState.searchQuery
    val isOnlineMode = uiState.searchSource == SearchSource.ONLINE

    // Filter matching device songs, artists, albums, and folders
    val matchingSongs = remember(query, uiState.songs) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            uiState.songs.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.folderName.lowercase().contains(q)
            }
        }
    }

    val matchingAlbums = remember(query, uiState.albums) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            uiState.albums.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    val matchingArtists = remember(query, uiState.artists) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            uiState.artists.filter { it.name.lowercase().contains(q) }
        }
    }

    val matchingFolders = remember(query, uiState.folders) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            uiState.folders.filter { it.name.lowercase().contains(q) || it.path.lowercase().contains(q) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 220.dp)
        ) {
            // Header Search Bar
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Discover Music",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Search online global catalog or your device library",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    // Liquid Glass Outlined Search Input Field
                    OutlinedTextField(
                        value = query,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x1A282C44))
                            .testTag("search_input_field"),
                        placeholder = {
                            Text(
                                text = if (isOnlineMode) "Search Telugu, Phonk, The Weeknd, BTS..." else "Search songs, artists, folders...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = SoftCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            if (isOnlineMode && query.isNotBlank()) {
                                onTriggerOnlineSearch(query)
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Toggle: Online Music vs Device Library
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x1A1F2438))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isOnlineMode) Color(0xFF7C3AED) else Color.Transparent)
                                .clickable { onSearchSourceChange(SearchSource.ONLINE) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = if (isOnlineMode) Color.White else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Online Catalog",
                                    color = if (isOnlineMode) Color.White else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isOnlineMode) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!isOnlineMode) Color(0xFF00ADB5) else Color.Transparent)
                                .clickable { onSearchSourceChange(SearchSource.DEVICE) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = if (!isOnlineMode) Color.White else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Device Library",
                                    color = if (!isOnlineMode) Color.White else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (!isOnlineMode) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Quick Category Shortcuts (when query is empty)
            if (query.isBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Explore Global Music",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // Curated Exploration Chips
                        val exploreChips = listOf(
                            "Telugu Hits", "Hindi Bollywood", "Brazilian Phonk",
                            "The Weeknd", "Taylor Swift", "Drift Phonk", "Tamil Hits",
                            "Punjabi Hits", "Billie Eilish", "Drake", "Kordhell",
                            "K-Pop Hits", "Anime Openings", "EDM Dance", "Romantic Songs"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            exploreChips.forEach { chip ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x221E2338))
                                        .clickable {
                                            onSearchSourceChange(SearchSource.ONLINE)
                                            onSearchQueryChange(chip)
                                            onTriggerOnlineSearch(chip)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = chip,
                                        color = SoftCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Trending preview recommendations
                if (uiState.onlineTrendingSongs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "🔥 Trending Worldwide",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }

                    items(uiState.onlineTrendingSongs.take(8), key = { "trend_search_${it.id}" }) { song ->
                        OnlineSongResultRow(
                            song = song,
                            isCurrent = uiState.currentSong?.id == song.id,
                            isPlaying = uiState.isPlaying && uiState.currentSong?.id == song.id,
                            onPlay = { onPlaySong(song) },
                            onOpenService = { service -> onOpenService(context, song, service) }
                        )
                    }
                }
            }

            // Results Section: ONLINE MODE
            if (isOnlineMode && query.isNotBlank()) {
                if (uiState.isOnlineSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = NeonPurple,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Searching online catalog...",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else if (uiState.onlineSearchError != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.onlineSearchError ?: "No songs found",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Online Search Results",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.onlineSearchResults.size} tracks",
                                color = SoftCyan,
                                fontSize = 13.sp
                            )
                        }
                    }

                    items(uiState.onlineSearchResults, key = { "online_res_${it.id}" }) { song ->
                        OnlineSongResultRow(
                            song = song,
                            isCurrent = uiState.currentSong?.id == song.id,
                            isPlaying = uiState.isPlaying && uiState.currentSong?.id == song.id,
                            onPlay = { onPlaySong(song) },
                            onOpenService = { service -> onOpenService(context, song, service) }
                        )
                    }
                }
            }

            // Results Section: DEVICE MODE
            if (!isOnlineMode && query.isNotBlank()) {
                val totalMatches = matchingSongs.size + matchingAlbums.size + matchingArtists.size + matchingFolders.size

                item {
                    Text(
                        text = "Found $totalMatches matches in device library",
                        color = SoftCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                if (matchingSongs.isNotEmpty()) {
                    item {
                        Text(
                            text = "Songs (${matchingSongs.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    items(matchingSongs, key = { "local_res_${it.id}_${it.filePath}" }) { song ->
                        DeviceSongResultRow(
                            song = song,
                            isCurrent = uiState.currentSong?.id == song.id,
                            isPlaying = uiState.isPlaying && uiState.currentSong?.id == song.id,
                            onPlay = { onPlaySong(song) }
                        )
                    }
                }

                if (matchingAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums (${matchingAlbums.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    items(matchingAlbums, key = { "album_res_${it.id}" }) { album ->
                        AlbumResultRow(album = album, onClick = { onAlbumClick(album) })
                    }
                }

                if (matchingArtists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Artists (${matchingArtists.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    items(matchingArtists, key = { "artist_res_${it.name}" }) { artist ->
                        ArtistResultRow(artist = artist, onClick = { onArtistClick(artist) })
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineSongResultRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpenService: (ExternalServiceType) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High-res album artwork
        Box(modifier = Modifier.size(54.dp)) {
            SongAlbumArtwork(
                song = song,
                cornerRadius = 12.dp,
                showGlowBorder = isCurrent
            )
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Playing",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) NeonCyan else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${song.artist} • ${song.album}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Playback Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x337C3AED))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (song.playbackType == PlaybackType.PREVIEW) "PREVIEW (30s)" else "FULL TRACK",
                        color = Color(0xFFD8B4FE),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!song.sourceProvider.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3338BDF8))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = song.sourceProvider.uppercase(),
                            color = Color(0xFFBAE6FD),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (!song.genre.isNullOrBlank()) {
                    Text(
                        text = song.genre,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Action Menu: Listen on YouTube / Spotify
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Official streaming options",
                    tint = SoftCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF1E2338))
            ) {
                DropdownMenuItem(
                    text = { Text("Play in USMAN (Stream)", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onPlay()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Open on Spotify", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onOpenService(ExternalServiceType.SPOTIFY)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Open on Apple Music", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onOpenService(ExternalServiceType.APPLE_MUSIC)
                    }
                )
            }
        }
    }
}

@Composable
fun DeviceSongResultRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            SongAlbumArtwork(
                song = song,
                cornerRadius = 10.dp,
                showGlowBorder = isCurrent
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) NeonCyan else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${song.formattedDuration}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AlbumResultRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Album,
            contentDescription = null,
            tint = NeonMagenta,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = album.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "${album.artist} • ${album.songCount} songs", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun ArtistResultRow(artist: Artist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = ElectricBlue,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = artist.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "${artist.songCount} songs • ${artist.albumCount} albums", color = TextSecondary, fontSize = 12.sp)
        }
    }
}
