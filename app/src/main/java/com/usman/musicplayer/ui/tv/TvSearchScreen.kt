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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.SearchSource

/**
 * TV-Optimized Search Screen for Android TV / Google TV.
 * Features large accessible search field, soft keyboard support,
 * Online vs Device source toggle, recent search history chips,
 * and high-contrast result grid.
 */
@Composable
fun TvSearchScreen(
    uiState: MusicPlayerUiState,
    onSearchQueryChange: (String) -> Unit,
    onTriggerOnlineSearch: (String) -> Unit,
    onSearchSourceChange: (SearchSource) -> Unit,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val recentQueries = listOf("Nazar Lagi", "Sai Abhyankkar", "Telugu Hits", "Asees Kaur", "Pushpa 2", "Arjan Vailly")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .padding(top = 20.dp, start = 24.dp, end = 24.dp)
    ) {
        // TV Header Title & Search Source Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SEARCH MUSIC",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Search Source Pills (Online Catalog vs Device Library)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvSourcePill(
                    label = "Online Catalog",
                    isSelected = uiState.searchSource == SearchSource.ONLINE,
                    onClick = { onSearchSourceChange(SearchSource.ONLINE) }
                )
                TvSourcePill(
                    label = "Device Library",
                    isSelected = uiState.searchSource == SearchSource.DEVICE,
                    onClick = { onSearchSourceChange(SearchSource.DEVICE) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Large Accessible TV Search Input Bar
        var isTextFieldFocused by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { q ->
                onSearchQueryChange(q)
                if (uiState.searchSource == SearchSource.ONLINE && q.trim().length >= 2) {
                    onTriggerOnlineSearch(q.trim())
                }
            },
            placeholder = { Text("Search songs, artists, or albums...", color = Color(0xFF64748B), fontSize = 16.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = SoftCyan) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSearchQueryChange("") }
                            .padding(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (uiState.searchSource == SearchSource.ONLINE && uiState.searchQuery.isNotBlank()) {
                    onTriggerOnlineSearch(uiState.searchQuery.trim())
                }
                focusManager.clearFocus()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SoftCyan,
                unfocusedBorderColor = Color(0x441E2338),
                focusedContainerColor = Color(0x331E2338),
                unfocusedContainerColor = Color(0x181E2338),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isTextFieldFocused = it.isFocused }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Recent Search Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(recentQueries) { query ->
                var isFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) SoftCyan else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isFocused) SoftCyan.copy(alpha = 0.3f) else Color(0x221E2338))
                        .clickable {
                            onSearchQueryChange(query)
                            if (uiState.searchSource == SearchSource.ONLINE) {
                                onTriggerOnlineSearch(query)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = query, color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Results Section
        val results = if (uiState.searchSource == SearchSource.ONLINE) uiState.onlineSearchResults else uiState.deviceMatchingSongs

        when {
            uiState.isOnlineSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SoftCyan, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Searching online catalog...", color = Color(0xFFCBD5E1), fontSize = 16.sp)
                    }
                }
            }
            results.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No relevant songs found for '${uiState.searchQuery}'",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(results, key = { "${it.id}_${it.filePath}" }) { song ->
                        TvSongCard(
                            song = song,
                            onClick = { onPlaySong(song) },
                            cardWidth = 160.dp,
                            cardHeight = 210.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSourcePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SoftCyan else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    isFocused -> SoftCyan.copy(alpha = 0.35f)
                    isSelected -> SoftCyan.copy(alpha = 0.2f)
                    else -> Color(0x331E2338)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
