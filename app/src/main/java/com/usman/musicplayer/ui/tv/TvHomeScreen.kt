package com.usman.musicplayer.ui.tv

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeHeroFeature
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.HomeQuickAccessCard
import com.usman.musicplayer.model.HomeQuickAccessType
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.screens.rememberCurrentLocalHour
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextSecondary
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

/**
 * TV-Optimized Spotify-Style Home Screen for Android TV / Google TV.
 * Supports ONLY 4 languages (Telugu, Tamil, English, Hindi) + ALL discovery,
 * spacious D-pad navigable shelves, Quick Access tiles, Hero spotlight, and overlays.
 */
@Composable
fun TvHomeScreen(
    uiState: MusicPlayerUiState,
    onNavigateToSearch: () -> Unit,
    onSearchCategory: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onTabGenreSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectHomeLanguage: (HomeLanguageFilter) -> Unit = {},
    onRequestSectionLoad: (String) -> Unit = {},
    onLoadMoreSection: (String) -> Unit = {},
    onOpenQuickCard: (HomeQuickAccessCard) -> Unit = {},
    onOpenCuratedPlaylist: (CuratedPlaylist) -> Unit = {},
    onCloseCollectionSheet: () -> Unit = {},
    onOpenSeeAllSection: (String) -> Unit = {},
    onCloseSeeAllSection: () -> Unit = {},
    onPlayQueue: (Song, List<Song>) -> Unit = { s, _ -> onPlaySong(s) },
    onShuffleQueue: (List<Song>) -> Unit = { songs -> if (songs.isNotEmpty()) onPlaySong(songs.random()) },
    onToggleFavorite: (Song) -> Unit = {},
    onOpenAlbum: (AlbumSearchResult) -> Unit = {},
    onOpenArtist: (OnlineArtistResult) -> Unit = { artist -> onSearchCategory(artist.name) },
    onSavePlaylistToLibrary: (String, List<Song>, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val activeCollection = uiState.activeHomeCollectionSheet
    if (activeCollection != null) {
        TvHomeCollectionOverlay(
            playlist = activeCollection,
            isLoading = uiState.isHomeCollectionLoading,
            currentSong = uiState.currentSong,
            onBack = onCloseCollectionSheet,
            onPlaySong = onPlayQueue,
            onShuffleAll = onShuffleQueue,
            onToggleFavorite = onToggleFavorite,
            onSaveToLibrary = onSavePlaylistToLibrary
        )
        return
    }

    val seeAllSection = uiState.selectedHomeSeeAllSectionId?.let { id ->
        uiState.homeSections.find { it.id == id }
    }
    if (seeAllSection != null) {
        TvHomeSeeAllOverlay(
            section = seeAllSection,
            currentSong = uiState.currentSong,
            onBack = onCloseSeeAllSection,
            onLoadMore = onLoadMoreSection,
            onPlaySong = onPlayQueue,
            onShuffleAll = onShuffleQueue,
            onToggleFavorite = onToggleFavorite,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenPlaylist = onOpenCuratedPlaylist
        )
        return
    }

    val currentHour = rememberCurrentLocalHour()
    val greeting = remember(currentHour) {
        HomeLanguagePolicy.timeOfDayGreeting(currentHour)
    }
    val filters = remember {
        listOf(
            HomeLanguageFilter.ALL,
            HomeLanguageFilter.TELUGU,
            HomeLanguageFilter.TAMIL,
            HomeLanguageFilter.ENGLISH,
            HomeLanguageFilter.HINDI
        )
    }

    val visibleSections by remember(uiState.homeSections) {
        derivedStateOf {
            uiState.homeSections.filter {
                it.language == HomeLanguageFilter.ALL ||
                    it.language.jioSaavnLanguage in HomeLanguagePolicy.ALLOWED_LANGUAGES
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12)),
        contentPadding = PaddingValues(bottom = 48.dp, top = 22.dp, start = 28.dp, end = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. TV Spotify-Style Header + Search / Settings Shortcuts
        item(key = "tv_home_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "USMAN MUSIC • TV",
                        color = SoftCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = greeting,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("tv_home_greeting_text")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvFocusButton(
                        label = "Search",
                        icon = Icons.Default.Search,
                        onClick = onNavigateToSearch,
                        testTag = "tv_home_search_button"
                    )
                    TvFocusButton(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        onClick = onOpenSettings,
                        testTag = "tv_home_settings_button"
                    )
                }
            }
        }

        // 2. Four Language Filter Chips (ALL | TELUGU | TAMIL | ENGLISH | HINDI)
        item(key = "tv_language_chips") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(filters, key = { it.name }) { filter ->
                    TvLanguageChip(
                        filter = filter,
                        isSelected = uiState.selectedHomeLanguage == filter,
                        onClick = { onSelectHomeLanguage(filter) }
                    )
                }
            }
        }

        // 3. Featured Hero Spotlight Card
        val hero = uiState.homeHeroFeature
        if (hero != null) {
            item(key = "tv_featured_hero") {
                TvFeaturedHeroBanner(
                    hero = hero,
                    onPlayHero = {
                        val lead = hero.primarySong ?: hero.queueSongs.firstOrNull()
                        if (lead != null) {
                            onPlayQueue(lead, hero.queueSongs)
                        } else if (hero.album != null) {
                            onOpenAlbum(hero.album)
                        }
                    },
                    onShuffleHero = { onShuffleQueue(hero.queueSongs) }
                )
            }
        }

        // 4. Quick Access 6 Cards (Telugu Hits, Tamil Hits, English Hits, Hindi Hits, Recently Played, Your Favourite Songs)
        if (uiState.homeQuickCards.isNotEmpty()) {
            item(key = "tv_quick_access") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "QUICK ACCESS",
                        color = SoftCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(uiState.homeQuickCards, key = { it.id }) { card ->
                            TvQuickAccessCard(
                                card = card,
                                onClick = { onOpenQuickCard(card) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Lazy-loaded Discovery & 4-Language Shelves
        items(
            items = visibleSections,
            key = { it.id },
            contentType = { it.contentType }
        ) { section ->
            TvHomeDiscoverySectionRow(
                section = section,
                onRequestLoad = onRequestSectionLoad,
                onSeeAll = onOpenSeeAllSection,
                onPlaySong = onPlayQueue,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenPlaylist = onOpenCuratedPlaylist
            )
        }
    }
}

@Composable
private fun TvLanguageChip(
    filter: HomeLanguageFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvLangChip"
    )
    Box(
        modifier = Modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> NeonCyan
                    else -> Color(0x28FFFFFF)
                },
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                when {
                    isSelected -> NeonCyan
                    isFocused -> SoftCyan.copy(alpha = 0.35f)
                    else -> Color(0xFF151A2A)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp)
            .testTag("tv_lang_chip_${filter.chipLabel.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = filter.chipLabel,
            color = if (isSelected) Color(0xFF040710) else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun TvFeaturedHeroBanner(
    hero: HomeHeroFeature,
    onPlayHero: () -> Unit,
    onShuffleHero: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF28194A), Color(0xFF12233E), Color(0xFF0E131F))
                )
            )
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(22.dp))
            .padding(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A2236)),
                contentAlignment = Alignment.Center
            ) {
                if (!hero.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = hero.artworkUri,
                        contentDescription = hero.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (hero.primarySong != null) {
                    SongAlbumArtwork(song = hero.primarySong, modifier = Modifier.fillMaxSize(), cornerRadius = 16.dp, showGlowBorder = false)
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = SoftCyan, modifier = Modifier.size(44.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(hero.badge, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(hero.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(hero.subtitle, color = TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvFocusButton(
                    label = "Play",
                    icon = Icons.Default.PlayArrow,
                    isPrimary = true,
                    onClick = onPlayHero,
                    testTag = "tv_hero_play_button"
                )
                if (hero.queueSongs.size > 1) {
                    TvFocusButton(
                        label = "Shuffle",
                        icon = Icons.Default.Shuffle,
                        onClick = onShuffleHero,
                        testTag = "tv_hero_shuffle_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun TvQuickAccessCard(
    card: HomeQuickAccessCard,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvQaScale"
    )
    Row(
        modifier = Modifier
            .width(248.dp)
            .height(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.5.dp else 1.dp, if (isFocused) SoftCyan else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color(0xFF222A42) else Color(0xFF141828))
            .clickable(onClick = onClick)
            .testTag("tv_quick_card_${card.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF7C3AED)))),
            contentAlignment = Alignment.Center
        ) {
            if (!card.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = card.artworkUri,
                    contentDescription = card.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val icon = when (card.type) {
                    HomeQuickAccessType.RECENTLY_PLAYED -> Icons.Default.History
                    HomeQuickAccessType.FAVORITE_SONGS -> Icons.Default.Favorite
                    else -> Icons.Default.MusicNote
                }
                Icon(icon, contentDescription = card.title, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(card.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvHomeDiscoverySectionRow(
    section: HomeDiscoverySection,
    onRequestLoad: (String) -> Unit,
    onSeeAll: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenAlbum: (AlbumSearchResult) -> Unit,
    onOpenArtist: (OnlineArtistResult) -> Unit,
    onOpenPlaylist: (CuratedPlaylist) -> Unit
) {
    LaunchedEffect(section.id) {
        if (!section.hasAttemptedLoad && !section.isLoading) {
            onRequestLoad(section.id)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (section.subtitle.isNotBlank()) {
                    Text(section.subtitle, color = TextSecondary, fontSize = 12.sp)
                }
            }
            if (section.hasContent) {
                var seeAllFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .onFocusChanged { seeAllFocused = it.isFocused }
                        .border(if (seeAllFocused) 2.dp else 0.dp, if (seeAllFocused) SoftCyan else Color.Transparent, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (seeAllFocused) SoftCyan.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { onSeeAll(section.id) }
                        .padding(horizontal = 14.dp)
                        .testTag("tv_see_all_${section.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("See All", color = SoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        when {
            !section.hasContent && (section.isLoading || !section.hasAttemptedLoad) -> {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF131826))
                        )
                    }
                }
            }

            section.contentType == HomeSectionContentType.SONGS -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(section.songs.take(15), key = { "${section.id}_${it.id}_${it.filePath}" }) { song ->
                        TvSongCard(
                            song = song,
                            onClick = { onPlaySong(song, section.songs) }
                        )
                    }
                }
            }

            section.contentType == HomeSectionContentType.ALBUMS -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(section.albums.take(14), key = { "${section.id}_${it.id}" }) { album ->
                        TvHomeAlbumCard(album = album, onClick = { onOpenAlbum(album) })
                    }
                }
            }

            section.contentType == HomeSectionContentType.ARTISTS -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(section.artists.take(20), key = { "${section.id}_${it.id}_${it.name}" }) { artist ->
                        TvHomeArtistCard(artist = artist, onClick = { onOpenArtist(artist) })
                    }
                }
            }

            section.contentType == HomeSectionContentType.PLAYLISTS -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(section.playlists.take(12), key = { "${section.id}_${it.id}" }) { playlist ->
                        TvHomePlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
                    }
                }
            }
        }
    }
}
