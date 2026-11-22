package com.usman.musicplayer.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.model.StreamingQualitySelector
import com.usman.musicplayer.ui.theme.NeonCyan
import com.usman.musicplayer.ui.theme.NeonPurple
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary

/**
 * Consistent three-dot vertical menu button (⋮) for song rows across Phone and Android TV.
 * Meets 48dp minimum touch target, supports TV D-pad focus highlight, and stops click
 * propagation so tapping options never triggers song playback.
 */
@Composable
fun SongRowMoreButton(
    songTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    tint: Color = TextSecondary
) {
    var isFocused by remember { mutableStateOf(false) }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(CircleShape)
            .background(if (isFocused) SoftCyan.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) SoftCyan else Color.Transparent,
                shape = CircleShape
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options for $songTitle",
            tint = if (isFocused) Color.White else tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Reusable song-specific options bottom sheet used consistently across Search, Home,
 * Albums, Artists, Playlists, Library, Queue, and Downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsBottomSheet(
    song: Song,
    playlists: List<Playlist>,
    isInListenTogetherRoom: Boolean,
    canControlListenTogether: Boolean,
    selectedStreamingQuality: StreamingQuality,
    activeStreamQualityLabel: String?,
    onDismiss: () -> Unit,
    onPlayNow: (Song) -> Unit,
    onPlayNext: (Song, (String) -> Unit) -> Unit,
    onAddToQueue: (Song, (String) -> Unit) -> Unit,
    onAddToPlaylist: (Long, Song, Boolean, (Boolean, String) -> Unit) -> Unit,
    onCreatePlaylist: (String, Song, (Boolean, String) -> Unit) -> Unit,
    onDownloadOffline: (Song) -> Unit,
    onOpenLyrics: (Song) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenListenTogether: () -> Unit,
    onAddToListenTogetherQueue: (Song) -> Unit,
    onPlayInListenTogetherRoom: (Song) -> Unit,
    onFeedbackMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF5080D1A),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("song_options_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Song Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongAlbumArtwork(
                    song = song,
                    modifier = Modifier.size(52.dp),
                    cornerRadius = 12.dp,
                    showGlowBorder = false
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album.ifBlank { "Single" }}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Play Now
            SongOptionActionRow(
                icon = Icons.Default.PlayArrow,
                title = "Play Now",
                tint = SoftCyan,
                testTag = "song_option_play_now",
                onClick = {
                    onDismiss()
                    onPlayNow(song)
                }
            )

            // 2. Play Next
            SongOptionActionRow(
                icon = Icons.Default.QueuePlayNext,
                title = "Play Next",
                tint = Color(0xFF38BDF8),
                testTag = "song_option_play_next",
                onClick = {
                    onDismiss()
                    onPlayNext(song, onFeedbackMessage)
                }
            )

            // 3. Add to Queue
            SongOptionActionRow(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Add to Queue",
                tint = Color(0xFF60A5FA),
                testTag = "song_option_add_to_queue",
                onClick = {
                    onDismiss()
                    onAddToQueue(song, onFeedbackMessage)
                }
            )

            // 4. Add to Playlist
            SongOptionActionRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Add to Playlist",
                tint = NeonPurple,
                testTag = "song_option_add_to_playlist",
                onClick = {
                    showPlaylistDialog = true
                }
            )

            // 5. Download Offline
            SongOptionActionRow(
                icon = Icons.Default.Download,
                title = if (song.isOnline) "Download Offline" else "Available Offline (Local File)",
                tint = Color(0xFF00E676),
                testTag = "song_option_download_offline",
                onClick = {
                    onDismiss()
                    if (song.isOnline) {
                        onDownloadOffline(song)
                        onFeedbackMessage("Started offline download for '${song.title}'")
                    } else {
                        onFeedbackMessage("'${song.title}' is already stored on your device")
                    }
                }
            )

            // 6. Synchronized Lyrics
            SongOptionActionRow(
                icon = Icons.Default.FormatQuote,
                title = "Synchronized Lyrics",
                tint = NeonCyan,
                testTag = "song_option_lyrics",
                onClick = {
                    onDismiss()
                    onOpenLyrics(song)
                }
            )

            // 7. Audio Effects / Equalizer
            SongOptionActionRow(
                icon = Icons.Default.Equalizer,
                title = "Audio Effects / Equalizer",
                tint = Color(0xFFA855F7),
                testTag = "song_option_equalizer",
                onClick = {
                    onDismiss()
                    onOpenEqualizer()
                }
            )

            // 8. Song Information
            SongOptionActionRow(
                icon = Icons.Default.Info,
                title = "Song Information",
                tint = Color(0xFF60A5FA),
                testTag = "song_option_info",
                onClick = {
                    showInfoDialog = true
                }
            )

            // 9. Share Song
            SongOptionActionRow(
                icon = Icons.Default.Share,
                title = "Share Song",
                tint = Color(0xFFF43F5E),
                testTag = "song_option_share",
                onClick = {
                    onDismiss()
                    val shareBody = buildString {
                        append("Listening to \"${song.title}\" by ${song.artist} on USMAN MUSIC")
                        if (!song.externalUrl.isNullOrBlank()) {
                            append("\n${song.externalUrl}")
                        }
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, shareBody)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                }
            )

            // 10. Listen Together
            val ltTitle = when {
                !isInListenTogetherRoom -> "Listen Together"
                canControlListenTogether -> "Listen Together • Add to Shared Queue"
                else -> "Listen Together • Open Room"
            }
            val ltSubtitle = when {
                !isInListenTogetherRoom -> "Create or join a synced room with friends"
                canControlListenTogether -> "Adds this full track to your active room's queue"
                else -> "Host Only mode is active in your current room"
            }
            SongOptionActionRow(
                icon = Icons.Rounded.Groups,
                title = ltTitle,
                subtitle = ltSubtitle,
                tint = SoftCyan,
                testTag = "song_option_listen_together",
                onClick = {
                    onDismiss()
                    if (!isInListenTogetherRoom) {
                        onOpenListenTogether()
                    } else if (canControlListenTogether) {
                        onAddToListenTogetherQueue(song)
                        onFeedbackMessage("Added '${song.title}' to Listen Together queue")
                    } else {
                        onOpenListenTogether()
                        onFeedbackMessage("Only the host can modify the queue in Host Only mode")
                    }
                }
            )

            if (isInListenTogetherRoom && canControlListenTogether && song.isOnline) {
                SongOptionActionRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Play Now in Listen Together Room",
                    tint = Color(0xFF34D399),
                    testTag = "song_option_listen_together_play_now",
                    onClick = {
                        onDismiss()
                        onPlayInListenTogetherRoom(song)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPlaylistDialog) {
        SongAddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { showPlaylistDialog = false },
            onSelectPlaylist = { playlistId ->
                onAddToPlaylist(playlistId, song, false) { _, msg ->
                    onFeedbackMessage(msg)
                }
                showPlaylistDialog = false
                onDismiss()
            },
            onCreatePlaylist = { newName ->
                onCreatePlaylist(newName, song) { _, msg ->
                    onFeedbackMessage(msg)
                }
                showPlaylistDialog = false
                onDismiss()
            }
        )
    }

    if (showInfoDialog) {
        SongInformationDialog(
            song = song,
            selectedStreamingQuality = selectedStreamingQuality,
            activeStreamQualityLabel = activeStreamQualityLabel,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
private fun SongOptionActionRow(
    icon: ImageVector,
    title: String,
    tint: Color,
    testTag: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) SoftCyan.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SongAddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F172A),
        title = {
            Text(
                text = "Add to Playlist",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = song.title,
                    color = SoftCyan,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (playlists.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        playlists.forEach { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { onSelectPlaylist(playlist.id) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = SoftCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.songCount} tracks",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(40) },
                    placeholder = { Text("Create new playlist...", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SoftCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onCreatePlaylist(newName.trim())
                    }
                },
                enabled = newName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SoftCyan, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create & Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun SongInformationDialog(
    song: Song,
    selectedStreamingQuality: StreamingQuality,
    activeStreamQualityLabel: String?,
    onDismiss: () -> Unit
) {
    val detectedBitrate = StreamingQualitySelector.detectBitrateFromUrl(song.contentUriString)
    val detectedCodec = StreamingQualitySelector.detectCodecFromUrl(song.contentUriString ?: song.filePath)
    val qualitySummary = when {
        !song.isOnline -> buildString {
            append("Local Audio File")
            if (!detectedCodec.isNullOrBlank()) append(" • $detectedCodec")
        }
        detectedBitrate != null -> buildString {
            append("$detectedBitrate kbps")
            if (!detectedCodec.isNullOrBlank()) append(" • $detectedCodec")
        }
        !activeStreamQualityLabel.isNullOrBlank() -> activeStreamQualityLabel
        else -> "${selectedStreamingQuality.displayName} (resolved at playback)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF20F172A),
        title = {
            Text("Song Information", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoField("Title", song.title)
                InfoField("Artist", song.artist)
                InfoField("Album", song.album.ifBlank { "Single" })
                InfoField("Duration", song.formattedDuration)
                InfoField("Source", if (song.isOnline) "Online Full-Length Stream" else "Device Storage")
                InfoField("Audio Quality", qualitySummary)
                if (!song.genre.isNullOrBlank()) {
                    InfoField("Genre", song.genre)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SoftCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun InfoField(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = TextPrimary, fontSize = 13.5.sp)
    }
}
