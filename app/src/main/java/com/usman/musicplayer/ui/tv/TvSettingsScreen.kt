package com.usman.musicplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel

@Composable
fun TvSettingsScreen(
    uiState: MusicPlayerUiState,
    viewModel: MusicPlayerViewModel,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Device Configuration",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Optimized for Android TV & Google TV remote navigation",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Section: Streaming & Audio Quality
        item {
            TvSettingsCard(
                title = "Streaming Audio Quality",
                subtitle = "Active: 320 KBPS AAC HD (Highest Fidelity)",
                icon = Icons.Default.Speed,
                actionText = "Configured",
                onClick = {}
            )
        }

        // Section: Rescan Local Storage
        item {
            TvSettingsCard(
                title = "Rescan Local Media Library",
                subtitle = "Detect newly added MP3 audio files on USB or internal storage",
                icon = Icons.Default.Refresh,
                actionText = "Rescan Now",
                onClick = { viewModel.scanMusicLibrary() }
            )
        }

        // Section: Playback Diagnostics
        item {
            TvSettingsCard(
                title = "Playback Diagnostics & System Logs",
                subtitle = "View real-time Media3 states, JioSaavn resolution, and error traces",
                icon = Icons.Default.BugReport,
                actionText = "Open Logs",
                onClick = onOpenDiagnostics
            )
        }

        // Section: Device & App Information Card
        item {
            TvSystemInfoCard()
        }
    }
}

@Composable
private fun TvSettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionText: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) TvFocusCyan else Color(0x22FFFFFF),
                shape = shape
            )
            .clip(shape)
            .background(if (isFocused) Color(0xFF0F2642) else Color(0xFF0F172A))
            .clickable(onClick = onClick)
            .focusable()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isFocused) Color(0xFF00E5FF) else Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else TvFocusCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isFocused) TvFocusCyan else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) TvFocusCyan else Color(0x3300E5FF))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionText,
                color = if (isFocused) Color.Black else Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvSystemInfoCard() {
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x22FFFFFF), shape)
            .clip(shape)
            .background(Color(0xFF0A101D))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = TvFocusCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "System & Hardware Profile",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoColumn("Application", "USMAN Music Player v1.0")
            InfoColumn("Target Platform", "Android TV / Google TV / Phones")
            InfoColumn("Playback Engine", "Media3 ExoPlayer 1.x")
            InfoColumn("Online Provider", "JioSaavn Native (320kbps)")
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column {
        Text(text = label, color = Color(0xFF64748B), fontSize = 11.sp)
        Text(text = value, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
