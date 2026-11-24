package com.usman.musicplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.AlbumDetails
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.TvAlbumGridCard
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

private enum class TvLibrarySection(val label: String, val testTag: String, val mappedTab: LibraryTab? = null) {
    ALL_SONGS("All Songs", "tv_lib_tab_all_songs", LibraryTab.ALL_SONGS),
    FAVORITES("Favorites", "tv_lib_tab_favorites"),
    RECENTLY_PLAYED("Recently Played", "tv_lib_tab_recent"),
    ALBUMS("Albums", "tv_lib_tab_albums", LibraryTab.ALBUMS),
    ARTISTS("Artists", "tv_lib_tab_artists", LibraryTab.ARTISTS),
    PLAYLISTS("Playlists", "tv_lib_tab_playlists", LibraryTab.PLAYLISTS),
    DOWNLOADS("Downloads", "tv_lib_tab_downloads", LibraryTab.DOWNLOADS)
}

/**
 * TV-Optimized Library Screen for Android TV / Google TV with complete phone feature parity:
 * All Songs, Favorites, Recently Played, Albums, Artists, Playlists, and Downloads.
 */
@Composable
fun TvLibraryScreen(
    uiState: MusicPlayerUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayQueue: (Song, List<Song>) -> Unit = { s, _ -> onPlaySong(s) },
    onOpenSavedAlbum: (AlbumDetails) -> Unit = {},
    onOpenLocalAlbum: (Album) -> Unit = {},
    onSelectPlaylist: (Playlist) -> Unit = {},
    onOpenSongOptions: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeSection by remember(uiState.activeLibraryTab) {
        mutableStateOf(
            when (uiState.activeLibraryTab) {
                LibraryTab.ALBUMS -> TvLibrarySection.ALBUMS
                LibraryTab.ARTISTS -> TvLibrarySection.ARTISTS
                LibraryTab.PLAYLISTS -> TvLibrarySection.PLAYLISTS
                LibraryTab.DOWNLOADS, LibraryTab.DOWNLOAD_CENTRE -> TvLibrarySection.DOWNLOADS
                else -> TvLibrarySection.ALL_SONGS
            }
        )
    }

    val favoriteSongs = uiState.favoriteSongs

    val groupedArtists = remember(uiState.songs, uiState.recentlyPlayed, uiState.favoriteSongs) {
        (uiState.songs + uiState.favoriteSongs + uiState.recentlyPlayed)
            .distinctBy { "${it.id}_${it.filePath}" }
            .groupBy { it.artist.trim().ifBlank { "Unknown Artist" } }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key to it.value }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .padding(top = 20.dp, start = 24.dp, end = 24.dp)
            .testTag("tv_library_screen")
    ) {
        Text(
            text = "MUSIC LIBRARY",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(TvLibrarySection.entries, key = { it.name }) { section ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = activeSection == section

                Box(
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) SoftCyan else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            when {
                                isFocused -> SoftCyan.copy(alpha = 0.35f)
                                isSelected -> SoftCyan.copy(alpha = 0.2f)
                                else -> Color(0x331E2338)
                            }
                        )
                        .clickable {
                            activeSection = section
                            section.mappedTab?.let(onTabSelected)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .testTag(section.testTag)
                ) {
                    Text(
                        text = section.label,
                        color = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (activeSection) {
            TvLibrarySection.ALBUMS -> {
                if (uiState.savedAlbums.isEmpty() && uiState.albums.isEmpty()) {
                    TvLibraryEmptyState("No saved or local albums available")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 176.dp),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.savedAlbums, key = { "tv_saved_album_${it.sourceProvider}_${it.id}" }) { saved ->
                            val summary = AlbumSearchResult(
                                id = saved.id,
                                title = saved.title,
                                movieName = saved.movieName,
                                artist = saved.artist,
                                year = saved.year,
                                songCount = saved.tracks.size.coerceAtLeast(saved.songCount),
                                artworkUri = saved.artworkUri,
                                sourceProvider = saved.sourceProvider,
                                externalUrl = saved.externalUrl,
                                language = saved.language,
                                isCompleteAlbum = saved.isComplete
                            )
                            TvAlbumGridCard(
                                album = summary,
                                onClick = { onOpenSavedAlbum(saved) }
                            )
                        }

                        items(uiState.albums, key = { "tv_local_album_${it.id}_${it.title}" }) { album ->
                            val summary = AlbumSearchResult(
                                id = album.id.toString(),
                                title = album.title,
                                artist = album.artist,
                                songCount = album.songCount,
                                artworkUri = album.artworkUri,
                                sourceProvider = "Device Library",
                                isCompleteAlbum = true
                            )
                            TvAlbumGridCard(
                                album = summary,
                                onClick = { onOpenLocalAlbum(album) }
                            )
                        }
                    }
                }
            }

            TvLibrarySection.ARTISTS -> {
                if (groupedArtists.isEmpty()) {
                    TvLibraryEmptyState("No artists in your library yet")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 188.dp),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(groupedArtists, key = { "tv_artist_${it.first}" }) { (artistName, artistSongs) ->
                            TvLibraryCollectionTile(
                                title = artistName,
                                subtitle = "${artistSongs.size} track${if (artistSongs.size == 1) "" else "s"}",
                                isArtist = true,
                                onClick = {
                                    artistSongs.firstOrNull()?.let { first ->
                                        onPlayQueue(first, artistSongs)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            TvLibrarySection.PLAYLISTS -> {
                if (uiState.playlists.isEmpty()) {
                    TvLibraryEmptyState("No playlists created yet")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.playlists, key = { "tv_playlist_${it.id}" }) { playlist: Playlist ->
                            TvLibraryCollectionTile(
                                title = playlist.name,
                                subtitle = "${playlist.songCount} song${if (playlist.songCount == 1) "" else "s"}",
                                isArtist = false,
                                onClick = {
                                    onSelectPlaylist(playlist)
                                }
                            )
                        }
                    }
                }
            }

            else -> {
                val songsToDisplay = when (activeSection) {
                    TvLibrarySection.ALL_SONGS -> uiState.songs
                    TvLibrarySection.FAVORITES -> favoriteSongs
                    TvLibrarySection.RECENTLY_PLAYED -> uiState.recentlyPlayed
                    TvLibrarySection.DOWNLOADS -> uiState.downloadedSongs
                    else -> uiState.songs
                }

                if (songsToDisplay.isEmpty()) {
                    TvLibraryEmptyState("No tracks available in ${activeSection.label}")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(songsToDisplay, key = { "${activeSection.name}_${it.id}_${it.filePath}" }) { song ->
                            TvSongCard(
                                song = song,
                                onClick = { onPlayQueue(song, songsToDisplay) },
                                cardWidth = 160.dp,
                                cardHeight = 214.dp,
                                onMoreOptions = onOpenSongOptions?.let { cb -> { cb(song) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraryEmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = SoftCyan.copy(alpha = 0.55f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = Color(0xFF94A3B8),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TvLibraryCollectionTile(
    title: String,
    subtitle: String,
    isArtist: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) Color(0xFF1F2A44) else Color(0xFF121828)
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) SoftCyan else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isArtist) Icons.Default.Person else Icons.Default.QueueMusic,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
