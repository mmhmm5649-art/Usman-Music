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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.SearchFilterCategory
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.OnlineArtistResultRow
import com.usman.musicplayer.ui.components.SearchFilterCategoryBar
import com.usman.musicplayer.ui.components.TvAlbumGridCard
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.SearchSource

/**
 * TV-Optimized Search Screen for Android TV / Google TV.
 * Features large accessible search field, soft keyboard support,
 * Online vs Device source toggle, All/Songs/Albums/Artists filters,
 * recent search history chips, and high-contrast result grid.
 */
@Composable
fun TvSearchScreen(
    uiState: MusicPlayerUiState,
    onSearchQueryChange: (String) -> Unit,
    onTriggerOnlineSearch: (String) -> Unit,
    onSearchSourceChange: (SearchSource) -> Unit,
    onPlaySong: (Song) -> Unit,
    onSearchFilterCategoryChange: (SearchFilterCategory) -> Unit = {},
    onOpenOnlineAlbum: (AlbumSearchResult) -> Unit = {},
    onOpenOnlineArtist: (OnlineArtistResult) -> Unit = {},
    onOpenLocalAlbum: (Album) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val defaultQuickQueries = listOf("Nazar Lagi", "Sai Abhyankkar", "Telugu Hits", "Asees Kaur", "Pushpa 2", "Arjan Vailly")
    val recentQueries = remember(uiState.searchHistory) {
        (uiState.searchHistory + defaultQuickQueries).distinctBy { it.lowercase() }.take(10)
    }
    val isOnlineMode = uiState.searchSource == SearchSource.ONLINE
    val filter = uiState.searchFilterCategory

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .padding(top = 20.dp, start = 24.dp, end = 24.dp)
    ) {
        // TV Header Title & Search Source Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SEARCH MUSIC",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Search Source Pills (Online Catalog vs Device Library)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvSourcePill(
                    label = "Online Catalog",
                    isSelected = isOnlineMode,
                    onClick = { onSearchSourceChange(SearchSource.ONLINE) }
                )
                TvSourcePill(
                    label = "Device Library",
                    isSelected = !isOnlineMode,
                    onClick = { onSearchSourceChange(SearchSource.DEVICE) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Large Accessible TV Search Input Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { q ->
                onSearchQueryChange(q)
                if (isOnlineMode && q.trim().length >= 2) {
                    onTriggerOnlineSearch(q.trim())
                }
            },
            placeholder = { Text("Search songs, movies, albums, or artists...", color = Color(0xFF64748B), fontSize = 16.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = SoftCyan) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSearchQueryChange("") }
                            .padding(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (isOnlineMode && uiState.searchQuery.isNotBlank()) {
                    onTriggerOnlineSearch(uiState.searchQuery.trim())
                }
                focusManager.clearFocus()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SoftCyan,
                unfocusedBorderColor = Color(0x441E2338),
                focusedContainerColor = Color(0x331E2338),
                unfocusedContainerColor = Color(0x181E2338),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Category Row (All / Songs / Albums / Artists)
        SearchFilterCategoryBar(
            selectedCategory = filter,
            onCategorySelected = onSearchFilterCategoryChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Recent Search Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(recentQueries) { query ->
                var isFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) SoftCyan else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isFocused) SoftCyan.copy(alpha = 0.3f) else Color(0x221E2338))
                        .clickable {
                            onSearchQueryChange(query)
                            if (isOnlineMode) {
                                onTriggerOnlineSearch(query)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = query, color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results Section
        val hasAnyOnlineResults = uiState.onlineSearchResults.isNotEmpty() ||
            uiState.onlineSearchAlbums.isNotEmpty() ||
            uiState.onlineSearchArtists.isNotEmpty()
        val hasAnyDeviceResults = uiState.deviceMatchingSongs.isNotEmpty() ||
            uiState.deviceMatchingAlbums.isNotEmpty() ||
            uiState.deviceMatchingArtists.isNotEmpty()

        when {
            isOnlineMode && uiState.isOnlineSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SoftCyan, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Searching online catalog...", color = Color(0xFFCBD5E1), fontSize = 16.sp)
                    }
                }
            }
            uiState.searchQuery.isNotBlank() && ((isOnlineMode && !hasAnyOnlineResults) || (!isOnlineMode && !hasAnyDeviceResults)) -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No matching results found for '${uiState.searchQuery}'",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isOnlineMode) {
                        val showAlbums = (filter == SearchFilterCategory.ALL || filter == SearchFilterCategory.ALBUMS) &&
                            uiState.onlineSearchAlbums.isNotEmpty()
                        val showArtists = (filter == SearchFilterCategory.ALL || filter == SearchFilterCategory.ARTISTS) &&
                            uiState.onlineSearchArtists.isNotEmpty()
                        val showSongs = (filter == SearchFilterCategory.ALL || filter == SearchFilterCategory.SONGS) &&
                            uiState.onlineSearchResults.isNotEmpty()

                        if (showAlbums) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "ALBUMS & MOVIE SOUNDTRACKS (${uiState.onlineSearchAlbums.size})",
                                    color = SoftCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            items(
                                items = if (filter == SearchFilterCategory.ALL) uiState.onlineSearchAlbums.take(6) else uiState.onlineSearchAlbums,
                                key = { "tv_album_${it.sourceProvider}_${it.id}" }
                            ) { album ->
                                TvAlbumGridCard(
                                    album = album,
                                    onClick = { onOpenOnlineAlbum(album) }
                                )
                            }
                        }

                        if (showArtists) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "ARTISTS (${uiState.onlineSearchArtists.size})",
                                    color = SoftCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            items(
                                items = if (filter == SearchFilterCategory.ALL) uiState.onlineSearchArtists.take(4) else uiState.onlineSearchArtists,
                                key = { "tv_artist_${it.id}_${it.name}" },
                                span = { GridItemSpan(maxLineSpan) }
                            ) { artist ->
                                OnlineArtistResultRow(
                                    artist = artist,
                                    onClick = { onOpenOnlineArtist(artist) }
                                )
                            }
                        }

                        if (showSongs) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "SONGS (${uiState.onlineSearchResults.size})",
                                    color = SoftCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            items(
                                items = uiState.onlineSearchResults,
                                key = { "tv_song_${it.id}_${it.filePath}" }
                            ) { song ->
                                TvSongCard(
                                    song = song,
                                    onClick = { onPlaySong(song) },
                                    cardWidth = 168.dp,
                                    cardHeight = 216.dp
                                )
                            }
                        }
                    } else {
                        val showAlbums = (filter == SearchFilterCategory.ALL || filter == SearchFilterCategory.ALBUMS) &&
                            uiState.deviceMatchingAlbums.isNotEmpty()
                        val showSongs = (filter == SearchFilterCategory.ALL || filter == SearchFilterCategory.SONGS) &&
                            uiState.deviceMatchingSongs.isNotEmpty()

                        if (showAlbums) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "DEVICE ALBUMS (${uiState.deviceMatchingAlbums.size})",
                                    color = SoftCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(
                                items = uiState.deviceMatchingAlbums,
                                key = { "tv_dev_album_${it.id}_${it.title}" }
                            ) { album ->
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

                        if (showSongs) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "DEVICE SONGS (${uiState.deviceMatchingSongs.size})",
                                    color = SoftCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(
                                items = uiState.deviceMatchingSongs,
                                key = { "tv_dev_song_${it.id}_${it.filePath}" }
                            ) { song ->
                                TvSongCard(
                                    song = song,
                                    onClick = { onPlaySong(song) },
                                    cardWidth = 168.dp,
                                    cardHeight = 216.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSourcePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
