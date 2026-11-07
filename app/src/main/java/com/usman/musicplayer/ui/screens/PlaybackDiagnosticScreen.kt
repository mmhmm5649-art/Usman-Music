package com.usman.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.playback.PlaybackErrorRepository
import com.usman.musicplayer.viewmodel.MusicPlayerUiState

@Composable
fun PlaybackDiagnosticScreen(
    onDismiss: () -> Unit
) {
    val logs by PlaybackErrorRepository.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Playback Diagnostic Logs", color = Color.White, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { 
                    clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                }) { Text("Copy Logs") }
                Button(onClick = { PlaybackErrorRepository.clearError() }) { Text("Clear") }
                Button(onClick = onDismiss) { Text("Close") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logs) { log ->
                    Text(log, color = Color.LightGray, fontSize = 11.sp)
                }
            }
        }
    }
}
