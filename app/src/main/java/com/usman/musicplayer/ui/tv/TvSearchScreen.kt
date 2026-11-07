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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel

@Composable
fun TvSearchScreen(
    uiState: MusicPlayerUiState,
    viewModel: MusicPlayerViewModel,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf(uiState.searchQuery) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val suggestedTags = listOf(
        "Boom Boom", "Sai Abhyankkar", "Anirudh", "Arijit Singh",
        "Telugu Top 50", "Phonk", "Katchi Sera", "Sid Sriram", "Shreya Ghoshal"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Search Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val searchBarShape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .onFocusChanged { isSearchFocused = it.isFocused }
                    .border(
                        width = if (isSearchFocused) 2.5.dp else 1.dp,
                        color = if (isSearchFocused) TvFocusCyan else Color(0x33FFFFFF),
                        shape = searchBarShape
                    )
                    .clip(searchBarShape)
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearchFocused) TvFocusCyan else Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = searchInput,
                    onValueChange = {
                        searchInput = it
                        viewModel.updateSearchQuery(it)
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(TvFocusCyan),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.updateSearchQuery(searchInput) }
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchInput.isEmpty()) {
                            Text(
                                text = "Search tracks, artists, or albums via JioSaavn...",
                                color = Color(0xFF64748B),
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )

                if (searchInput.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                searchInput = ""
                                viewModel.updateSearchQuery("")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (uiState.isOnlineSearching) {
                Spacer(modifier = Modifier.width(16.dp))
                CircularProgressIndicator(
                    color = TvFocusCyan,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Suggestion Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(suggestedTags) { tag ->
                TvSearchChip(
                    text = tag,
                    isSelected = searchInput.equals(tag, ignoreCase = true),
                    onClick = {
                        searchInput = tag
                        viewModel.updateSearchQuery(tag)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Search Results
        val results = uiState.onlineSearchResults
        if (results.isEmpty()) {
            if (searchInput.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0x33FFFFFF),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Type using your TV remote to search millions of songs",
                            color = Color(0xFF94A3B8),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Streams directly in 320kbps HD Audio",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (!uiState.isOnlineSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks found for \"$searchInput\"",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            Text(
                text = "Online Search Results (${results.size})",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                items(results, key = { "tv_search_${it.id}_${it.filePath}" }) { song ->
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
private fun TvSearchChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> TvFocusCyan
                    isSelected -> Color(0xFF38BDF8)
                    else -> Color(0x26FFFFFF)
                },
                shape = shape
            )
            .clip(shape)
            .background(
                when {
                    isFocused -> Color(0xFF0C2A4A)
                    isSelected -> Color(0xFF0369A1)
                    else -> Color(0xFF1E293B)
                }
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused || isSelected) Color.White else Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
