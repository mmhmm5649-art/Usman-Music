package com.usman.musicplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.data.AppImageLoader
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.screens.buildGroupedArtistsForSeeAll
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextSecondary

@Composable
fun TvHomeCollectionOverlay(
    playlist: CuratedPlaylist,
    isLoading: Boolean,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onSaveToLibrary: (String, List<Song>, (Boolean, String) -> Unit) -> Unit
) {
    BackHandler(onBack = onBack)
    val playAllFocus = remember { FocusRequester() }
    var feedback by remember(playlist.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(playlist.id) {
        runCatching { playAllFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TvFocusButton(
                label = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                testTag = "tv_collection_back"
            )
            Text(
                text = playlist.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TvFocusButton(
                label = "Play All",
                icon = Icons.Default.PlayArrow,
                onClick = { if (playlist.songs.isNotEmpty()) onPlaySong(playlist.songs.first(), playlist.songs) },
                isPrimary = true,
                modifier = Modifier.focusRequester(playAllFocus),
                testTag = "tv_collection_play_all"
            )
            TvFocusButton(
                label = "Shuffle",
                icon = Icons.Default.Shuffle,
                onClick = { onShuffleAll(playlist.songs) },
                testTag = "tv_collection_shuffle"
            )
            TvFocusButton(
                label = "Save",
                icon = Icons.Default.BookmarkAdd,
                onClick = {
                    onSaveToLibrary(playlist.title, playlist.songs) { _, msg -> feedback = msg }
                },
                testTag = "tv_collection_save"
            )
        }

        if (!feedback.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(feedback!!, color = SoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${playlist.description} • ${playlist.songs.size} tracks",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SoftCyan)
                }
            }
            playlist.songs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tracks in this collection yet.", color = TextSecondary, fontSize = 16.sp)
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(playlist.songs, key = { "tv_col_${it.id}_${it.filePath}" }) { song ->
                        TvSongListRow(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            onPlay = { onPlaySong(song, playlist.songs) },
                            onToggleFavorite = { onToggleFavorite(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvHomeSeeAllOverlay(
    section: HomeDiscoverySection,
    currentSong: Song?,
    onBack: () -> Unit,
    onLoadMore: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (AlbumSearchResult) -> Unit,
    onOpenArtist: (OnlineArtistResult) -> Unit,
    onOpenPlaylist: (CuratedPlaylist) -> Unit
) {
    BackHandler(onBack = onBack)
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(section.id) {
        runCatching { initialFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TvFocusButton(
                label = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                modifier = Modifier.focusRequester(initialFocus),
                testTag = "tv_see_all_back"
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(section.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(section.subtitle, color = TextSecondary, fontSize = 13.sp)
            }
            if (section.contentType == HomeSectionContentType.SONGS && section.songs.isNotEmpty()) {
                TvFocusButton(
                    label = "Play All",
                    icon = Icons.Default.PlayArrow,
                    isPrimary = true,
                    onClick = { onPlaySong(section.songs.first(), section.songs) }
                )
                TvFocusButton(
                    label = "Shuffle",
                    icon = Icons.Default.Shuffle,
                    onClick = { onShuffleAll(section.songs) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            when (section.contentType) {
                HomeSectionContentType.SONGS -> {
                    items(section.songs, key = { "tv_all_${section.id}_${it.id}_${it.filePath}" }) { song ->
                        TvSongListRow(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            onPlay = { onPlaySong(song, section.songs) },
                            onToggleFavorite = { onToggleFavorite(song) }
                        )
                    }
                }
                HomeSectionContentType.ALBUMS -> {
                    items(section.albums.chunked(4)) { rowAlbums ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            rowAlbums.forEach { album ->
                                TvHomeAlbumCard(
                                    album = album,
                                    onClick = {
                                        onBack()
                                        onOpenAlbum(album)
                                    }
                                )
                            }
                        }
                    }
                }
                HomeSectionContentType.ARTISTS -> {
                    if (section.id == "discovery_popular_artists") {
                        val groups = buildGroupedArtistsForSeeAll(
                            artists = section.artists,
                            filter = HomeLanguageFilter.ALL
                        )
                        groups.forEach { group ->
                            item(key = "tv_artist_hdr_${group.key}") {
                                Text(
                                    text = group.title,
                                    color = SoftCyan,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 4.dp)
                                        .testTag("tv_see_all_artist_group_${group.key}")
                                )
                            }
                            items(group.artists.chunked(5)) { rowArtists ->
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    rowArtists.forEach { artist ->
                                        TvHomeArtistCard(
                                            artist = artist,
                                            testTag = "tv_see_all_artist_${group.key}_${artist.id}",
                                            onClick = {
                                                onBack()
                                                onOpenArtist(artist)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(section.artists.chunked(5)) { rowArtists ->
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                rowArtists.forEach { artist ->
                                    TvHomeArtistCard(
                                        artist = artist,
                                        testTag = "tv_see_all_artist_${artist.id}",
                                        onClick = {
                                            onBack()
                                            onOpenArtist(artist)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                HomeSectionContentType.PLAYLISTS -> {
                    items(section.playlists.chunked(4)) { rowPlaylists ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            rowPlaylists.forEach { playlist ->
                                TvHomePlaylistCard(
                                    playlist = playlist,
                                    onClick = {
                                        onBack()
                                        onOpenPlaylist(playlist)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (section.canLoadMore) {
                item {
                    Box(modifier = Modifier.padding(top = 12.dp)) {
                        TvFocusButton(
                            label = if (section.isLoading) "Loading..." else "Load More",
                            icon = Icons.Default.PlayArrow,
                            onClick = { if (!section.isLoading) onLoadMore(section.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvFocusButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    testTag: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvBtnScale"
    )
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isFocused -> NeonCyan
                isPrimary -> SoftCyan.copy(alpha = 0.25f)
                else -> Color(0x331E2538)
            },
            contentColor = if (isFocused) Color(0xFF040710) else Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x28FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TvHomeAlbumCard(
    album: AlbumSearchResult,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvAlbumScale"
    )
    Column(
        modifier = Modifier
            .width(172.dp)
            .height(220.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.5.dp else 0.dp, if (isFocused) SoftCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x441E2338) else Color(0x181E2338))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF151B2E))
        ) {
            val albumArt = album.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
            if (albumArt != null) {
                val context = LocalContext.current
                val imageLoader = remember(context) { AppImageLoader.get(context) }
                val req = remember(context, albumArt) {
                    AppImageLoader.buildImageRequest(
                        context,
                        albumArt,
                        AppImageLoader.SIZE_CARD_THUMB_PX,
                        AppImageLoader.SIZE_CARD_THUMB_PX
                    )
                }
                AsyncImage(
                    model = req,
                    imageLoader = imageLoader,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(album.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, color = Color(0xFFCBD5E1), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TvHomeArtistCard(
    artist: OnlineArtistResult,
    onClick: () -> Unit,
    testTag: String = "tv_artist_card_${artist.id}"
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvArtistScale"
    )
    val subtitleText = remember(artist.languages, artist.subtitle) {
        artist.languages.takeIf { it.isNotEmpty() }?.joinToString(" • ")
            ?: artist.subtitle?.takeIf { it.isNotBlank() }
            ?: "Artist"
    }
    Column(
        modifier = Modifier
            .width(142.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.5.dp else 0.dp, if (isFocused) SoftCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x441E2338) else Color(0x141E2338))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(Color(0xFF182034))
        ) {
            val artistArt = artist.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
            if (artistArt != null) {
                val context = LocalContext.current
                val imageLoader = remember(context) { AppImageLoader.get(context) }
                val req = remember(context, artistArt) {
                    AppImageLoader.buildImageRequest(context, artistArt, 260, 260)
                }
                AsyncImage(
                    model = req,
                    imageLoader = imageLoader,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(artist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitleText, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TvHomePlaylistCard(
    playlist: CuratedPlaylist,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvPlaylistScale"
    )
    Column(
        modifier = Modifier
            .width(176.dp)
            .height(222.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.5.dp else 0.dp, if (isFocused) SoftCyan else Color.Transparent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x441E2338) else Color(0x181E2338))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B)))),
            contentAlignment = Alignment.Center
        ) {
            val playlistArt = playlist.coverArtUri?.trim()?.takeIf { it.isNotEmpty() }
            if (playlistArt != null) {
                val context = LocalContext.current
                val imageLoader = remember(context) { AppImageLoader.get(context) }
                val req = remember(context, playlistArt) {
                    AppImageLoader.buildImageRequest(
                        context,
                        playlistArt,
                        AppImageLoader.SIZE_CARD_THUMB_PX,
                        AppImageLoader.SIZE_CARD_THUMB_PX
                    )
                }
                AsyncImage(
                    model = req,
                    imageLoader = imageLoader,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.QueueMusic, null, tint = SoftCyan, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(playlist.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(playlist.description, color = Color(0xFFCBD5E1), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvSongListRow(
    song: Song,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var rowFocused by remember { mutableStateOf(false) }
    var favFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    rowFocused -> Color(0x441E293B)
                    isCurrent -> Color(0x2800E5FF)
                    else -> Color(0x181E2338)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .onFocusChanged { rowFocused = it.isFocused }
                .border(if (rowFocused) 2.dp else 0.dp, if (rowFocused) SoftCyan else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable(onClick = onPlay)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongAlbumArtwork(song = song, modifier = Modifier.size(52.dp), cornerRadius = 10.dp, showGlowBorder = isCurrent)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent) NeonCyan else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.album} • ${song.formattedDuration}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .onFocusChanged { favFocused = it.isFocused }
                .border(if (favFocused) 2.dp else 0.dp, if (favFocused) SoftCyan else Color.Transparent, CircleShape)
                .background(if (favFocused) SoftCyan.copy(alpha = 0.28f) else Color(0x22FFFFFF))
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favourite",
                tint = if (song.isFavorite) NeonMagenta else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
