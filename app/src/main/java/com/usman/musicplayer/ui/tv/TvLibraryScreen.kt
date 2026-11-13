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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.AlbumDetails
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.TvAlbumGridCard
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

/**
 * TV-Optimized Library Screen for Android TV / Google TV.
 * Offers tabs for All Songs, Albums, Artists, Playlists, Downloads,
 * with spacious grid layouts and clear D-pad focus outlines.
 */
@Composable
fun TvLibraryScreen(
    uiState: MusicPlayerUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenSavedAlbum: (AlbumDetails) -> Unit = {},
    onOpenLocalAlbum: (Album) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Pair(LibraryTab.ALL_SONGS, "All Songs"),
        Pair(LibraryTab.ALBUMS, "Albums"),
        Pair(LibraryTab.ARTISTS, "Artists"),
        Pair(LibraryTab.PLAYLISTS, "Playlists"),
        Pair(LibraryTab.DOWNLOADS, "Downloads")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .padding(top = 20.dp, start = 24.dp, end = 24.dp)
    ) {
        // TV Header Title
        Text(
            text = "MUSIC LIBRARY",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selector Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(tabs, key = { it.first.name }) { tabPair ->
                val tab = tabPair.first
                val label = tabPair.second
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = uiState.activeLibraryTab == tab

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
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Content Display
        if (uiState.activeLibraryTab == LibraryTab.ALBUMS) {
            if (uiState.savedAlbums.isEmpty() && uiState.albums.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No saved or local albums available",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
        } else {
            val songsToDisplay = when (uiState.activeLibraryTab) {
                LibraryTab.ALL_SONGS -> uiState.songs
                LibraryTab.DOWNLOADS -> uiState.downloadedSongs
                else -> uiState.songs
            }

            if (songsToDisplay.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No tracks available in this section",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(songsToDisplay, key = { "${it.id}_${it.filePath}" }) { song ->
                        TvSongCard(
                            song = song,
                            onClick = { onPlaySong(song) },
                            cardWidth = 160.dp,
                            cardHeight = 210.dp
                        )
                    }
                }
            }
        }
    }
}
