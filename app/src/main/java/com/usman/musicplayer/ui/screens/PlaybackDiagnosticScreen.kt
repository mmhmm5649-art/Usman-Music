package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@Composable
fun PlaybackDiagnosticScreen(
    uiState: MusicPlayerUiState,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Surface(modifier = Modifier.fillMaxSize().padding(16.dp), color = Color.Black) {
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            Text("Playback Diagnostic", color = Color.White, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Current Song: ${uiState.currentSong?.title ?: "None"}", color = Color.Gray)
            Text("isPlaying: ${uiState.isPlaying}", color = Color.Gray)
            Text("OnlineSearchError: ${uiState.onlineSearchError ?: "None"}", color = Color.Red)
            Text("Content URI: ${uiState.currentSong?.contentUriString ?: "null"}", color = Color.Gray)
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss) { Text("Close") }
        }
    }
}
