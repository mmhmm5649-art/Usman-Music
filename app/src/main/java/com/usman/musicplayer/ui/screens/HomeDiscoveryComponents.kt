package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.usman.musicplayer.R
import com.usman.musicplayer.data.AppImageLoader
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
import com.usman.musicplayer.ui.components.AlbumArtThumbnail
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary

@Composable
fun SpotifyStyleHomeHeader(
    onSearchClick: () -> Unit,
    onProfileSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentHour = rememberCurrentLocalHour()
    val greeting = remember(currentHour) {
        HomeLanguagePolicy.timeOfDayGreeting(currentHour)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "USMAN MUSIC",
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Text(
                    text = "USMAN MUSIC",
                    color = SoftCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = greeting,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home_greeting_text")
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x1EFFFFFF))
                    .testTag("home_search_shortcut_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Music",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onProfileSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x2800E5FF))
                    .testTag("home_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Settings & Profile",
                    tint = SoftCyan,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
internal fun rememberCurrentLocalHour(): Int {
    return remember {
        java.time.LocalTime.now().hour
    }
}

@Composable
fun HomeLanguageFilterChipsRow(
    selectedLanguage: HomeLanguageFilter,
    onSelectLanguage: (HomeLanguageFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeLanguageFilter.entries.forEach { filter ->
            key(filter.name) {
                val isSelected = filter == selectedLanguage
                val backgroundColor = if (isSelected) SoftCyan else Color(0x1EFFFFFF)
                val textColor = if (isSelected) AmoledBlack else TextPrimary
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(backgroundColor)
                        .clickable { onSelectLanguage(filter) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("home_lang_chip_${filter.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.chipLabel,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun HomeQuickAccessGrid(
    cards: List<HomeQuickAccessCard>,
    onCardClick: (HomeQuickAccessCard) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = remember(context) { AppImageLoader.get(context) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val chunks = remember(cards) { cards.chunked(2) }
        chunks.forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCards.forEach { card ->
                    key(card.id) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x16FFFFFF))
                                .clickable { onCardClick(card) }
                                .padding(8.dp)
                                .testTag("home_quick_card_${card.id}"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x3300E5FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val artUri = card.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
                                    if (artUri != null) {
                                        val request = remember(context, artUri) {
                                            AppImageLoader.buildImageRequest(
                                                context = context,
                                                data = artUri,
                                                widthPx = AppImageLoader.SIZE_MICRO_THUMB_PX,
                                                heightPx = AppImageLoader.SIZE_MICRO_THUMB_PX
                                            )
                                        }
                                        AsyncImage(
                                            model = request,
                                            imageLoader = imageLoader,
                                            contentDescription = card.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = when (card.type) {
                                                HomeQuickAccessType.TELUGU_HITS,
                                                HomeQuickAccessType.TAMIL_HITS,
                                                HomeQuickAccessType.ENGLISH_HITS,
                                                HomeQuickAccessType.HINDI_HITS -> Icons.Default.Album
                                                HomeQuickAccessType.RECENTLY_PLAYED -> Icons.Default.Search
                                                HomeQuickAccessType.FAVORITE_SONGS -> Icons.Default.Movie
                                            },
                                            contentDescription = null,
                                            tint = SoftCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = card.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = card.subtitle,
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                if (rowCards.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FeaturedHeroSpotlightCard(
    hero: HomeHeroFeature,
    onPlayHero: () -> Unit,
    onShuffleHero: () -> Unit,
    onOpenAlbum: (AlbumSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { AppImageLoader.get(context) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF16203B))
            .clickable { onPlayHero() }
            .testTag("featured_hero_card")
    ) {
        val heroArt = hero.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
        if (heroArt != null) {
            val heroRequest = remember(context, heroArt) {
                AppImageLoader.buildImageRequest(
                    context = context,
                    data = heroArt,
                    widthPx = AppImageLoader.SIZE_HERO_WIDTH_PX,
                    heightPx = AppImageLoader.SIZE_HERO_HEIGHT_PX
                )
            }
            AsyncImage(
                model = heroRequest,
                imageLoader = imageLoader,
                contentDescription = hero.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC040710), Color(0xFF040710))
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF2A1B4E), Color(0xFF16203B))
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SoftCyan.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = hero.badge,
                        color = SoftCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column {
                Text(
                    text = hero.title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hero.subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HomeDiscoveryCarouselSection(
    section: HomeDiscoverySection,
    currentSong: Song?,
    isPlaying: Boolean,
    onRequestLoad: (String) -> Unit,
    onSeeAllClick: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (AlbumSearchResult) -> Unit,
    onOpenArtist: (OnlineArtistResult) -> Unit,
    onOpenPlaylist: (CuratedPlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(section.id) {
        if (!section.hasAttemptedLoad && !section.isLoading) {
            onRequestLoad(section.id)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("discovery_section_${section.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (section.subtitle.isNotBlank()) {
                    Text(
                        text = section.subtitle,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(
                onClick = { onSeeAllClick(section.id) },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("see_all_${section.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "See All",
                    tint = SoftCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val context = LocalContext.current
        val imageLoader = remember(context) { AppImageLoader.get(context) }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (section.contentType) {
                HomeSectionContentType.SONGS -> {
                    items(
                        items = section.songs,
                        key = { "${section.id}_${it.id}_${it.filePath}" },
                        contentType = { "home_song_card" }
                    ) { song ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onPlaySong(song, section.songs) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x16FFFFFF))
                            ) {
                                SongAlbumArtwork(
                                    song = song,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 12.dp,
                                    showGlowBorder = false,
                                    targetSizePx = AppImageLoader.SIZE_CARD_THUMB_PX
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = song.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HomeSectionContentType.ALBUMS -> {
                    items(
                        items = section.albums,
                        key = { "${section.id}_${it.id}" },
                        contentType = { "home_album_card" }
                    ) { album ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onOpenAlbum(album) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x16FFFFFF))
                            ) {
                                AlbumArtThumbnail(
                                    artworkUri = album.artworkUri,
                                    title = album.title,
                                    artist = album.artist,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 12.dp,
                                    targetSizePx = AppImageLoader.SIZE_CARD_THUMB_PX
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = album.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = album.artist,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HomeSectionContentType.ARTISTS -> {
                    items(
                        items = section.artists,
                        key = { "${section.id}_${it.id}_${it.name}" },
                        contentType = { "home_artist_card" }
                    ) { artist ->
                        Column(
                            modifier = Modifier
                                .width(110.dp)
                                .clickable { onOpenArtist(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF181D2C)),
                                contentAlignment = Alignment.Center
                            ) {
                                val artistArt = artist.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
                                if (artistArt != null) {
                                    val artistReq = remember(context, artistArt) {
                                        AppImageLoader.buildImageRequest(
                                            context = context,
                                            data = artistArt,
                                            widthPx = 260,
                                            heightPx = 260
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
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = SoftCyan,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = artist.name,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HomeSectionContentType.PLAYLISTS -> {
                    items(
                        items = section.playlists,
                        key = { "${section.id}_${it.id}" },
                        contentType = { "home_playlist_card" }
                    ) { playlist ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onOpenPlaylist(playlist) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x16FFFFFF))
                            ) {
                                AlbumArtThumbnail(
                                    artworkUri = playlist.coverArtUri,
                                    title = playlist.title,
                                    artist = playlist.description,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 12.dp,
                                    targetSizePx = AppImageLoader.SIZE_CARD_THUMB_PX
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playlist.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
