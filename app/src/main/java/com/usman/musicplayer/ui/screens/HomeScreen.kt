package com.usman.musicplayer.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.data.AppImageLoader
import com.usman.musicplayer.data.repository.ExternalServiceType
import com.usman.musicplayer.data.repository.MusicGenreCategory
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.HomeQuickAccessCard
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@Composable
fun HomeScreen(
    uiState: MusicPlayerUiState,
    onNavigateToSearch: () -> Unit,
    onSearchCategory: (String) -> Unit,
    onNavigateToLibrary: (LibraryTab) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenService: (Context, Song, ExternalServiceType) -> Unit,
    onTabGenreSelected: (String) -> Unit,
    onSelectCategory: (MusicGenreCategory) -> Unit,
    onClearCategory: () -> Unit,
    onRescanClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
    onRetryTelugu: () -> Unit = {},
    onRetryJamendo: () -> Unit = {},
    onOpenSettings: () -> Unit = onRescanClick,
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
    onDownloadSong: (Song) -> Unit = {},
    onOpenAlbum: (AlbumSearchResult) -> Unit = {},
    onOpenArtist: (OnlineArtistResult) -> Unit = { artist -> onSearchCategory(artist.name) },
    onSavePlaylistToLibrary: (String, List<Song>, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {

    val isNewReleasesMode = uiState.activeHomeGenreTab == "New Releases"

    val visibleSections by remember(uiState.homeSections, isNewReleasesMode) {
        derivedStateOf {
            val base = uiState.homeSections.filter { section ->
                section.language == HomeLanguageFilter.ALL ||
                    section.language.jioSaavnLanguage in HomeLanguagePolicy.ALLOWED_LANGUAGES
            }
            if (isNewReleasesMode) {
                base.filter {
                    it.id.contains("new", ignoreCase = true) ||
                        it.id.contains("latest", ignoreCase = true) ||
                        it.id.contains("soundtrack", ignoreCase = true) ||
                        it.id.contains("featured_albums", ignoreCase = true)
                }
            } else {
                base
            }
        }
    }

    val ambientColor = remember(uiState.selectedHomeLanguage) {
        when (uiState.selectedHomeLanguage) {
            HomeLanguageFilter.TELUGU -> Color(0xFF0284C7)
            HomeLanguageFilter.TAMIL -> Color(0xFF9333EA)
            HomeLanguageFilter.ENGLISH -> Color(0xFF0D9488)
            HomeLanguageFilter.HINDI -> Color(0xFFDB2777)
            HomeLanguageFilter.ALL -> NeonPurple
        }
    }

    val context = LocalContext.current
    val visiblePrefetchSignature = remember(uiState.homeQuickCards, uiState.homeHeroFeature, visibleSections) {
        buildList {
            uiState.homeQuickCards.take(6).forEach { card ->
                card.artworkUri?.takeIf { it.isNotBlank() }?.let { add(it to AppImageLoader.SIZE_MICRO_THUMB_PX) }
            }
            uiState.homeHeroFeature?.artworkUri?.takeIf { it.isNotBlank() }?.let {
                add(it to AppImageLoader.SIZE_HERO_WIDTH_PX)
            }
            visibleSections.take(3).forEach { section ->
                when (section.contentType) {
                    com.usman.musicplayer.model.HomeSectionContentType.SONGS ->
                        section.songs.take(4).forEach { s -> s.albumArtUri?.takeIf { it.isNotBlank() }?.let { add(it to AppImageLoader.SIZE_CARD_THUMB_PX) } }
                    com.usman.musicplayer.model.HomeSectionContentType.ALBUMS ->
                        section.albums.take(4).forEach { a -> a.artworkUri?.takeIf { it.isNotBlank() }?.let { add(it to AppImageLoader.SIZE_CARD_THUMB_PX) } }
                    com.usman.musicplayer.model.HomeSectionContentType.ARTISTS ->
                        section.artists.take(4).forEach { ar -> ar.artworkUri?.takeIf { it.isNotBlank() }?.let { add(it to 260) } }
                    com.usman.musicplayer.model.HomeSectionContentType.PLAYLISTS ->
                        section.playlists.take(4).forEach { p -> p.coverArtUri?.takeIf { it.isNotBlank() }?.let { add(it to AppImageLoader.SIZE_CARD_THUMB_PX) } }
                }
            }
        }.distinctBy { it.first }
    }

    LaunchedEffect(visiblePrefetchSignature) {
        visiblePrefetchSignature.forEach { (url, sizePx) ->
            val heightPx = if (sizePx == AppImageLoader.SIZE_HERO_WIDTH_PX) AppImageLoader.SIZE_HERO_HEIGHT_PX else sizePx
            AppImageLoader.prefetchArtwork(context, url, widthPx = sizePx, heightPx = heightPx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Subtle Spotify-inspired top ambient wash reacting to selected language
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ambientColor.copy(alpha = 0.24f),
                            ElectricBlue.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            item(key = "home_header") {
                SpotifyStyleHomeHeader(
                    onSearchClick = onNavigateToSearch,
                    onProfileSettingsClick = onOpenSettings
                )
            }

            item(key = "home_language_chips") {
                HomeLanguageFilterChipsRow(
                    selectedLanguage = uiState.selectedHomeLanguage,
                    onSelectLanguage = onSelectHomeLanguage
                )
            }

            if (isNewReleasesMode) {
                item(key = "new_releases_title") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "New Releases",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Fresh drops across Telugu, Tamil, English & Hindi",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // Quick Access 6-Card Grid (Telugu Hits, Tamil Hits, English Hits, Hindi Hits, Recently Played, Your Favourite Songs)
                item(key = "home_quick_access_grid") {
                    HomeQuickAccessGrid(
                        cards = uiState.homeQuickCards,
                        onCardClick = onOpenQuickCard
                    )
                }

                // Featured Hero Spotlight Card
                val hero = uiState.homeHeroFeature
                if (hero != null) {
                    item(key = "home_featured_hero") {
                        FeaturedHeroSpotlightCard(
                            hero = hero,
                            onPlayHero = {
                                val song = hero.primarySong ?: hero.queueSongs.firstOrNull()
                                if (song != null) {
                                    onPlayQueue(song, hero.queueSongs)
                                } else if (hero.album != null) {
                                    onOpenAlbum(hero.album)
                                }
                            },
                            onShuffleHero = { onShuffleQueue(hero.queueSongs) },
                            onOpenAlbum = onOpenAlbum
                        )
                    }
                }
            }

            // Lazy-loaded Discovery & 4-Language Sections
            items(
                items = visibleSections,
                key = { it.id },
                contentType = { it.contentType }
            ) { section ->
                HomeDiscoveryCarouselSection(
                    section = section,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    onRequestLoad = onRequestSectionLoad,
                    onSeeAllClick = onOpenSeeAllSection,
                    onPlaySong = onPlayQueue,
                    onToggleFavorite = onToggleFavorite,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onOpenPlaylist = onOpenCuratedPlaylist
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Interactive Playlist / Quick-Access Collection Bottom Sheet
        val activeCollection = uiState.activeHomeCollectionSheet
        if (activeCollection != null) {
            HomeCollectionDetailSheet(
                playlist = activeCollection,
                isLoading = uiState.isHomeCollectionLoading,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                onDismiss = onCloseCollectionSheet,
                onPlaySong = { song, queue -> onPlayQueue(song, queue) },
                onShuffleAll = onShuffleQueue,
                onToggleFavorite = onToggleFavorite,
                onDownloadSong = onDownloadSong,
                onSaveToLibrary = onSavePlaylistToLibrary
            )
        }

        // Interactive "See All" Section Sheet with Pagination
        val seeAllId = uiState.selectedHomeSeeAllSectionId
        val seeAllSection = seeAllId?.let { id -> uiState.homeSections.find { it.id == id } }
        if (seeAllSection != null) {
            HomeSeeAllSheet(
                section = seeAllSection,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                onDismiss = onCloseSeeAllSection,
                onLoadMore = onLoadMoreSection,
                onPlaySong = onPlayQueue,
                onShuffleAll = onShuffleQueue,
                onToggleFavorite = onToggleFavorite,
                onDownloadSong = onDownloadSong,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenPlaylist = onOpenCuratedPlaylist,
                onSaveSectionAsPlaylist = onSavePlaylistToLibrary
            )
        }

    }
}
