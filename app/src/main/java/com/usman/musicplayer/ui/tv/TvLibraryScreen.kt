package com.usman.musicplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel

private enum class TvLibTab(val label: String) {
    LOCAL("Local MP3 Files"),
    FAVORITES("Favorites"),
    RECENT("Recently Played")
}

@Composable
fun TvLibraryScreen(
    uiState: MusicPlayerUiState,
    viewModel: MusicPlayerViewModel,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(TvLibTab.LOCAL) }

    val currentSongs = when (selectedTab) {
        TvLibTab.LOCAL -> uiState.songs
        TvLibTab.FAVORITES -> uiState.songs.filter { it.isFavorite }
        TvLibTab.RECENT -> uiState.recentlyPlayed
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Header Row with Tabs and Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvLibTab.values().forEach { tab ->
                    TvTabButton(
                        text = tab.label,
                        isSelected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }

            // Quick Play Actions
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvFocusableButton(
                    text = "Shuffle All",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = TvFocusCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        val shuffled = currentSongs.shuffled()
                        if (shuffled.isNotEmpty()) onPlaySong(shuffled.first())
                    }
                )

                if (currentSongs.isNotEmpty()) {
                    TvFocusableButton(
                        text = "Play All",
                        isPrimary = true,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = { onPlaySong(currentSongs.first()) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Content Count
        Text(
            text = "${selectedTab.label} (${currentSongs.size} tracks)",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Grid of Songs
        if (currentSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when (selectedTab) {
                            TvLibTab.FAVORITES -> Icons.Default.Favorite
                            TvLibTab.RECENT -> Icons.Default.QueueMusic
                            else -> Icons.Default.MusicNote
                        },
                        contentDescription = null,
                        tint = Color(0x33FFFFFF),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (selectedTab) {
                            TvLibTab.FAVORITES -> "No favorites added yet"
                            TvLibTab.RECENT -> "No songs played recently"
                            else -> "No local MP3 files detected"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                items(currentSongs, key = { "tv_lib_${it.id}_${it.filePath}" }) { song ->
                    TvFocusableSongCard(
                        song = song,
                        isCurrent = song.id == uiState.currentSong?.id,
                        isPlaying = uiState.isPlaying,
                        onClick = { onPlaySong(song) },
                        cardWidth = 210.dp,
                        cardHeight = 230.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TvTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> TvFocusCyan
                    isSelected -> Color(0xFF00E5FF)
                    else -> Color(0x22FFFFFF)
                },
                shape = shape
            )
            .clip(shape)
            .background(
                when {
                    isFocused -> Color(0xFF0C2442)
                    isSelected -> Color(0xFF0369A1)
                    else -> Color(0xFF131D31)
                }
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}
