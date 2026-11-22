package com.usman.musicplayer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usman.musicplayer.data.listentogether.ListenTogetherConnectionStatus
import com.usman.musicplayer.data.listentogether.ListenTogetherControlMode
import com.usman.musicplayer.data.listentogether.ListenTogetherParticipant
import com.usman.musicplayer.data.listentogether.ListenTogetherQrCodec
import com.usman.musicplayer.data.listentogether.ListenTogetherRoomState
import com.usman.musicplayer.data.listentogether.ListenTogetherTrack
import com.usman.musicplayer.data.listentogether.ListenTogetherUiState
import com.usman.musicplayer.data.listentogether.ParticipantConnectionState
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.ui.components.SongAlbumArtwork
import com.usman.musicplayer.ui.theme.AmoledBlack
import com.usman.musicplayer.ui.theme.ElectricBlue
import com.usman.musicplayer.ui.theme.SoftCyan
import com.usman.musicplayer.ui.theme.TextMuted
import com.usman.musicplayer.ui.theme.TextPrimary
import com.usman.musicplayer.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(
    state: ListenTogetherUiState,
    playbackProgressFlow: StateFlow<PlaybackProgress> = remember { MutableStateFlow(PlaybackProgress()) },
    onBack: () -> Unit,
    onCreateRoom: (displayName: String, controlMode: ListenTogetherControlMode) -> Unit,
    onJoinRoom: (roomCodeOrInvite: String, displayName: String) -> Unit,
    onLeaveRoom: () -> Unit,
    onEndRoom: () -> Unit = onLeaveRoom,
    onSetControlMode: (ListenTogetherControlMode) -> Unit,
    onSetQuality: (StreamingQuality) -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onUpdateServerUrl: (String) -> Result<String>,
    onVerifyServerHealth: suspend (String) -> Result<String> = { Result.success("Endpoint valid") },
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRequestResync: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onAddTrackToQueue: (Song, Boolean) -> Unit,
    onPlayTrackNow: (Song) -> Unit,
    onSearchSongs: suspend (String) -> List<Song>,
    onClearError: () -> Unit = {},
    onClearBanner: () -> Unit = {},
    onClearPendingDeepLink: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val playbackProgress by playbackProgressFlow.collectAsStateWithLifecycle()

    var displayNameInput by remember(state.displayName) { mutableStateOf(state.displayName) }
    var roomCodeInput by remember { mutableStateOf("") }
    var selectedInitialControlMode by remember { mutableStateOf(ListenTogetherControlMode.HOST_ONLY) }
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var showAddSongSheet by remember { mutableStateOf(false) }
    var showEndRoomConfirmDialog by remember { mutableStateOf(false) }
    var localToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.pendingDeepLinkRoomCode) {
        val code = state.pendingDeepLinkRoomCode
        if (!code.isNullOrBlank()) {
            roomCodeInput = code
            onClearPendingDeepLink()
        }
    }

    LaunchedEffect(localToastMessage) {
        if (localToastMessage != null) {
            delay(2500L)
            localToastMessage = null
        }
    }

    LaunchedEffect(state.infoBanner) {
        if (state.infoBanner != null) {
            delay(3500L)
            onClearBanner()
        }
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF071526),
                Color(0xFF050A14),
                AmoledBlack
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("listen_together_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("listen_together_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listen Together",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    ConnectionStatusLine(
                        status = state.connectionStatus,
                        isServerConfigured = state.isServerConfigured,
                        rttMs = state.diagnostics.estimatedRttMs
                    )
                }

                IconButton(
                    onClick = { showServerSettingsDialog = true },
                    modifier = Modifier.testTag("listen_together_server_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = "Server Settings",
                        tint = if (state.isServerConfigured) SoftCyan else Color(0xFFFBBF24)
                    )
                }
            }

            // Banners & Errors
            val bannerText = localToastMessage ?: state.infoBanner
            if (!bannerText.isNullOrBlank()) {
                StatusBannerCard(
                    text = bannerText,
                    isError = false,
                    onDismiss = {
                        localToastMessage = null
                        onClearBanner()
                    }
                )
            }
            if (!state.errorMessage.isNullOrBlank()) {
                StatusBannerCard(
                    text = state.errorMessage,
                    isError = true,
                    onDismiss = onClearError
                )
            }

            // Main Content
            val activeRoom = state.room
            if (activeRoom == null) {
                ListenTogetherLobbyContent(
                    state = state,
                    displayNameInput = displayNameInput,
                    onDisplayNameChange = { displayNameInput = it },
                    roomCodeInput = roomCodeInput,
                    onRoomCodeChange = { roomCodeInput = ListenTogetherQrCodec.normalizeRoomCode(it) },
                    selectedInitialControlMode = selectedInitialControlMode,
                    onSelectInitialControlMode = { selectedInitialControlMode = it },
                    onOpenServerSettings = { showServerSettingsDialog = true },
                    onCreateRoom = {
                        onUpdateDisplayName(displayNameInput)
                        if (!state.isServerConfigured) {
                            showServerSettingsDialog = true
                        } else {
                            onCreateRoom(displayNameInput, selectedInitialControlMode)
                        }
                    },
                    onJoinRoom = {
                        onUpdateDisplayName(displayNameInput)
                        if (!state.isServerConfigured) {
                            showServerSettingsDialog = true
                        } else {
                            onJoinRoom(roomCodeInput, displayNameInput)
                        }
                    },
                    onScanQrClick = { showQrScannerDialog = true },
                    onSelectQuality = onSetQuality
                )
            } else {
                ListenTogetherActiveRoomContent(
                    state = state,
                    room = activeRoom,
                    playbackProgress = playbackProgress,
                    onCopyCode = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Room Code", activeRoom.roomCode))
                        localToastMessage = "Room code ${activeRoom.roomCode} copied"
                    },
                    onShowQr = { showQrCodeDialog = true },
                    onShareRoom = {
                        val shareText = ListenTogetherQrCodec.buildShareMessage(
                            roomCode = activeRoom.roomCode,
                            trackTitle = activeRoom.currentTrack?.title,
                            serverUrl = state.serverUrl.takeIf { it.isNotBlank() }
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "USMAN MUSIC Listen Together Room")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Listen Together Room"))
                    },
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onSeekTo = onSeekTo,
                    onRequestResync = onRequestResync,
                    onSelectQuality = onSetQuality,
                    onSelectControlMode = onSetControlMode,
                    onOpenAddSong = { showAddSongSheet = true },
                    onPlayQueueIndex = onPlayQueueIndex,
                    onRemoveQueueIndex = onRemoveQueueIndex,
                    onLeaveRoom = onLeaveRoom,
                    onEndRoom = { showEndRoomConfirmDialog = true }
                )
            }
        }
    }

    if (showEndRoomConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndRoomConfirmDialog = false },
            containerColor = Color(0xFF0E1627),
            title = {
                Text("End Listening Room?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will close the room for all connected participants. To keep the room running for others, tap Leave Room instead.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndRoomConfirmDialog = false
                        onEndRoom()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    modifier = Modifier.testTag("listen_together_confirm_end_room_button")
                ) {
                    Text("End Room", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndRoomConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Show Room QR Code Dialog
    val roomForQr = state.room
    if (showQrCodeDialog && roomForQr != null) {
        RoomQrCodeDialog(
            roomCode = roomForQr.roomCode,
            serverUrl = state.serverUrl.takeIf { it.isNotBlank() },
            onDismiss = { showQrCodeDialog = false }
        )
    }

    // Live CameraX + Gallery QR Code Scanner Dialog
    if (showQrScannerDialog) {
        ListenTogetherQrScannerDialog(
            onDismiss = { showQrScannerDialog = false },
            onQrPayloadScanned = { payload ->
                showQrScannerDialog = false
                val parsed = ListenTogetherQrCodec.parseInvitePayload(payload)
                if (parsed != null) {
                    roomCodeInput = parsed.roomCode
                    onUpdateDisplayName(displayNameInput)
                    onJoinRoom(payload, displayNameInput)
                }
            }
        )
    }

    // Server Endpoint Settings Dialog
    if (showServerSettingsDialog) {
        ServerEndpointDialog(
            currentUrl = state.serverUrl,
            onDismiss = { showServerSettingsDialog = false },
            onVerifyHealth = onVerifyServerHealth,
            onSave = { newUrl ->
                val result = onUpdateServerUrl(newUrl)
                if (result.isSuccess) {
                    showServerSettingsDialog = false
                }
            }
        )
    }

    // Add Song to Shared Queue Search Sheet
    if (showAddSongSheet && state.room != null) {
        ModalBottomSheet(
            onDismissRequest = { showAddSongSheet = false },
            containerColor = Color(0xF5081120),
            scrimColor = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            AddSongToRoomSheetContent(
                onSearchSongs = onSearchSongs,
                onPlayNow = { song ->
                    onPlayTrackNow(song)
                    showAddSongSheet = false
                },
                onAddToQueue = { song ->
                    onAddTrackToQueue(song, state.room.currentTrack == null)
                    localToastMessage = "Queued '${song.title}'"
                },
                onClose = { showAddSongSheet = false }
            )
        }
    }
}

@Composable
private fun ConnectionStatusLine(
    status: ListenTogetherConnectionStatus,
    isServerConfigured: Boolean,
    rttMs: Long
) {
    val (label, color) = when {
        !isServerConfigured -> "Server URL needed" to Color(0xFFFBBF24)
        status == ListenTogetherConnectionStatus.CONNECTED -> {
            val rttSuffix = if (rttMs > 0L) " • ${rttMs}ms ping" else ""
            "Connected$rttSuffix" to Color(0xFF4ADE80)
        }
        status == ListenTogetherConnectionStatus.CONNECTING -> "Connecting…" to SoftCyan
        status == ListenTogetherConnectionStatus.RECONNECTING -> "Reconnecting…" to Color(0xFFFBBF24)
        status == ListenTogetherConnectionStatus.ERROR -> "Connection issue" to Color(0xFFF87171)
        else -> "Ready to sync" to TextSecondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusBannerCard(
    text: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val bg = if (isError) Color(0xFF3F1218) else Color(0xFF0A2A3B)
    val border = if (isError) Color(0xFFF87171).copy(alpha = 0.55f) else SoftCyan.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = if (isError) Color(0xFFFECACA) else Color(0xFFCCFBF1),
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss message",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ListenTogetherLobbyContent(
    state: ListenTogetherUiState,
    displayNameInput: String,
    onDisplayNameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    selectedInitialControlMode: ListenTogetherControlMode,
    onSelectInitialControlMode: (ListenTogetherControlMode) -> Unit,
    onOpenServerSettings: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onScanQrClick: () -> Unit,
    onSelectQuality: (StreamingQuality) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("listen_together_lobby_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!state.isServerConfigured) {
            item(key = "server_setup_notice") {
                GlassSectionCard(borderColor = Color(0xFFFBBF24).copy(alpha = 0.55f)) {
                    Text(
                        text = "Configure Sync Server",
                        color = Color(0xFFFDE68A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect two phones by entering your deployed Listen Together WebSocket URL (wss://.../ws) or local Wi-Fi server address.",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenServerSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color.Black),
                        modifier = Modifier.testTag("listen_together_configure_server_button")
                    ) {
                        Text("Set Server URL", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Display Name
        item(key = "display_name_card") {
            GlassSectionCard {
                Text(
                    text = "Your Display Name",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { onDisplayNameChange(it.take(32)) },
                    placeholder = { Text("e.g., Usman's Phone", color = TextMuted) },
                    singleLine = true,
                    colors = listenTogetherTextFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listen_together_display_name_input")
                )
            }
        }

        // Create Room Card
        item(key = "create_room_card") {
            GlassSectionCard(borderColor = SoftCyan.copy(alpha = 0.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = null,
                        tint = SoftCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Host a Listening Room",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Generate a unique 6-character room code and QR code. Both phones stream full tracks in sync.",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                ControlModeSelectorRow(
                    selected = selectedInitialControlMode,
                    enabled = true,
                    onSelect = onSelectInitialControlMode
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onCreateRoom,
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = SoftCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("listen_together_create_room_button")
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Create Room", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }

        // Join Room Card
        item(key = "join_room_card") {
            GlassSectionCard {
                Text(
                    text = "Join a Friend's Room",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter the 6-character code or scan the QR code from the host phone.",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = onRoomCodeChange,
                    placeholder = { Text("Enter 6-character code (e.g. A7K9P2)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { onJoinRoom() }),
                    colors = listenTogetherTextFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listen_together_code_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onJoinRoom,
                        enabled = !state.isBusy && roomCodeInput.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("listen_together_join_room_button")
                    ) {
                        Text("Join Room", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onScanQrClick,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("listen_together_scan_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = SoftCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan QR", color = TextPrimary)
                    }
                }
            }
        }

        // Streaming Quality Preference Card
        item(key = "lobby_quality_card") {
            StreamingQualityCard(
                selectedQuality = state.selectedQuality,
                activeLabel = state.activeStreamQualityLabel,
                isSwitching = state.isQualitySwitching,
                onSelectQuality = onSelectQuality
            )
        }
    }
}

@Composable
private fun ListenTogetherActiveRoomContent(
    state: ListenTogetherUiState,
    room: ListenTogetherRoomState,
    playbackProgress: PlaybackProgress,
    onCopyCode: () -> Unit,
    onShowQr: () -> Unit,
    onShareRoom: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRequestResync: () -> Unit,
    onSelectQuality: (StreamingQuality) -> Unit,
    onSelectControlMode: (ListenTogetherControlMode) -> Unit,
    onOpenAddSong: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onLeaveRoom: () -> Unit,
    onEndRoom: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("listen_together_active_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Room Code & Invite Card
        item(key = "active_room_code_card") {
            GlassSectionCard(borderColor = SoftCyan.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (state.isHost) "ROOM CODE • YOU ARE HOST" else "ROOM CODE • LISTENING IN SYNC",
                            color = SoftCyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = room.roomCode.chunked(3).joinToString(" "),
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("listen_together_room_code_text")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onCopyCode,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .testTag("listen_together_copy_code_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Code", tint = SoftCyan)
                        }
                        IconButton(
                            onClick = onShowQr,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .testTag("listen_together_show_qr_button")
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = "Show QR Code", tint = SoftCyan)
                        }
                        IconButton(
                            onClick = onShareRoom,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SoftCyan.copy(alpha = 0.22f))
                                .testTag("listen_together_share_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share Room", tint = SoftCyan)
                        }
                    }
                }
            }
        }

        // Synchronized Now Playing Card
        item(key = "active_now_playing_card") {
            val diag = state.diagnostics
            val telemetryText = buildString {
                append(diag.syncStatusLabel)
                if (diag.isClockSynced) {
                    append(" • Drift ${diag.lastMeasuredDriftMs}ms")
                }
            }
            SynchronizedPlayerCard(
                room = room,
                canControl = state.canControlPlayback,
                diagnosticsLabel = telemetryText,
                playbackProgress = playbackProgress,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onSeekTo = onSeekTo,
                onRequestResync = onRequestResync,
                onOpenAddSong = onOpenAddSong
            )
        }

        // Independent Per-Device Streaming Quality Card
        item(key = "active_quality_card") {
            StreamingQualityCard(
                selectedQuality = state.selectedQuality,
                activeLabel = state.activeStreamQualityLabel,
                isSwitching = state.isQualitySwitching,
                onSelectQuality = onSelectQuality
            )
        }

        // Control Permissions Card
        item(key = "active_control_mode_card") {
            GlassSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Playback Controls",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.isHost) "Host setting" else "Set by host",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                ControlModeSelectorRow(
                    selected = room.controlMode,
                    enabled = state.isHost,
                    onSelect = onSelectControlMode
                )
            }
        }

        // Participants List Card
        item(key = "active_participants_card") {
            GlassSectionCard {
                Text(
                    text = "Participants (${room.participants.size})",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    room.participants.forEach { participant ->
                        ParticipantRow(
                            participant = participant,
                            isMe = participant.participantId == state.participantId
                        )
                    }
                }
            }
        }

        // Shared Queue Header + Tracks
        item(key = "shared_queue_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = SoftCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shared Queue (${room.queue.size})",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.canControlPlayback) {
                    Button(
                        onClick = onOpenAddSong,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftCyan.copy(alpha = 0.22f), contentColor = SoftCyan),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("listen_together_add_song_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Song", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (room.queue.isEmpty()) {
            item(key = "empty_queue_card") {
                GlassSectionCard {
                    Text(
                        text = "No songs in the shared queue yet. Tap Add Song to pick a full-length track and start listening together.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            itemsIndexed(
                items = room.queue,
                key = { idx, track -> "${track.trackId}_$idx" }
            ) { index, track ->
                SharedQueueTrackRow(
                    track = track,
                    isCurrent = index == room.currentIndex,
                    canControl = state.canControlPlayback,
                    onClick = { if (state.canControlPlayback) onPlayQueueIndex(index) },
                    onRemove = { onRemoveQueueIndex(index) }
                )
            }
        }

        // Leave Room / End Room Actions
        item(key = "leave_room_footer") {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onLeaveRoom,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("listen_together_leave_room_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Leave Room", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }

                if (state.isHost) {
                    Button(
                        onClick = onEndRoom,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33EF4444),
                            contentColor = Color(0xFFFCA5A5)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("listen_together_end_room_button")
                    ) {
                        Text("End Room", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SynchronizedPlayerCard(
    room: ListenTogetherRoomState,
    canControl: Boolean,
    diagnosticsLabel: String,
    playbackProgress: PlaybackProgress,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRequestResync: () -> Unit,
    onOpenAddSong: () -> Unit
) {
    val track = room.currentTrack
    GlassSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = diagnosticsLabel,
                    color = Color(0xFF86EFAC),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(
                onClick = onRequestResync,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("listen_together_resync_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Resync playback",
                    tint = SoftCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (track == null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Nothing playing yet",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (canControl) "Pick a song to start synchronized playback for everyone in this room."
                else "Waiting for the host to start a song…",
                color = TextSecondary,
                fontSize = 12.5.sp
            )
            if (canControl) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAddSong,
                    colors = ButtonDefaults.buttonColors(containerColor = SoftCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Choose a Song", fontWeight = FontWeight.Bold)
                }
            }
            return@GlassSectionCard
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SongAlbumArtwork(
                song = track.toSong(),
                cornerRadius = 14.dp,
                showGlowBorder = false,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val durationMs = playbackProgress.durationMs.takeIf { it > 0L }
            ?: track.durationMs.coerceAtLeast(1L)
        val livePosMs = playbackProgress.currentPositionMs.coerceIn(0L, durationMs)
        var isDragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableFloatStateOf(livePosMs.toFloat()) }
        val displayedPos = if (isDragging) dragPosition else livePosMs.toFloat()

        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = displayedPos.coerceIn(0f, durationMs.toFloat()),
            onValueChange = {
                if (canControl) {
                    isDragging = true
                    dragPosition = it
                }
            },
            onValueChangeFinished = {
                if (canControl && isDragging) {
                    isDragging = false
                    onSeekTo(dragPosition.toLong())
                }
            },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            enabled = canControl,
            colors = SliderDefaults.colors(
                thumbColor = SoftCyan,
                activeTrackColor = SoftCyan,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTimeMs(displayedPos.toLong()), color = TextMuted, fontSize = 11.sp)
            Text(formatTimeMs(durationMs), color = TextMuted, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSkipPrevious,
                enabled = canControl,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("listen_together_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous track",
                    tint = if (canControl) TextPrimary else TextMuted,
                    modifier = Modifier.size(30.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(if (canControl) SoftCyan else Color.White.copy(alpha = 0.14f))
                    .clickable(enabled = canControl, onClick = onTogglePlayPause)
                    .testTag("listen_together_play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (room.playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (room.playback.isPlaying) "Pause" else "Play",
                    tint = if (canControl) Color.Black else TextMuted,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onSkipNext,
                enabled = canControl,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("listen_together_next_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next track",
                    tint = if (canControl) TextPrimary else TextMuted,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (!canControl) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Host Controls Only is active — playback follows the host automatically.",
                color = TextMuted,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StreamingQualityCard(
    selectedQuality: StreamingQuality,
    activeLabel: String,
    isSwitching: Boolean,
    onSelectQuality: (StreamingQuality) -> Unit
) {
    GlassSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.HighQuality,
                    contentDescription = null,
                    tint = SoftCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your Streaming Quality",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isSwitching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SoftCyan)
            } else {
                Text(
                    text = activeLabel,
                    color = SoftCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("listen_together_active_quality_label")
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Independent per device — changing quality only affects your phone while keeping sync.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreamingQuality.entries.forEach { quality ->
                val isSelected = quality == selectedQuality
                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(
                            if (isSelected) SoftCyan.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) SoftCyan else Color.White.copy(alpha = 0.12f),
                            shape = shape
                        )
                        .clickable { onSelectQuality(quality) }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("quality_option_${quality.key}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = quality.displayName,
                            color = if (isSelected) SoftCyan else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${quality.targetBitrateKbps} kbps",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlModeSelectorRow(
    selected: ListenTogetherControlMode,
    enabled: Boolean,
    onSelect: (ListenTogetherControlMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ListenTogetherControlMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val shape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) ElectricBlue.copy(alpha = 0.26f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) SoftCyan else Color.White.copy(alpha = 0.12f),
                        shape = shape
                    )
                    .clickable(enabled = enabled) { onSelect(mode) }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("control_mode_${mode.wireValue.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (mode == ListenTogetherControlMode.HOST_ONLY) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = if (isSelected) SoftCyan else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (mode == ListenTogetherControlMode.HOST_ONLY) "Host Only" else "Everyone",
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: ListenTogetherParticipant,
    isMe: Boolean
) {
    val isConnected = participant.connectionState == ParticipantConnectionState.CONNECTED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SoftCyan.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (participant.isHost) Icons.Rounded.Star else Icons.Rounded.Person,
                contentDescription = null,
                tint = if (participant.isHost) Color(0xFFFACC15) else SoftCyan,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isMe) "${participant.displayName} (You)" else participant.displayName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (participant.isHost) {
                    Text(
                        text = "HOST",
                        color = Color(0xFFFACC15),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFACC15).copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = if (isConnected) "Connected" else "Reconnecting…",
                color = if (isConnected) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                fontSize = 11.5.sp
            )
        }

        Text(
            text = participant.qualityLabel,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SharedQueueTrackRow(
    track: ListenTogetherTrack,
    isCurrent: Boolean,
    canControl: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) SoftCyan.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = if (isCurrent) SoftCyan.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = canControl, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongAlbumArtwork(
            song = track.toSong(),
            cornerRadius = 10.dp,
            showGlowBorder = false,
            modifier = Modifier.size(46.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) SoftCyan else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canControl) {
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove from shared queue",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RoomQrCodeDialog(
    roomCode: String,
    serverUrl: String?,
    onDismiss: () -> Unit
) {
    val qrPayload = remember(roomCode, serverUrl) {
        ListenTogetherQrCodec.buildInviteUri(roomCode, serverUrl)
    }
    val qrBitmap = remember(qrPayload) {
        ListenTogetherQrCodec.generateQrBitmap(qrPayload, sizePx = 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A1424),
        title = {
            Text(
                text = "Scan to Join Room $roomCode",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code for room $roomCode",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Point Phone B's Listen Together scanner at this QR code to join in sync.",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )
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
private fun ServerEndpointDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onVerifyHealth: suspend (String) -> Result<String>,
    onSave: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testPassed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A1424),
        title = {
            Text(
                text = "Listen Together Server",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter your deployed Listen Together WebSocket URL (use wss:// for internet hosts like Render, Railway, Fly.io, or Cloud Run; or ws://192.168.x.x:8080/ws on local Wi-Fi).",
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        testResultText = null
                    },
                    placeholder = { Text("wss://your-server.onrender.com/ws", color = TextMuted) },
                    singleLine = true,
                    colors = listenTogetherTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listen_together_server_url_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testResultText = null
                                val res = onVerifyHealth(urlInput)
                                isTesting = false
                                testPassed = res.isSuccess
                                testResultText = res.fold(
                                    onSuccess = { it },
                                    onFailure = { it.message ?: "Unable to reach server" }
                                )
                            }
                        },
                        enabled = !isTesting && urlInput.isNotBlank(),
                        modifier = Modifier.testTag("listen_together_test_server_button")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SoftCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test Connection", color = SoftCyan, fontSize = 12.sp)
                    }
                }

                if (!testResultText.isNullOrBlank()) {
                    Text(
                        text = testResultText!!,
                        color = if (testPassed) Color(0xFF4ADE80) else Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(urlInput) },
                colors = ButtonDefaults.buttonColors(containerColor = SoftCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("listen_together_save_server_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AddSongToRoomSheetContent(
    onSearchSongs: suspend (String) -> List<Song>,
    onPlayNow: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    fun triggerSearch(q: String) {
        if (q.trim().length < 2) {
            results = emptyList()
            return
        }
        scope.launch {
            isSearching = true
            results = runCatching { onSearchSongs(q) }.getOrDefault(emptyList())
            isSearching = false
        }
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300L)
        triggerSearch(query)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .heightIn(min = 320.dp, max = 520.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Add Full Track to Room",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs, artists, or movies…", color = TextMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SoftCyan) },
            singleLine = true,
            colors = listenTogetherTextFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("listen_together_song_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SoftCyan, strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(results, key = { idx, song -> "${song.id}_$idx" }) { _, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable { onPlayNow(song) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongAlbumArtwork(
                            song = song,
                            cornerRadius = 8.dp,
                            showGlowBorder = false,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onAddToQueue(song) }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add to shared queue",
                                tint = SoftCyan
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun GlassSectionCard(
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xB80A1526))
            .border(1.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun listenTogetherTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = SoftCyan,
    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
    cursorColor = SoftCyan,
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
)

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format("%d:%02d", mins, secs)
}
