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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.AlbumDetails
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AlbumArtThumbnail
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.GlassBorderSubtle
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun AlbumDetailsScreen(
    albumSummary: AlbumSearchResult?,
    albumDetails: AlbumDetails?,
    isLoading: Boolean,
    error: String?,
    playlists: List<Playlist>,
    isSavedToLibrary: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    isTv: Boolean,
    onBack: () -> Unit,
    onRetry: (AlbumSearchResult) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onPlayTrack: (List<Song>, Int) -> Unit,
    onToggleSaveLibrary: (AlbumDetails, (Boolean, String) -> Unit) -> Unit,
    onAddAlbumToPlaylist: (Long, List<Song>, (Boolean, String) -> Unit) -> Unit,
    onCreatePlaylistFromAlbum: (String, List<Song>, (Boolean, String) -> Unit) -> Unit,
    onDownloadAlbum: (List<Song>, (String) -> Unit) -> Unit,
    onDownloadSong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3200)
            feedbackMessage = null
        }
    }

    val title = albumDetails?.title ?: albumSummary?.title ?: "Album"
    val movieName = albumDetails?.movieName ?: albumSummary?.movieName
    val artist = albumDetails?.artist ?: albumSummary?.artist ?: "Unknown Artist"
    val year = albumDetails?.year ?: albumSummary?.year
    val artworkUri = albumDetails?.artworkUri ?: albumSummary?.artworkUri
    val rawProvider = albumDetails?.sourceProvider ?: albumSummary?.sourceProvider ?: "JioSaavn"
    val sourceProvider = if (rawProvider.equals("iTunes", ignoreCase = true)) "JioSaavn" else rawProvider
    val language = albumDetails?.language ?: albumSummary?.language
    val tracks = remember(albumDetails?.tracks) {
        albumDetails?.tracks.orEmpty().filter {
            !it.sourceProvider.equals("iTunes", ignoreCase = true) &&
                (!it.isOnline || (it.playbackType == PlaybackType.FULL && (it.isStreamable || !it.contentUriString.isNullOrBlank())))
        }
    }
    val totalCount = albumDetails?.songCount ?: albumSummary?.songCount ?: tracks.size
    val isComplete = albumDetails?.isComplete ?: false
    val supportsDownload = remember(tracks) {
        tracks.any { it.playbackType == PlaybackType.FULL && (it.isStreamable || !it.isOnline) }
    }

    val effectiveDetails = remember(albumDetails, albumSummary, tracks) {
        albumDetails ?: albumSummary?.let {
            AlbumDetails(
                id = it.id,
                title = it.title,
                movieName = it.movieName,
                artist = it.artist,
                year = it.year,
                songCount = it.songCount ?: tracks.size,
                artworkUri = it.artworkUri,
                sourceProvider = it.sourceProvider,
                externalUrl = it.externalUrl,
                language = it.language,
                isComplete = false,
                tracks = tracks
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Subtle ambient top backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.25f),
                            SoftCyan.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = if (isTv) 32.dp else 20.dp,
                end = if (isTv) 32.dp else 20.dp,
                top = 12.dp,
                bottom = 160.dp
            )
        ) {
            // Top bar
            item {
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
                            .background(if (isBackFocused) SoftCyan.copy(alpha = 0.3f) else Color(0x261E2338))
                            .border(
                                width = if (isBackFocused) 2.dp else 1.dp,
                                color = if (isBackFocused) SoftCyan else GlassBorderSubtle,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(onClick = onBack)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("album_details_back_button"),
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
                            text = if (sourceProvider.equals("Device Library", ignoreCase = true)) "DEVICE LIBRARY" else "USMAN MUSIC",
                            color = Color(0xFFBAE6FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Album Header Card
            item {
                if (isTv) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(196.dp)) {
                            AlbumArtThumbnail(
                                artworkUri = artworkUri,
                                title = title,
                                artist = artist,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 20.dp,
                                showGlowBorder = true
                            )
                        }
                        AlbumHeaderMetadata(
                            title = title,
                            movieName = movieName,
                            artist = artist,
                            year = year,
                            language = language,
                            trackCount = tracks.size,
                            declaredCount = totalCount,
                            officialExpectedCount = albumDetails?.officialExpectedCount,
                            contributingAlbumsCount = albumDetails?.contributingAlbumsCount ?: 1,
                            isSoundtrackCollection = albumDetails?.isSoundtrackCollection ?: albumSummary?.isSoundtrackCollection ?: false,
                            isComplete = isComplete,
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
                        Box(modifier = Modifier.size(190.dp)) {
                            AlbumArtThumbnail(
                                artworkUri = artworkUri,
                                title = title,
                                artist = artist,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 22.dp,
                                showGlowBorder = true
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        AlbumHeaderMetadata(
                            title = title,
                            movieName = movieName,
                            artist = artist,
                            year = year,
                            language = language,
                            trackCount = tracks.size,
                            declaredCount = totalCount,
                            officialExpectedCount = albumDetails?.officialExpectedCount,
                            contributingAlbumsCount = albumDetails?.contributingAlbumsCount ?: 1,
                            isSoundtrackCollection = albumDetails?.isSoundtrackCollection ?: albumSummary?.isSoundtrackCollection ?: false,
                            isComplete = isComplete,
                            centered = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Controls Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Play All",
                        enabled = tracks.isNotEmpty(),
                        isPrimary = true,
                        testTag = "album_play_all_button",
                        onClick = { onPlayAll(tracks) }
                    )
                    AlbumActionButton(
                        icon = Icons.Default.Shuffle,
                        label = "Shuffle All",
                        enabled = tracks.isNotEmpty(),
                        isPrimary = false,
                        testTag = "album_shuffle_button",
                        onClick = { onShuffleAll(tracks) }
                    )
                    AlbumActionButton(
                        icon = if (isSavedToLibrary) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                        label = if (isSavedToLibrary) "In Library" else "Add to Library",
                        enabled = effectiveDetails != null,
                        isPrimary = false,
                        testTag = "album_save_library_button",
                        onClick = {
                            effectiveDetails?.let { details ->
                                onToggleSaveLibrary(details) { _, msg -> feedbackMessage = msg }
                            }
                        }
                    )
                    AlbumActionButton(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        label = "Add to Playlist",
                        enabled = tracks.isNotEmpty(),
                        isPrimary = false,
                        testTag = "album_add_playlist_button",
                        onClick = { showPlaylistDialog = true }
                    )
                    if (supportsDownload) {
                        AlbumActionButton(
                            icon = Icons.Default.Download,
                            label = "Download Album",
                            enabled = tracks.isNotEmpty(),
                            isPrimary = false,
                            testTag = "album_download_button",
                            onClick = { onDownloadAlbum(tracks) { msg -> feedbackMessage = msg } }
                        )
                    }
                }
            }

            // Action feedback banner
            if (!feedbackMessage.isNullOrBlank()) {
                item {
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

            // Tracklist Loading / Error / Songs
            when {
                isLoading && tracks.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = SoftCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Loading album tracklist...",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                error != null && tracks.isEmpty() -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = error,
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (albumSummary != null) {
                                AlbumActionButton(
                                    icon = Icons.Default.Refresh,
                                    label = "Retry",
                                    enabled = true,
                                    isPrimary = false,
                                    testTag = "album_retry_button",
                                    onClick = { onRetry(albumSummary) }
                                )
                            }
                        }
                    }
                }

                else -> {
                    val isCollection = albumDetails?.isSoundtrackCollection ?: albumSummary?.isSoundtrackCollection ?: false
                    item {
                        Text(
                            text = if (isCollection) "Soundtrack Songs (${tracks.size} discovered)" else "Tracks (${tracks.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    itemsIndexed(tracks, key = { idx, s -> "${idx}_${s.id}_${s.filePath}" }) { index, track ->
                        val isCurrent = currentSong?.id == track.id
                        AlbumTrackRow(
                            trackNumber = index + 1,
                            song = track,
                            showOriginalAlbum = isCollection,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            onPlay = { onPlayTrack(tracks, index) },
                            onDownload = {
                                onDownloadSong(track)
                                feedbackMessage = "Downloading \"${track.title}\""
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            containerColor = Color(0xF50F1322),
            title = {
                Text(
                    text = "Add Album to Playlist",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (playlists.isNotEmpty()) {
                        Text(
                            text = "Select existing playlist:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        playlists.forEach { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x221E2338))
                                    .clickable {
                                        showPlaylistDialog = false
                                        onAddAlbumToPlaylist(playlist.id, tracks) { _, msg ->
                                            feedbackMessage = msg
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = playlist.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${playlist.songCount} songs",
                                    color = SoftCyan,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "Or create new playlist from album:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text(title, color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPlaylistName.ifBlank { title }
                        showPlaylistDialog = false
                        newPlaylistName = ""
                        onCreatePlaylistFromAlbum(name, tracks) { _, msg ->
                            feedbackMessage = msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White)
                ) {
                    Text("Create Playlist")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun AlbumHeaderMetadata(
    title: String,
    movieName: String?,
    artist: String,
    year: String?,
    language: String?,
    trackCount: Int,
    declaredCount: Int,
    officialExpectedCount: Int?,
    contributingAlbumsCount: Int,
    isSoundtrackCollection: Boolean,
    isComplete: Boolean,
    centered: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!movieName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x3300E5FF))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                val movieText = if (movieName.equals(title, ignoreCase = true)) {
                    "Movie Soundtrack: $movieName"
                } else {
                    "Movie: $movieName"
                }
                Text(
                    text = movieText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist,
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        val officialConfirmedComplete = isComplete && officialExpectedCount != null && trackCount >= officialExpectedCount
        val metaChips = buildList {
            if (!year.isNullOrBlank()) add(year)
            if (!language.isNullOrBlank()) add(language.replaceFirstChar { it.uppercase() })
            if (trackCount > 0) {
                when {
                    officialConfirmedComplete -> {
                        val label = if (isSoundtrackCollection) "Complete Soundtrack" else "Complete Album"
                        add("$trackCount songs • $label")
                    }
                    officialExpectedCount != null && officialExpectedCount > trackCount -> {
                        add("$trackCount of $officialExpectedCount songs discovered • Partial")
                    }
                    else -> {
                        add("$trackCount songs discovered")
                    }
                }
            } else if (declaredCount > 0) {
                add("$declaredCount songs")
            }
            if (contributingAlbumsCount > 1) {
                add("$contributingAlbumsCount releases combined")
            }
        }

        if (metaChips.isNotEmpty()) {
            Text(
                text = metaChips.joinToString(" • "),
                color = if (officialConfirmedComplete) SoftCyan else Color(0xFFFBBF24),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AlbumActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isPrimary: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    val bg = when {
        !enabled -> Color(0x1A1E2338)
        isFocused && isPrimary -> NeonCyan
        isFocused -> SoftCyan.copy(alpha = 0.35f)
        isPrimary -> NeonPurple
        else -> Color(0x331E2338)
    }

    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(bg, shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) SoftCyan else GlassBorderSubtle,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (!enabled) TextMuted else Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (!enabled) TextMuted else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AlbumTrackRow(
    trackNumber: Int,
    song: Song,
    showOriginalAlbum: Boolean = false,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val isPlayable = !song.isOnline || song.playbackType != PlaybackType.EXTERNAL_SERVICE || !song.contentUriString.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 3.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    isFocused -> Color(0x441E2544)
                    isCurrent -> Color(0x3300E5FF)
                    else -> Color(0x1814182E)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SoftCyan else Color.Transparent,
                shape = shape
            )
            .clickable(enabled = isPlayable, onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("album_track_row_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Playing",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = trackNumber.toString(),
                    color = if (isCurrent) NeonCyan else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = when {
                    !isPlayable -> TextMuted
                    isCurrent -> NeonCyan
                    else -> TextPrimary
                },
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = if (showOriginalAlbum && song.album.isNotBlank() && !song.album.equals("Unknown Album", true)) {
                "${song.artist} • ${song.album}"
            } else {
                song.artist
            }
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 3.dp)
            ) {
                val (badgeLabel, badgeColor, badgeBg) = if (!isPlayable) {
                    Triple("UNAVAILABLE", Color(0xFFFCA5A5), Color(0x33EF4444))
                } else {
                    Triple("FULL TRACK", Color(0xFFD8B4FE), Color(0x337C3AED))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(badgeBg)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (song.durationMs > 0L) {
            Text(
                text = song.formattedDuration,
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        if (song.isOnline && song.playbackType == PlaybackType.FULL && song.isStreamable) {
            var isDlFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onFocusChanged { isDlFocused = it.isFocused }
                    .clip(CircleShape)
                    .background(if (isDlFocused) SoftCyan.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable(onClick = onDownload),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${song.title}",
                    tint = if (isDlFocused) SoftCyan else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
