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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

/**
 * TV-Optimized Home Screen for Android TV / Google TV.
 * Offers large typography, spacious category rows, clear section headers,
 * and high-contrast D-pad remote focus navigation.
 */
@Composable
fun TvHomeScreen(
    uiState: MusicPlayerUiState,
    onNavigateToSearch: () -> Unit,
    onSearchCategory: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onTabGenreSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val genreTabs = listOf("Global Hits", "Telugu Hits", "Bollywood", "Phonk Drift", "Top English", "2026 Releases")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12)),
        contentPadding = PaddingValues(bottom = 40.dp, top = 20.dp, start = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // TV Banner Header
        item {
            TvHomeBanner(
                activeTab = uiState.activeHomeGenreTab,
                onNavigateToSearch = onNavigateToSearch,
                onOpenSettings = onOpenSettings
            )
        }

        // Genre Selector Row
        item {
            Column {
                Text(
                    text = "DISCOVER GENRES",
                    color = SoftCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(genreTabs) { tab ->
                        var isFocused by remember { mutableStateOf(false) }
                        val isSelected = uiState.activeHomeGenreTab == tab

                        Box(
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .border(
                                    width = if (isFocused) 2.dp else 0.dp,
                                    color = if (isFocused) SoftCyan else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    when {
                                        isFocused -> SoftCyan.copy(alpha = 0.35f)
                                        isSelected -> SoftCyan.copy(alpha = 0.2f)
                                        else -> Color(0x331E2338)
                                    }
                                )
                                .clickable {
                                    onTabGenreSelected(tab)
                                    onSearchCategory(tab)
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = tab,
                                color = if (isFocused || isSelected) Color.White else Color(0xFFCBD5E1),
                                fontSize = 15.sp,
                                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Active Category Songs Row
        item {
            val activeSongs = when (uiState.activeHomeGenreTab) {
                "Telugu Hits" -> uiState.onlineTeluguSongs
                "Bollywood" -> uiState.onlineBollywoodSongs
                "English Pop" -> uiState.onlineEnglishSongs
                else -> uiState.onlineTrendingSongs
            }
            if (activeSongs.isNotEmpty()) {
                TvSongSection(
                    title = uiState.activeHomeGenreTab.uppercase(),
                    songs = activeSongs,
                    onPlaySong = onPlaySong
                )
            }
        }

        // Trending Online Tracks Row
        item {
            if (uiState.onlineTrendingSongs.isNotEmpty()) {
                TvSongSection(
                    title = "GLOBAL TRENDING",
                    songs = uiState.onlineTrendingSongs,
                    onPlaySong = onPlaySong
                )
            }
        }

        // Telugu Hits Row
        item {
            if (uiState.onlineTeluguSongs.isNotEmpty()) {
                TvSongSection(
                    title = "TELUGU HITS & TOLLYWOOD",
                    songs = uiState.onlineTeluguSongs,
                    onPlaySong = onPlaySong
                )
            }
        }

        // Bollywood Hits Row
        item {
            if (uiState.onlineBollywoodSongs.isNotEmpty()) {
                TvSongSection(
                    title = "BOLLYWOOD HINDI",
                    songs = uiState.onlineBollywoodSongs,
                    onPlaySong = onPlaySong
                )
            }
        }

        // Device Library Songs Row
        item {
            if (uiState.songs.isNotEmpty()) {
                TvSongSection(
                    title = "LOCAL DEVICE LIBRARY",
                    songs = uiState.songs,
                    onPlaySong = onPlaySong
                )
            }
        }
    }
}

@Composable
private fun TvHomeBanner(
    activeTab: String,
    onNavigateToSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val bannerGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF1E293B),
            Color(0xFF0F172A)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bannerGradient)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "USMAN MUSIC FOR TV",
                    color = SoftCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Explore & Stream Online Songs",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                var isSearchFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onNavigateToSearch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSearchFocused) SoftCyan else Color(0x331E2338),
                        contentColor = if (isSearchFocused) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.onFocusChanged { isSearchFocused = it.isFocused }
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Songs", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TvSongSection(
    title: String,
    songs: List<Song>,
    onPlaySong: (Song) -> Unit
) {
    Column {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(songs, key = { "${it.id}_${it.filePath}" }) { song ->
                TvSongCard(
                    song = song,
                    onClick = { onPlaySong(song) }
                )
            }
        }
    }
}
