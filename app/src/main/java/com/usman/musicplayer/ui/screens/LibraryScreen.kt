package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.GlassButtonSurface
import com.usman.musicplayer.ui.components.LiquidGlassPanel
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.components.UsmanHeader
import com.usman.musicplayer.ui.theme.ActivePillBrush
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

@Composable
fun LibraryScreen(
    uiState: MusicPlayerUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onPlaySong: (Song) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onFolderSelected: (MusicFolder) -> Unit,
    onNavigateToSearch: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onRescanLibrary: () -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine active filter banner
    val activeFilterTitle = when {
        uiState.selectedAlbumFilter != null -> "Album: ${uiState.selectedAlbumFilter}"
        uiState.selectedArtistFilter != null -> "Artist: ${uiState.selectedArtistFilter}"
        uiState.selectedFolderFilter != null -> "Folder: ${uiState.selectedFolderFilter}"
        else -> null
    }

    // Filter displayed songs if a specific album, artist or folder was clicked
    val displayedSongs = remember(
        uiState.songs,
        uiState.selectedAlbumFilter,
        uiState.selectedArtistFilter,
        uiState.selectedFolderFilter
    ) {
        when {
            uiState.selectedAlbumFilter != null -> uiState.songs.filter { it.album == uiState.selectedAlbumFilter }
            uiState.selectedArtistFilter != null -> uiState.songs.filter { it.artist == uiState.selectedArtistFilter }
            uiState.selectedFolderFilter != null -> uiState.songs.filter { it.folderName == uiState.selectedFolderFilter }
            else -> uiState.songs
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Neon ambient wave gradient across the top matching reference image #2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.28f),
                            ElectricBlue.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // USMAN Top Header
            UsmanHeader(
                onSearchClick = onNavigateToSearch,
                onMenuClick = onRescanLibrary
            )

            // "Your Library" title with glowing subtitle and rescan status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Your Library",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isScanning) "Scanning local storage..." else "Your music. Your vibe.",
                        color = if (uiState.isScanning) NeonCyan else TextSecondary,
                        fontSize = 14.sp
                    )
                }

                if (uiState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    GlassButtonSurface(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color(0x331E2338)
                    ) {
                        IconButton(
                            onClick = onRescanLibrary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Library",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Selector Tabs matching reference image 2:
            // [All Songs] [Albums] [Artists] [Folders]
            LibraryTabsRow(
                activeTab = uiState.activeLibraryTab,
                onTabSelected = {
                    onClearFilter()
                    onTabSelected(it)
                }
            )

            // Active Filter Chip (if an album/artist/folder was drilled down)
            if (activeFilterTitle != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassPanel(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color(0x401E2544)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeFilterTitle,
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear filter",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(onClick = onClearFilter)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState.activeLibraryTab) {
                    LibraryTab.ALL_SONGS -> {
                        AllSongsList(
                            songs = displayedSongs,
                            currentSong = uiState.currentSong,
                            isPlaying = uiState.isPlaying,
                            hasPermission = uiState.hasStoragePermission,
                            onSongClick = onPlaySong,
                            onRescan = onRescanLibrary,
                            onOpenFolderPicker = onOpenFolderPicker
                        )
                    }
                    LibraryTab.ALBUMS -> {
                        AlbumsGrid(
                            albums = uiState.albums,
                            onAlbumClick = onAlbumSelected
                        )
                    }
                    LibraryTab.ARTISTS -> {
                        ArtistsList(
                            artists = uiState.artists,
                            onArtistClick = onArtistSelected
                        )
                    }
                    LibraryTab.FOLDERS -> {
                        FoldersList(
                            folders = uiState.folders,
                            onFolderClick = onFolderSelected,
                            onAddSafFolder = onOpenFolderPicker
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabsRow(
    activeTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(LibraryTab.entries) { tab ->
            val isSelected = tab == activeTab
            val shape = RoundedCornerShape(22.dp)

            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(ActivePillBrush, shape)
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)),
                                    shape
                                )
                        } else {
                            Modifier
                                .background(Color(0x3314182E), shape)
                                .border(1.dp, GlassBorderSubtle, shape)
                        }
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("library_tab_${tab.name}"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (tab) {
                        LibraryTab.ALL_SONGS -> Icons.Default.MusicNote
                        LibraryTab.ALBUMS -> Icons.Default.Album
                        LibraryTab.ARTISTS -> Icons.Default.Person
                        LibraryTab.FOLDERS -> Icons.Default.Folder
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) Color.White else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AllSongsList(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    hasPermission: Boolean,
    onSongClick: (Song) -> Unit,
    onRescan: () -> Unit,
    onOpenFolderPicker: () -> Unit
) {
    if (songs.isEmpty()) {
        EmptyLibraryState(
            message = if (!hasPermission) {
                "Storage permission is required to discover audio files."
            } else {
                "No audio files found. Supported formats include MP3, AAC, M4A, FLAC, WAV, and OGG."
            },
            showRescan = hasPermission,
            onRescan = onRescan,
            onOpenFolderPicker = onOpenFolderPicker
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp)
        ) {
            items(songs, key = { "all_song_${it.id}_${it.filePath}" }) { song ->
                val isCurrent = currentSong?.id == song.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSongClick(song) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("song_item_${song.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongAlbumArtwork(
                        song = song,
                        modifier = Modifier.size(52.dp),
                        cornerRadius = 14.dp,
                        showGlowBorder = isCurrent
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = if (isCurrent) NeonCyan else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${song.artist} • ${song.album}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isCurrent && isPlaying) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Playing",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = song.formattedDuration,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyLibraryState(message = "No albums found in local storage.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums, key = { "album_${it.id}_${it.title}" }) { album ->
                LiquidGlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onAlbumClick(album) }
                        .testTag("album_card_${album.id}"),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x2614182E)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF101326)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = album.title,
                                tint = NeonPurple.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = album.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${album.artist} • ${album.songCount} songs",
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
private fun ArtistsList(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyLibraryState(message = "No artists found in local storage.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp)
        ) {
            items(artists, key = { "artist_${it.name}" }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onArtistClick(artist) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("artist_row_${artist.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0x331C2036))
                            .border(1.dp, NeonMagenta.copy(alpha = 0.5f), RoundedCornerShape(26.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = artist.name,
                            tint = NeonMagenta,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${artist.songCount} songs • ${artist.albumCount} albums",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersList(
    folders: List<MusicFolder>,
    onFolderClick: (MusicFolder) -> Unit,
    onAddSafFolder: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp)
    ) {
        // Storage Access Framework (SAF) folder browsing button
        item {
            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onAddSafFolder)
                    .testTag("add_saf_folder_btn"),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = Color(0x33142038),
                borderBrush = Brush.horizontalGradient(listOf(NeonCyan, ElectricBlue)),
                borderWidth = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Browse Folder",
                        tint = NeonCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Browse Device Folder (SAF)",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pick specific music folder or SD card directory",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (folders.isEmpty()) {
            item {
                EmptyLibraryState(message = "No music folders detected.")
            }
        } else {
            items(folders, key = { "folder_${it.path}_${it.name}" }) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onFolderClick(folder) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("folder_row_${folder.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x3314182E))
                            .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = folder.name,
                            tint = if (folder.isSafFolder) NeonCyan else ElectricBlue,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${folder.songCount} songs • ${folder.path}",
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
private fun EmptyLibraryState(
    message: String,
    showRescan: Boolean = false,
    onRescan: () -> Unit = {},
    onOpenFolderPicker: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SoftCyan,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRescan) {
                    Button(
                        onClick = onRescan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("empty_library_rescan_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Scan Again")
                    }
                }

                if (onOpenFolderPicker != null) {
                    Button(
                        onClick = onOpenFolderPicker,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("empty_library_saf_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Open Folder (SAF)")
                    }
                }
            }
        }
    }
}
