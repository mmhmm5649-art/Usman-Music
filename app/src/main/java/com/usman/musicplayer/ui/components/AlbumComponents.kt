package com.usman.musicplayer.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.SearchFilterCategory
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.theme.ActivePillBrush
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.GlassBorderSubtle
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import kotlin.math.abs

@Composable
fun AlbumArtThumbnail(
    artworkUri: String?,
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    showGlowBorder: Boolean = false
) {
    val placeholderSong = remember(artworkUri, title, artist) {
        Song(
            id = abs((artworkUri ?: "${title}_$artist").hashCode().toLong()),
            title = title,
            artist = artist,
            album = title,
            durationMs = 0L,
            filePath = artworkUri.orEmpty(),
            albumArtUri = artworkUri,
            isOnline = !artworkUri.isNullOrBlank(),
            playbackType = PlaybackType.FULL
        )
    }
    SongAlbumArtwork(
        song = placeholderSong,
        modifier = modifier,
        cornerRadius = cornerRadius,
        showGlowBorder = showGlowBorder
    )
}

@Composable
fun SearchFilterCategoryBar(
    selectedCategory: SearchFilterCategory,
    onCategorySelected: (SearchFilterCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchFilterCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            var isFocused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(18.dp)

            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(
                        when {
                            isFocused -> Modifier
                                .border(2.dp, SoftCyan, shape)
                                .background(SoftCyan.copy(alpha = 0.28f), shape)
                            isSelected -> Modifier
                                .background(ActivePillBrush, shape)
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)),
                                    shape
                                )
                            else -> Modifier
                                .background(Color(0x2614182E), shape)
                                .border(1.dp, GlassBorderSubtle, shape)
                        }
                    )
                    .clip(shape)
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_filter_${category.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    color = if (isSelected || isFocused) Color.White else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun OnlineAlbumResultCard(
    album: AlbumSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(if (isFocused) Color(0x441E2544) else Color(0x2614182E), shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) SoftCyan else GlassBorderSubtle,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("online_album_card_${album.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(72.dp)) {
            AlbumArtThumbnail(
                artworkUri = album.artworkUri,
                title = album.title,
                artist = album.artist,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!album.movieName.isNullOrBlank()) {
                val movieLabel = if (album.movieName.equals(album.title, ignoreCase = true)) {
                    "Movie Soundtrack"
                } else {
                    "Movie: ${album.movieName}"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = movieLabel,
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = buildAlbumMetaLine(album),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 5.dp)
            ) {
                if (album.isSoundtrackCollection) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x4400E5FF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (album.isCompleteAlbum) "COMPLETE SOUNDTRACK" else "SOUNDTRACK COLLECTION",
                            color = Color(0xFF67E8F9),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                if (album.songCount != null && album.songCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x337C3AED))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        val countText = when {
                            album.isSoundtrackCollection -> "${album.songCount} SONGS"
                            album.isCompleteAlbum -> "${album.songCount} TRACKS"
                            else -> "${album.songCount} FOUND"
                        }
                        Text(
                            text = countText,
                            color = Color(0xFFD8B4FE),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (album.sourceProvider.equals("Device Library", ignoreCase = true)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3338BDF8))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIBRARY",
                            color = Color(0xFFBAE6FD),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open Album",
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun TvAlbumGridCard(
    album: AlbumSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 180.dp,
    cardHeight: Dp = 248.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvAlbumScale_${album.id}"
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) SoftCyan else Color(0x22FFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x441E2338) else Color(0x1E1E2338))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("tv_album_card_${album.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AlbumArtThumbnail(
                artworkUri = album.artworkUri,
                title = album.title,
                artist = album.artist,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp
            )
            if (album.sourceProvider.equals("Device Library", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB3070A12))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIBRARY",
                        color = SoftCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!album.movieName.isNullOrBlank()) {
            Text(
                text = if (album.movieName.equals(album.title, ignoreCase = true)) {
                    "Movie Soundtrack"
                } else {
                    "Movie: ${album.movieName}"
                },
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = buildAlbumMetaLine(album),
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (album.songCount != null && album.songCount > 0) {
            val tvCountLabel = if (album.isSoundtrackCollection) {
                if (album.isCompleteAlbum) "${album.songCount} songs • Complete OST" else "${album.songCount} songs discovered"
            } else {
                "${album.songCount} tracks"
            }
            Text(
                text = tvCountLabel,
                color = if (album.isSoundtrackCollection) SoftCyan else Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun OnlineArtistResultRow(
    artist: OnlineArtistResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(if (isFocused) Color(0x331E2338) else Color(0x1414182E), shape)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SoftCyan else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("online_artist_card_${artist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!artist.artworkUri.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
            ) {
                AlbumArtThumbnail(
                    artworkUri = artist.artworkUri,
                    title = artist.name,
                    artist = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 25.dp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0x332979FF))
                    .border(1.dp, ElectricBlue.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = artist.name,
                    tint = SoftCyan,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val cleanSubtitle = artist.languages.takeIf { it.isNotEmpty() }?.joinToString(" • ")
                ?: artist.subtitle
                    ?.replace(Regex("(?i)\\bjiosaavn(\\s*artist)?\\b"), "")
                    ?.trim(' ', '•', '-')
                    ?.takeIf { it.isNotBlank() }
                    ?.replaceFirstChar { it.uppercase() }
                ?: "Artist"
            Text(
                text = cleanSubtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildAlbumMetaLine(album: AlbumSearchResult): String {
    val parts = mutableListOf<String>()
    if (album.artist.isNotBlank()) parts.add(album.artist)
    if (!album.year.isNullOrBlank()) parts.add(album.year)
    return parts.joinToString(" • ")
}
