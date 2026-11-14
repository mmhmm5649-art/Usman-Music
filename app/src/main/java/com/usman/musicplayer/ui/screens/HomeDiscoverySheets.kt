package com.usman.musicplayer.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.data.AppImageLoader
import com.usman.musicplayer.data.repository.HomeDiscoveryCatalog
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.ArtistMatchingPolicy
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeNotificationItem
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

private val JIOSAAVN_ARTIST_REGEX = Regex("(?i)\\bjiosaavn(\\s*artist)?\\b")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCollectionDetailSheet(
    playlist: CuratedPlaylist,
    isLoading: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onSaveToLibrary: (String, List<Song>, (Boolean, String) -> Unit) -> Unit
) {
    var feedbackMessage by remember(playlist.id) { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF50E1220),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x337C3AED)),
                    contentAlignment = Alignment.Center
                ) {
                    val coverUri = playlist.coverArtUri?.trim()?.takeIf { it.isNotEmpty() }
                    if (coverUri != null) {
                        val context = LocalContext.current
                        val imageLoader = remember(context) { AppImageLoader.get(context) }
                        val coverReq = remember(context, coverUri) {
                            AppImageLoader.buildImageRequest(context, coverUri, 200, 200)
                        }
                        AsyncImage(
                            model = coverReq,
                            imageLoader = imageLoader,
                            contentDescription = playlist.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.QueueMusic, null, tint = NeonCyan, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.songs.size} full tracks",
                        color = SoftCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (playlist.songs.isNotEmpty()) {
                            onPlaySong(playlist.songs.first(), playlist.songs)
                        }
                    },
                    enabled = playlist.songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF040710)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("collection_play_all_button")
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }

                Button(
                    onClick = { onShuffleAll(playlist.songs) },
                    enabled = playlist.songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C2338),
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("collection_shuffle_button")
                ) {
                    Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = {
                        onSaveToLibrary(playlist.title, playlist.songs) { _, msg ->
                            feedbackMessage = msg
                        }
                    },
                    enabled = playlist.songs.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1C2338))
                        .testTag("collection_save_library_button")
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Save to Library", tint = SoftCyan)
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = feedbackMessage!!,
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0x22FFFFFF))
            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                }
                playlist.songs.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tracks yet. Play or favourite songs to see them here.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlist.songs, key = { "${playlist.id}_${it.id}_${it.filePath}" }) { song ->
                            SheetTrackRow(
                                song = song,
                                isCurrent = currentSong?.id == song.id,
                                isPlaying = isPlaying,
                                onPlay = { onPlaySong(song, playlist.songs) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onDownload = { onDownloadSong(song) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSeeAllSheet(
    section: HomeDiscoverySection,
    currentSong: Song?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onLoadMore: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onOpenAlbum: (AlbumSearchResult) -> Unit,
    onOpenArtist: (OnlineArtistResult) -> Unit,
    onOpenPlaylist: (CuratedPlaylist) -> Unit,
    onSaveSectionAsPlaylist: (String, List<Song>, (Boolean, String) -> Unit) -> Unit
) {
    var savedMsg by remember(section.id) { mutableStateOf<String?>(null) }
    var selectedArtistLanguage by remember(section.id) { mutableStateOf(HomeLanguageFilter.ALL) }
    val isPopularArtistsMultiLanguage = section.contentType == HomeSectionContentType.ARTISTS &&
        section.id == "discovery_popular_artists"

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF50E1220),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = section.title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = section.subtitle,
                color = TextSecondary,
                fontSize = 12.sp
            )

            if (isPopularArtistsMultiLanguage) {
                Spacer(modifier = Modifier.height(10.dp))
                val artistLangFilters = remember {
                    listOf(
                        HomeLanguageFilter.ALL,
                        HomeLanguageFilter.TELUGU,
                        HomeLanguageFilter.TAMIL,
                        HomeLanguageFilter.HINDI,
                        HomeLanguageFilter.ENGLISH
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(artistLangFilters, key = { it.name }) { filter ->
                        val selected = selectedArtistLanguage == filter
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) NeonCyan else Color(0xFF181D2C))
                                .border(
                                    width = 1.dp,
                                    color = if (selected) NeonCyan else Color(0x28FFFFFF),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { selectedArtistLanguage = filter }
                                .padding(horizontal = 14.dp)
                                .testTag("see_all_artist_lang_${filter.chipLabel.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.chipLabel,
                                color = if (selected) Color(0xFF040710) else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (section.contentType == HomeSectionContentType.SONGS && section.songs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlaySong(section.songs.first(), section.songs) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF040710)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onShuffleAll(section.songs) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2338), contentColor = TextPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    IconButton(
                        onClick = {
                            onSaveSectionAsPlaylist(section.title, section.songs) { _, msg -> savedMsg = msg }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1C2338))
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Save to Library", tint = SoftCyan)
                    }
                }
                if (!savedMsg.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(savedMsg!!, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x22FFFFFF))
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .testTag("see_all_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (section.contentType) {
                    HomeSectionContentType.SONGS -> {
                        items(section.songs, key = { "all_${section.id}_${it.id}_${it.filePath}" }) { song ->
                            SheetTrackRow(
                                song = song,
                                isCurrent = currentSong?.id == song.id,
                                isPlaying = isPlaying,
                                onPlay = { onPlaySong(song, section.songs) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onDownload = { onDownloadSong(song) }
                            )
                        }
                    }

                    HomeSectionContentType.ALBUMS -> {
                        items(section.albums, key = { "all_${section.id}_${it.id}" }) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onDismiss()
                                        onOpenAlbum(album)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF181D2C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val albumArt = album.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
                                    if (albumArt != null) {
                                        val context = LocalContext.current
                                        val imageLoader = remember(context) { AppImageLoader.get(context) }
                                        val albumReq = remember(context, albumArt) {
                                            AppImageLoader.buildImageRequest(
                                                context,
                                                albumArt,
                                                AppImageLoader.SIZE_MICRO_THUMB_PX,
                                                AppImageLoader.SIZE_MICRO_THUMB_PX
                                            )
                                        }
                                        AsyncImage(
                                            model = albumReq,
                                            imageLoader = imageLoader,
                                            contentDescription = album.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Album, null, tint = SoftCyan)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(album.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${album.artist}${album.year?.let { " • $it" }.orEmpty()}", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    HomeSectionContentType.ARTISTS -> {
                        if (isPopularArtistsMultiLanguage) {
                            val groupedArtists = buildGroupedArtistsForSeeAll(
                                artists = section.artists,
                                filter = selectedArtistLanguage
                            )
                            groupedArtists.forEach { (groupKey, groupTitle, groupList) ->
                                item(key = "header_${section.id}_$groupKey") {
                                    Text(
                                        text = groupTitle,
                                        color = SoftCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .padding(top = 8.dp, bottom = 2.dp)
                                            .testTag("see_all_artist_group_$groupKey")
                                    )
                                }
                                items(
                                    items = groupList,
                                    key = { "all_${section.id}_${groupKey}_${it.id}_${it.name}" }
                                ) { artist ->
                                    SheetArtistRow(
                                        artist = artist,
                                        testTag = "home_see_all_artist_${groupKey}_${artist.id}",
                                        onClick = {
                                            onOpenArtist(artist)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        } else {
                            items(section.artists, key = { "all_${section.id}_${it.id}_${it.name}" }) { artist ->
                                SheetArtistRow(
                                    artist = artist,
                                    testTag = "home_see_all_artist_${artist.id}",
                                    onClick = {
                                        onDismiss()
                                        onOpenArtist(artist)
                                    }
                                )
                            }
                        }
                    }

                    HomeSectionContentType.PLAYLISTS -> {
                        items(section.playlists, key = { "all_${section.id}_${it.id}" }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onDismiss()
                                        onOpenPlaylist(playlist)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x337C3AED)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val playlistArt = playlist.coverArtUri?.trim()?.takeIf { it.isNotEmpty() }
                                    if (playlistArt != null) {
                                        val context = LocalContext.current
                                        val imageLoader = remember(context) { AppImageLoader.get(context) }
                                        val playlistReq = remember(context, playlistArt) {
                                            AppImageLoader.buildImageRequest(
                                                context,
                                                playlistArt,
                                                AppImageLoader.SIZE_MICRO_THUMB_PX,
                                                AppImageLoader.SIZE_MICRO_THUMB_PX
                                            )
                                        }
                                        AsyncImage(
                                            model = playlistReq,
                                            imageLoader = imageLoader,
                                            contentDescription = playlist.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.QueueMusic, null, tint = NeonCyan)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(playlist.description, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                if (section.canLoadMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = { onLoadMore(section.id) },
                                enabled = !section.isLoading,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                if (section.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonCyan)
                                } else {
                                    Text("Load More Tracks", color = NeonCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNotificationsSheet(
    notifications: List<HomeNotificationItem>,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenAlbum: (AlbumSearchResult) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF50E1220),
        scrimColor = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = NeonCyan)
                Text(
                    text = "What's New",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Latest verified drops across Telugu, Tamil, English & Hindi",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You're all caught up. Fresh music alerts appear here.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF161B2A))
                                .clickable {
                                    when {
                                        item.song != null -> {
                                            onDismiss()
                                            onPlaySong(item.song)
                                        }
                                        item.album != null -> {
                                            onDismiss()
                                            onOpenAlbum(item.album)
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.song != null) {
                                SongAlbumArtwork(song = item.song, modifier = Modifier.size(46.dp), cornerRadius = 10.dp, showGlowBorder = false)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x3300E5FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Download, null, tint = NeonCyan)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.badge,
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.subtitle,
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
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun SheetTrackRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) Color(0x2200E5FF) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongAlbumArtwork(
            song = song,
            modifier = Modifier.size(46.dp),
            cornerRadius = 10.dp,
            showGlowBorder = isCurrent
        )
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
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent && isPlaying) {
            Icon(Icons.Default.Equalizer, contentDescription = "Playing", tint = NeonCyan, modifier = Modifier.size(18.dp))
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Remove Favourite" else "Add Favourite",
                tint = if (song.isFavorite) NeonMagenta else TextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
        IconButton(
            onClick = onDownload,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download track",
                tint = SoftCyan,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun SheetArtistRow(
    artist: OnlineArtistResult,
    testTag: String,
    onClick: () -> Unit
) {
    val subtitleText = remember(artist.languages, artist.subtitle) {
        artist.languages.takeIf { it.isNotEmpty() }?.joinToString(" • ")
            ?: artist.subtitle
                ?.replace(JIOSAAVN_ARTIST_REGEX, "")
                ?.trim(' ', '•', '-')
                ?.takeIf { it.isNotBlank() }
            ?: "Featured Artist"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF181D2C)),
            contentAlignment = Alignment.Center
        ) {
            val artistArt = artist.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
            if (artistArt != null) {
                val context = LocalContext.current
                val imageLoader = remember(context) { AppImageLoader.get(context) }
                val artistReq = remember(context, artistArt) {
                    AppImageLoader.buildImageRequest(
                        context,
                        artistArt,
                        AppImageLoader.SIZE_MICRO_THUMB_PX,
                        AppImageLoader.SIZE_MICRO_THUMB_PX
                    )
                }
                AsyncImage(
                    model = artistReq,
                    imageLoader = imageLoader,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, null, tint = SoftCyan)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitleText,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal data class ArtistLanguageGroup(
    val key: String,
    val title: String,
    val artists: List<OnlineArtistResult>
)

internal fun buildGroupedArtistsForSeeAll(
    artists: List<OnlineArtistResult>,
    filter: HomeLanguageFilter
): List<ArtistLanguageGroup> {
    val languageOrder = listOf(
        HomeLanguageFilter.TELUGU,
        HomeLanguageFilter.TAMIL,
        HomeLanguageFilter.HINDI,
        HomeLanguageFilter.ENGLISH
    )
    val selectedLanguages = if (filter == HomeLanguageFilter.ALL) languageOrder else listOf(filter)
    val matchedKeys = mutableSetOf<String>()

    val groups = selectedLanguages.mapNotNull { lang ->
        val seeds = HomeDiscoveryCatalog.ARTISTS_BY_LANGUAGE[lang].orEmpty()
        val orderedMatches = mutableListOf<OnlineArtistResult>()
        val seenNorm = mutableSetOf<String>()

        seeds.forEach { seedName ->
            val found = artists.firstOrNull { ArtistMatchingPolicy.artistsMatch(it.name, seedName) }
            if (found != null) {
                val norm = ArtistMatchingPolicy.normalizeArtistName(found.name)
                if (seenNorm.add(norm)) {
                    orderedMatches.add(found)
                    matchedKeys.add(norm)
                }
            }
        }
        artists.filter { artist ->
            artist.languages.any { it.equals(lang.displayName, ignoreCase = true) }
        }.forEach { artist ->
            val norm = ArtistMatchingPolicy.normalizeArtistName(artist.name)
            if (seenNorm.add(norm)) {
                orderedMatches.add(artist)
                matchedKeys.add(norm)
            }
        }

        if (orderedMatches.isEmpty()) {
            null
        } else {
            ArtistLanguageGroup(
                key = lang.jioSaavnLanguage ?: lang.name.lowercase(),
                title = "${lang.displayName} Artists",
                artists = ArtistMatchingPolicy.deduplicateArtists(orderedMatches)
            )
        }
    }.toMutableList()

    if (filter == HomeLanguageFilter.ALL) {
        val remaining = artists.filter {
            ArtistMatchingPolicy.normalizeArtistName(it.name) !in matchedKeys
        }
        if (remaining.isNotEmpty() && groups.isEmpty()) {
            groups.add(
                ArtistLanguageGroup(
                    key = "featured",
                    title = "Featured Artists",
                    artists = ArtistMatchingPolicy.deduplicateArtists(remaining)
                )
            )
        }
    }

    return groups
}


