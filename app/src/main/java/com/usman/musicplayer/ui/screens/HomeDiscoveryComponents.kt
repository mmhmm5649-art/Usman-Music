package com.usman.musicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Calendar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usman.musicplayer.R
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
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonMagenta
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

@Composable
fun rememberCurrentLocalHour(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            val updatedHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (updatedHour != currentHour) {
                currentHour = updatedHour
            }
        }
    }

    return currentHour
}

@Composable
fun SpotifyStyleHomeHeader(
    hasNotifications: Boolean,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x1EFFFFFF))
                        .testTag("home_notifications_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "New Releases & Updates",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (hasNotifications) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonCyan)
                    )
                }
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
fun HomeLanguageFilterChipsRow(
    selectedLanguage: HomeLanguageFilter,
    onSelectLanguage: (HomeLanguageFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = remember {
        listOf(
            HomeLanguageFilter.ALL,
            HomeLanguageFilter.TELUGU,
            HomeLanguageFilter.TAMIL,
            HomeLanguageFilter.ENGLISH,
            HomeLanguageFilter.HINDI
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(filters, key = { it.name }) { filter ->
            val isSelected = selectedLanguage == filter
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) NeonCyan else Color(0xFF181D2C),
                animationSpec = tween(180),
                label = "chipBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF030712) else TextPrimary,
                animationSpec = tween(180),
                label = "chipText"
            )

            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) NeonCyan else Color(0x28FFFFFF),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { onSelectLanguage(filter) }
                    .padding(horizontal = 18.dp)
                    .testTag("home_lang_chip_${filter.chipLabel.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.chipLabel,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        cards.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { card ->
                    QuickAccessTile(
                        card = card,
                        onClick = { onCardClick(card) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    card: HomeQuickAccessCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentGradient = remember(card.type) {
        when (card.type) {
            HomeQuickAccessType.TELUGU_HITS -> listOf(Color(0xFF0284C7), Color(0xFF1E3A8A))
            HomeQuickAccessType.TAMIL_HITS -> listOf(Color(0xFF9333EA), Color(0xFF4C1D95))
            HomeQuickAccessType.ENGLISH_HITS -> listOf(Color(0xFF0D9488), Color(0xFF134E4A))
            HomeQuickAccessType.HINDI_HITS -> listOf(Color(0xFFDB2777), Color(0xFF831843))
            HomeQuickAccessType.RECENTLY_PLAYED -> listOf(Color(0xFF2563EB), Color(0xFF1E293B))
            HomeQuickAccessType.FAVORITE_SONGS -> listOf(Color(0xFF7C3AED), Color(0xFFEC4899))
        }
    }

    Surface(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("home_quick_card_${card.id}"),
        color = Color(0xFF161B2A),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Brush.linearGradient(accentGradient)),
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
                    Icon(
                        imageVector = icon,
                        contentDescription = card.title,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
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
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF28194A),
                        Color(0xFF12233E),
                        Color(0xFF0E131F)
                    )
                )
            )
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(20.dp))
            .clickable(onClick = onPlayHero)
            .padding(16.dp)
            .testTag("home_featured_hero_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2538)),
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
                    SongAlbumArtwork(
                        song = hero.primarySong,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 14.dp,
                        showGlowBorder = false
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = NeonCyan, modifier = Modifier.size(36.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hero.badge,
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hero.title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hero.subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlayHero,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color(0xFF040710)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("hero_play_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    if (hero.queueSongs.size > 1) {
                        IconButton(
                            onClick = onShuffleHero,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .testTag("hero_shuffle_button")
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Mix", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (hero.album != null) {
                        TextButton(
                            onClick = { onOpenAlbum(hero.album) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Album", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (section.subtitle.isNotBlank()) {
                    Text(
                        text = section.subtitle,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (section.hasContent) {
                TextButton(
                    onClick = { onSeeAllClick(section.id) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_see_all_${section.id}")
                ) {
                    Text(
                        text = "See all",
                        color = SoftCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        when {
            !section.hasContent && (section.isLoading || !section.hasAttemptedLoad) -> {
                CarouselSkeletonRow(isCircle = section.contentType == HomeSectionContentType.ARTISTS)
            }

            section.contentType == HomeSectionContentType.SONGS -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.songs.take(16), key = { "${section.id}_${it.id}_${it.filePath}" }) { song ->
                        HomeSongCard(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            isPlaying = isPlaying,
                            onPlay = { onPlaySong(song, section.songs) },
                            onToggleFavorite = { onToggleFavorite(song) }
                        )
                    }
                }
            }

            section.contentType == HomeSectionContentType.ALBUMS -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.albums.take(15), key = { "${section.id}_${it.id}" }) { album ->
                        HomeAlbumCard(
                            album = album,
                            onClick = { onOpenAlbum(album) }
                        )
                    }
                }
            }

            section.contentType == HomeSectionContentType.ARTISTS -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(section.artists.take(20), key = { "${section.id}_${it.id}_${it.name}" }) { artist ->
                        HomeArtistCircleCard(
                            artist = artist,
                            onClick = { onOpenArtist(artist) }
                        )
                    }
                }
            }

            section.contentType == HomeSectionContentType.PLAYLISTS -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.playlists.take(12), key = { "${section.id}_${it.id}" }) { playlist ->
                        HomePlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSongCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(146.dp)
            .clickable(onClick = onPlay)
    ) {
        Box(modifier = Modifier.size(146.dp)) {
            SongAlbumArtwork(
                song = song,
                cornerRadius = 14.dp,
                showGlowBorder = isCurrent,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                        contentDescription = "Playing",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (song.isFavorite) "Remove Favourite" else "Add Favourite",
                        tint = if (song.isFavorite) NeonMagenta else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            color = if (isCurrent) NeonCyan else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeAlbumCard(
    album: AlbumSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(154.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(154.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF181D2C))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!album.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = album.artworkUri,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Album, null, tint = SoftCyan, modifier = Modifier.size(44.dp))
            }

            val badgeText = when {
                !album.movieName.isNullOrBlank() -> "OST"
                !album.year.isNullOrBlank() -> album.year
                else -> "ALBUM"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (!album.movieName.isNullOrBlank()) {
                        Icon(Icons.Default.Movie, null, tint = NeonCyan, modifier = Modifier.size(11.dp))
                    }
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
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
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeArtistCircleCard(
    artist: OnlineArtistResult,
    onClick: () -> Unit
) {
    val subtitleText = remember(artist.languages, artist.subtitle) {
        artist.languages.takeIf { it.isNotEmpty() }?.joinToString(" • ")
            ?: artist.subtitle?.takeIf { it.isNotBlank() }
            ?: "Artist"
    }

    Column(
        modifier = Modifier
            .width(114.dp)
            .clickable(onClick = onClick)
            .testTag("home_artist_card_${artist.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A2033))
                .border(1.5.dp, Color(0x3300E5FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = artist.artworkUri,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, null, tint = SoftCyan, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitleText,
            color = TextMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomePlaylistCard(
    playlist: CuratedPlaylist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.coverArtUri.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverArtUri,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.QueueMusic, null, tint = NeonCyan, modifier = Modifier.size(44.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NeonCyan),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF040710), modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.description,
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CarouselSkeletonRow(isCircle: Boolean) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false
    ) {
        items(4) {
            Column(modifier = Modifier.width(if (isCircle) 114.dp else 146.dp)) {
                Box(
                    modifier = Modifier
                        .size(if (isCircle) 110.dp else 146.dp)
                        .clip(if (isCircle) CircleShape else RoundedCornerShape(14.dp))
                        .background(Color(0xFF141826))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1B2032))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF141826))
                )
            }
        }
    }
}
