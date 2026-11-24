package com.usman.musicplayer.data.listentogether

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.usman.musicplayer.BuildConfig
import com.usman.musicplayer.data.repository.UnifiedMusicRepository
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.playback.PcmSpeakerChannelRole
import com.usman.musicplayer.playback.StereoSplitAudioController
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates real-time WebSocket room synchronization, NTP clock offset tracking,
 * latency-aware playback scheduling, drift correction, independent per-device
 * streaming quality resolution, and Media3 playback bridge integration.
 */
class ListenTogetherCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: UnifiedMusicRepository,
    private val playbackBridge: PlaybackBridge,
    private val wsClient: ListenTogetherWebSocketClient = ListenTogetherWebSocketClient(scope),
    private val clockSyncEngine: ClockSyncEngine = ClockSyncEngine(),
    private val driftController: PlaybackDriftController = PlaybackDriftController()
) {
    interface PlaybackBridge {
        fun currentPositionMs(): Long
        fun isPlaying(): Boolean
        fun currentSong(): Song?
        fun loadAndCueTrack(
            song: Song,
            resolvedStreamUrl: String,
            positionMs: Long,
            playImmediately: Boolean
        )
        fun play()
        fun pause()
        fun seekTo(positionMs: Long)
        fun setPlaybackSpeed(speed: Float)
        fun syncSharedQueueDisplay(songs: List<Song>, currentIndex: Int)
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(loadInitialUiState())
    val uiState: StateFlow<ListenTogetherUiState> = _uiState.asStateFlow()

    val isInRoom: Boolean
        get() = _uiState.value.isInRoom

    val canControlPlayback: Boolean
        get() = _uiState.value.canControlPlayback

    @Volatile
    var isApplyingRemoteState: Boolean = false
        private set

    // Cache resolved (trackId, quality) -> (Song, url, actualLabel) for instant quality/re-cue switches
    private data class ResolvedTrackEntry(
        val song: Song,
        val url: String,
        val actualLabel: String
    )
    private val resolvedTrackCache = ConcurrentHashMap<String, ResolvedTrackEntry>()

    private var loadedTrackId: String? = null
    private var loadedQuality: StreamingQuality? = null
    private var awaitingBufferReadyForTrackId: String? = null
    private var scheduledStartJob: Job? = null
    private var trackLoadJob: Job? = null
    private var clockPingLoopJob: Job? = null
    private var driftMonitorJob: Job? = null
    private var lastAppliedRevision: Long = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        observeWebSocket()
        observeStereoSplitAudioTelemetry()
        registerNetworkObserver()
    }

    private fun loadInitialUiState(): ListenTogetherUiState {
        val savedServerUrl = prefs.getString(KEY_SERVER_URL, null)?.trim().orEmpty()
        val buildConfigUrl = BuildConfig.LISTEN_TOGETHER_WS_URL.trim()
        val resolvedServerUrl = savedServerUrl
            .ifBlank { buildConfigUrl }
            .ifBlank { DEFAULT_DEPLOYED_WS_URL }

        val savedName = prefs.getString(KEY_DISPLAY_NAME, null)?.trim().orEmpty()
        val defaultDeviceName = savedName.ifBlank {
            Build.MODEL?.trim()?.take(24).takeIf { !it.isNullOrBlank() } ?: "Listener"
        }
        val savedQuality = StreamingQuality.fromKey(prefs.getString(KEY_STREAMING_QUALITY, null))

        return ListenTogetherUiState(
            serverUrl = resolvedServerUrl,
            isServerConfigured = resolvedServerUrl.isNotBlank(),
            displayName = defaultDeviceName,
            selectedQuality = savedQuality,
            activeStreamQualityLabel = savedQuality.shortLabel
        )
    }

    private fun observeWebSocket() {
        scope.launch {
            wsClient.connectionStatus.collect { status ->
                _uiState.update { state ->
                    state.copy(
                        connectionStatus = status,
                        isBusy = if (status == ListenTogetherConnectionStatus.ERROR || status == ListenTogetherConnectionStatus.DISCONNECTED) {
                            false
                        } else {
                            state.isBusy
                        }
                    )
                }
            }
        }

        scope.launch {
            wsClient.events.collect { event ->
                handleServerEvent(event)
            }
        }
    }

    private fun observeStereoSplitAudioTelemetry() {
        scope.launch {
            StereoSplitAudioController.telemetry.collect { telemetry ->
                _uiState.update { state ->
                    val isSplit = state.isStereoSplitMode
                    val notice = if (isSplit && telemetry.isMonoSource) {
                        "Current track is mono (1 channel) — meaningful Left/Right stereo separation is not available for mono audio."
                    } else {
                        null
                    }
                    state.copy(
                        isCurrentTrackMono = telemetry.isMonoSource,
                        stereoSplitNotice = notice
                    )
                }
            }
        }
    }

    fun updateDisplayName(name: String) {
        val cleaned = name.trim().take(32).ifBlank { "Listener" }
        prefs.edit().putString(KEY_DISPLAY_NAME, cleaned).apply()
        _uiState.update { it.copy(displayName = cleaned) }
    }

    fun updateServerUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            prefs.edit().remove(KEY_SERVER_URL).apply()
            val fallback = BuildConfig.LISTEN_TOGETHER_WS_URL.trim()
            _uiState.update {
                it.copy(
                    serverUrl = fallback,
                    isServerConfigured = fallback.isNotBlank(),
                    errorMessage = null
                )
            }
            return Result.success(fallback)
        }
        val validation = wsClient.validateEndpointUrl(trimmed)
        return validation.onSuccess { normalized ->
            prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
            _uiState.update {
                it.copy(
                    serverUrl = normalized,
                    isServerConfigured = true,
                    errorMessage = null,
                    infoBanner = "Server endpoint updated"
                )
            }
        }.onFailure { err ->
            _uiState.update { it.copy(errorMessage = err.message) }
        }
    }

    fun consumeDeepLinkInvite(rawPayload: String?) {
        val parsed = ListenTogetherQrCodec.parseInvitePayload(rawPayload) ?: return
        if (!parsed.serverUrl.isNullOrBlank()) {
            wsClient.validateEndpointUrl(parsed.serverUrl).onSuccess { validUrl ->
                prefs.edit().putString(KEY_SERVER_URL, validUrl).apply()
                _uiState.update { it.copy(serverUrl = validUrl, isServerConfigured = true) }
            }
        }
        _uiState.update { it.copy(pendingDeepLinkRoomCode = parsed.roomCode) }
    }

    fun clearPendingDeepLinkCode() {
        _uiState.update { it.copy(pendingDeepLinkRoomCode = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearInfoBanner() {
        _uiState.update { it.copy(infoBanner = null) }
    }

    fun createRoom(
        displayNameOverride: String? = null,
        controlMode: ListenTogetherControlMode = ListenTogetherControlMode.HOST_ONLY,
        initialSong: Song? = playbackBridge.currentSong(),
        initialQueueSongs: List<Song> = emptyList(),
        initialPositionMs: Long = playbackBridge.currentPositionMs(),
        initialIsPlaying: Boolean = playbackBridge.isPlaying()
    ) {
        val state = _uiState.value
        val name = (displayNameOverride ?: state.displayName).trim().take(32).ifBlank { "Listener" }
        updateDisplayName(name)

        val serverUrl = state.serverUrl
        val canonicalInitialTrack = initialSong?.let { ListenTogetherTrack.fromSong(it, addedByName = name) }
        val canonicalQueue = initialQueueSongs
            .mapNotNull { ListenTogetherTrack.fromSong(it, addedByName = name) }
            .distinctBy { it.trackId }
            .take(50)
        val effectiveQueue = when {
            canonicalQueue.isNotEmpty() -> canonicalQueue
            canonicalInitialTrack != null -> listOf(canonicalInitialTrack)
            else -> emptyList()
        }
        val initialIdx = if (canonicalInitialTrack != null) {
            effectiveQueue.indexOfFirst { it.trackId == canonicalInitialTrack.trackId }.coerceAtLeast(0)
        } else if (effectiveQueue.isNotEmpty()) {
            0
        } else {
            -1
        }

        val payload = ListenTogetherWebSocketClient.buildCreateRoomPayload(
            displayName = name,
            qualityLabel = state.selectedQuality.shortLabel,
            controlMode = controlMode,
            initialTrack = canonicalInitialTrack,
            initialQueue = effectiveQueue,
            initialIndex = initialIdx,
            initialPositionMs = if (canonicalInitialTrack != null) initialPositionMs else 0L,
            initialIsPlaying = canonicalInitialTrack != null && initialIsPlaying
        )

        _uiState.update { it.copy(isBusy = true, errorMessage = null, infoBanner = null) }
        val result = wsClient.connectAndSend(serverUrl, payload.toString())
        if (result.isFailure) {
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Unable to connect to server"
                )
            }
        }
    }

    fun joinRoom(roomCodeRaw: String, displayNameOverride: String? = null) {
        val parsed = ListenTogetherQrCodec.parseInvitePayload(roomCodeRaw)
        val cleanCode = parsed?.roomCode ?: ListenTogetherQrCodec.normalizeRoomCode(roomCodeRaw)
        if (!ListenTogetherQrCodec.isValidRoomCode(cleanCode)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 6-character room code.") }
            return
        }
        if (!parsed?.serverUrl.isNullOrBlank()) {
            wsClient.validateEndpointUrl(parsed!!.serverUrl!!).onSuccess { validUrl ->
                prefs.edit().putString(KEY_SERVER_URL, validUrl).apply()
                _uiState.update { it.copy(serverUrl = validUrl, isServerConfigured = true) }
            }
        }

        val state = _uiState.value
        val name = (displayNameOverride ?: state.displayName).trim().take(32).ifBlank { "Listener" }
        updateDisplayName(name)

        val payload = ListenTogetherWebSocketClient.buildJoinRoomPayload(
            roomCode = cleanCode,
            displayName = name,
            qualityLabel = state.selectedQuality.shortLabel
        )

        _uiState.update {
            it.copy(
                isBusy = true,
                errorMessage = null,
                infoBanner = null,
                pendingDeepLinkRoomCode = null
            )
        }
        val result = wsClient.connectAndSend(_uiState.value.serverUrl, payload.toString())
        if (result.isFailure) {
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Unable to connect to server"
                )
            }
        }
    }

    fun leaveRoom() {
        stopSyncLoops()
        wsClient.sendLeaveRoom()
        wsClient.disconnect(clearSession = true)
        resetLocalRoomState(infoMessage = "Left Listen Together room")
    }

    fun endRoom() {
        val state = _uiState.value
        if (!state.isHost) {
            _uiState.update { it.copy(errorMessage = "Only the room host can end this room.") }
            return
        }
        stopSyncLoops()
        wsClient.sendEndRoom()
        wsClient.disconnect(clearSession = true)
        resetLocalRoomState(infoMessage = "Ended Listen Together room for everyone")
    }

    fun setControlMode(mode: ListenTogetherControlMode) {
        val state = _uiState.value
        if (!state.isHost) {
            _uiState.update { it.copy(errorMessage = "Only the room host can change control permissions.") }
            return
        }
        wsClient.sendSetControlMode(mode)
    }

    fun setListeningMode(mode: ListenTogetherListeningMode) {
        if (!guardCanControl()) return
        val state = _uiState.value
        val room = state.room ?: return
        if (mode == ListenTogetherListeningMode.STEREO_SPLIT && room.participants.size > 2) {
            _uiState.update {
                it.copy(
                    errorMessage = "Stereo Split Speaker Mode supports at most 2 devices (Left & Right). Currently ${room.participants.size} devices are in this room."
                )
            }
            return
        }
        wsClient.sendSetListeningMode(mode)
    }

    fun assignSpeakerRole(
        targetParticipantId: String,
        role: ListenTogetherSpeakerRole,
        swap: Boolean = false
    ) {
        if (!guardCanControl()) return
        val room = _uiState.value.room ?: return
        if (room.listeningMode != ListenTogetherListeningMode.STEREO_SPLIT) {
            _uiState.update {
                it.copy(errorMessage = "Enable Stereo Split Speaker Mode before assigning Left/Right speakers.")
            }
            return
        }
        wsClient.sendAssignSpeakerRole(
            targetParticipantId = targetParticipantId,
            role = role,
            swap = swap
        )
    }

    private fun syncLocalSpeakerChannelRole(room: ListenTogetherRoomState?, participantId: String?) {
        val effectiveRole = if (room == null || room.listeningMode != ListenTogetherListeningMode.STEREO_SPLIT) {
            ListenTogetherSpeakerRole.STEREO
        } else {
            room.participants.firstOrNull { it.participantId == participantId }?.speakerRole
                ?: ListenTogetherSpeakerRole.STEREO
        }
        val pcmRole = when (effectiveRole) {
            ListenTogetherSpeakerRole.LEFT -> PcmSpeakerChannelRole.LEFT
            ListenTogetherSpeakerRole.RIGHT -> PcmSpeakerChannelRole.RIGHT
            ListenTogetherSpeakerRole.STEREO -> PcmSpeakerChannelRole.STEREO
        }
        StereoSplitAudioController.setChannelRole(pcmRole)
        val telemetry = StereoSplitAudioController.telemetry.value
        _uiState.update { state ->
            state.copy(
                localSpeakerRole = effectiveRole,
                isCurrentTrackMono = telemetry.isMonoSource,
                stereoSplitNotice = if (room?.listeningMode == ListenTogetherListeningMode.STEREO_SPLIT && telemetry.isMonoSource) {
                    "Current track is mono (1 channel) — meaningful Left/Right stereo separation is not available for mono audio."
                } else {
                    null
                }
            )
        }
    }

    /**
     * Switches ONLY this participant's streaming quality while preserving the room's
     * current track and synchronized playback position.
     */
    fun setStreamingQuality(quality: StreamingQuality) {
        prefs.edit().putString(KEY_STREAMING_QUALITY, quality.key).apply()
        val previous = _uiState.value.selectedQuality
        _uiState.update {
            it.copy(
                selectedQuality = quality,
                activeStreamQualityLabel = quality.shortLabel
            )
        }
        if (!isInRoom) return
        if (previous == quality && loadedQuality == quality) return

        wsClient.sendUpdateQuality(quality.shortLabel)
        val room = _uiState.value.room ?: return
        val currentTrack = room.currentTrack ?: return

        scope.launch {
            _uiState.update { it.copy(isQualitySwitching = true) }
            loadTrackForRoom(room = room, track = currentTrack, forceReload = true, requestedQuality = quality)
            _uiState.update { it.copy(isQualitySwitching = false) }
        }
    }

    fun requestPlay(): Boolean {
        if (!guardCanControl()) return false
        return wsClient.sendPlaybackCommand(
            action = "PLAY",
            positionMs = playbackBridge.currentPositionMs()
        )
    }

    fun requestPause(): Boolean {
        if (!guardCanControl()) return false
        return wsClient.sendPlaybackCommand(
            action = "PAUSE",
            positionMs = playbackBridge.currentPositionMs()
        )
    }

    fun requestTogglePlayPause(): Boolean {
        val room = _uiState.value.room ?: return false
        return if (room.playback.isPlaying) requestPause() else requestPlay()
    }

    fun requestSeek(positionMs: Long): Boolean {
        if (!guardCanControl()) return false
        return wsClient.sendPlaybackCommand(
            action = "SEEK",
            positionMs = positionMs.coerceAtLeast(0L)
        )
    }

    fun requestNext(): Boolean {
        if (!guardCanControl()) return false
        val currentTrackId = _uiState.value.room?.currentTrack?.trackId
        return wsClient.sendPlaybackCommand(action = "NEXT", fromTrackId = currentTrackId)
    }

    fun requestPrevious(): Boolean {
        if (!guardCanControl()) return false
        val currentTrackId = _uiState.value.room?.currentTrack?.trackId
        return wsClient.sendPlaybackCommand(action = "PREV", fromTrackId = currentTrackId)
    }

    fun requestPlaySong(song: Song, autoPlay: Boolean = true): Boolean {
        if (!guardCanControl()) return false
        val state = _uiState.value
        val canonical = ListenTogetherTrack.fromSong(
            song = song,
            addedByParticipantId = state.participantId.orEmpty(),
            addedByName = state.displayName
        )
        if (canonical == null) {
            _uiState.update {
                it.copy(
                    errorMessage = "'${song.title}' is a local or unsupported track. Listen Together requires full-length online streaming tracks."
                )
            }
            return false
        }
        return wsClient.sendPlaybackCommand(
            action = "SET_TRACK",
            track = canonical,
            positionMs = 0L,
            autoPlay = autoPlay
        )
    }

    fun requestSetQueue(songs: List<Song>, startIndex: Int = 0, autoPlay: Boolean = true): Boolean {
        if (!guardCanControl()) return false
        val state = _uiState.value
        val canonicalQueue = songs.mapNotNull {
            ListenTogetherTrack.fromSong(it, state.participantId.orEmpty(), state.displayName)
        }.take(100)
        if (canonicalQueue.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "None of the selected tracks are supported for Listen Together streaming.")
            }
            return false
        }
        return wsClient.sendPlaybackCommand(
            action = "SET_QUEUE",
            queue = canonicalQueue,
            queueIndex = startIndex.coerceIn(0, canonicalQueue.lastIndex),
            autoPlay = autoPlay
        )
    }

    fun requestAddToQueue(song: Song, autoPlayIfEmpty: Boolean = false): Boolean {
        if (!guardCanControl()) return false
        val state = _uiState.value
        val canonical = ListenTogetherTrack.fromSong(
            song = song,
            addedByParticipantId = state.participantId.orEmpty(),
            addedByName = state.displayName
        )
        if (canonical == null) {
            _uiState.update {
                it.copy(
                    errorMessage = "'${song.title}' cannot be added. Only full-length streaming songs work in Listen Together."
                )
            }
            return false
        }
        val sent = wsClient.sendPlaybackCommand(
            action = "ADD_TO_QUEUE",
            track = canonical,
            autoPlay = autoPlayIfEmpty
        )
        if (sent) {
            _uiState.update { it.copy(infoBanner = "Added '${song.title}' to shared queue") }
        }
        return sent
    }

    fun requestRemoveFromQueue(index: Int): Boolean {
        if (!guardCanControl()) return false
        return wsClient.sendPlaybackCommand(action = "REMOVE_FROM_QUEUE", queueIndex = index)
    }

    fun requestPlayQueueIndex(index: Int): Boolean {
        if (!guardCanControl()) return false
        return wsClient.sendPlaybackCommand(action = "PLAY_QUEUE_INDEX", queueIndex = index)
    }

    fun notifyLocalTrackEnded() {
        val state = _uiState.value
        val room = state.room ?: return
        if (!state.isHost && room.controlMode != ListenTogetherControlMode.EVERYONE) return
        val currentTrackId = room.currentTrack?.trackId ?: return
        wsClient.sendPlaybackCommand(action = "TRACK_ENDED", fromTrackId = currentTrackId)
    }

    fun onAppForegrounded() {
        if (!isInRoom) return
        if (wsClient.connectionStatus.value == ListenTogetherConnectionStatus.CONNECTED) {
            scope.launch {
                runClockSyncBurst(count = 3)
                wsClient.sendSyncRequest()
            }
        } else {
            wsClient.forceReconnectNow()
        }
    }

    fun requestManualResync() {
        if (!isInRoom) return
        scope.launch {
            runClockSyncBurst(count = 3)
            wsClient.sendSyncRequest()
        }
    }

    /**
     * Invoked by Media3 Player.Listener when STATE_READY is reached after buffering a newly
     * loaded or quality-switched stream. Immediately snaps/aligns playback to the authoritative
     * server timeline so initial CDN buffering latency does not leave guests behind the host.
     */
    fun onLocalPlaybackReady() {
        val room = _uiState.value.room ?: return
        val currentTrackId = room.currentTrack?.trackId ?: return
        if (awaitingBufferReadyForTrackId == currentTrackId && loadedTrackId == currentTrackId) {
            awaitingBufferReadyForTrackId = null
            evaluateDriftAndApply(room, forceImmediateSeek = true)
        }
    }

    /**
     * Verifies a candidate WebSocket server URL by calling its HTTP(S) `/health` endpoint.
     */
    suspend fun verifyServerHealth(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val normalizedWs = wsClient.validateEndpointUrl(rawUrl).getOrElse { return@withContext Result.failure(it) }
        val healthUrl = normalizedWs
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .replace(Regex("/ws/?$"), "/health")
        runCatching {
            val client = ListenTogetherWebSocketClient.defaultOkHttpClient()
            val req = okhttp3.Request.Builder().url(healthUrl).get().build()
            val start = System.currentTimeMillis()
            client.newCall(req).execute().use { resp ->
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                if (!resp.isSuccessful) {
                    error("Health check failed (HTTP ${resp.code})")
                }
                val body = resp.body?.string().orEmpty()
                if (!body.contains("\"status\"") || !body.contains("ok")) {
                    error("Unexpected response from $healthUrl")
                }
                "Server online (${elapsed}ms)"
            }
        }
    }

    fun release() {
        StereoSplitAudioController.resetToNormalStereo()
        stopSyncLoops()
        unregisterNetworkObserver()
        wsClient.disconnect(clearSession = true)
    }

    private fun guardCanControl(): Boolean {
        val state = _uiState.value
        if (!state.isInRoom) return false
        if (!state.canControlPlayback) {
            _uiState.update {
                it.copy(errorMessage = "Host Controls Only is enabled. Ask the host to switch to Everyone Can Control.")
            }
            return false
        }
        return true
    }

    private fun handleServerEvent(event: ListenTogetherServerEvent) {
        when (event) {
            is ListenTogetherServerEvent.TimeSyncPong -> {
                clockSyncEngine.recordSample(
                    clientSendTimeMs = event.clientSendTimeMs,
                    serverReceiveTimeMs = event.serverReceiveTimeMs,
                    serverSendTimeMs = event.serverSendTimeMs,
                    clientReceiveTimeMs = event.clientReceiveTimeMs
                )
                refreshDiagnosticsState()
            }

            is ListenTogetherServerEvent.RoomJoined -> {
                clockSyncEngine.seedCoarseOffsetIfUnsynced(event.serverTimeMs)
                lastAppliedRevision = 0L
                driftController.reset()
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        participantId = event.participantId,
                        room = event.room,
                        errorMessage = null,
                        infoBanner = if (event.reconnected) "Reconnected and resynced room" else "Connected to room ${event.room.roomCode}"
                    )
                }
                syncLocalSpeakerChannelRole(event.room, event.participantId)
                startSyncLoops()
                applyAuthoritativeRoomState(event.room, forceImmediateSeek = true)
            }

            is ListenTogetherServerEvent.RoomStateUpdated -> {
                clockSyncEngine.seedCoarseOffsetIfUnsynced(event.serverTimeMs)
                _uiState.update { it.copy(room = event.room, isBusy = false) }
                syncLocalSpeakerChannelRole(event.room, _uiState.value.participantId)
                if (event.type == "playback_state" || event.type == "room_state") {
                    applyAuthoritativeRoomState(event.room, forceImmediateSeek = event.room.playback.lastAction == "SEEK")
                }
            }

            is ListenTogetherServerEvent.HostChanged -> {
                clockSyncEngine.seedCoarseOffsetIfUnsynced(event.serverTimeMs)
                val becameHost = event.newHostId == _uiState.value.participantId
                val notice = if (becameHost) {
                    "You are now the room host"
                } else {
                    "${event.newHostName} is now the room host"
                }
                _uiState.update {
                    it.copy(
                        room = event.room,
                        infoBanner = notice
                    )
                }
                syncLocalSpeakerChannelRole(event.room, _uiState.value.participantId)
                applyAuthoritativeRoomState(event.room, forceImmediateSeek = false)
            }

            is ListenTogetherServerEvent.SyncPulse -> {
                val room = _uiState.value.room ?: return
                if (event.roomCode != room.roomCode) return
                clockSyncEngine.seedCoarseOffsetIfUnsynced(event.serverTimeMs)

                // If server revision jumped ahead of our local snapshot, request full state
                if (event.stateRevision > room.playback.stateRevision || event.queueRevision > room.playback.queueRevision) {
                    wsClient.sendSyncRequest()
                    return
                }
                val updatedPlayback = room.playback.copy(
                    isPlaying = event.isPlaying,
                    basePositionMs = event.basePositionMs,
                    scheduledStartServerTimeMs = event.scheduledStartServerTimeMs,
                    effectivePositionMs = event.effectivePositionMs,
                    playbackRate = event.playbackRate
                )
                val updatedRoom = room.copy(playback = updatedPlayback)
                _uiState.update { it.copy(room = updatedRoom) }
                evaluateDriftAndApply(updatedRoom, forceImmediateSeek = false)
            }

            is ListenTogetherServerEvent.RoomLeft -> {
                stopSyncLoops()
                resetLocalRoomState(infoMessage = "Left room ${event.roomCode}")
            }

            is ListenTogetherServerEvent.RoomClosed -> {
                stopSyncLoops()
                resetLocalRoomState(infoMessage = event.message)
            }

            is ListenTogetherServerEvent.ServerError -> {
                _uiState.update { it.copy(isBusy = false, errorMessage = event.message) }
                if (event.code == "ROOM_EXPIRED" || event.code == "ROOM_NOT_FOUND") {
                    if (isInRoom) {
                        stopSyncLoops()
                        resetLocalRoomState(errorMessage = event.message)
                    }
                }
            }
        }
    }

    private fun applyAuthoritativeRoomState(
        room: ListenTogetherRoomState,
        forceImmediateSeek: Boolean
    ) {
        // Keep MusicPlayerViewModel queue display mirrored with shared queue
        val queueSongs = room.queue.map { it.toSong() }
        playbackBridge.syncSharedQueueDisplay(queueSongs, room.currentIndex)

        val targetTrack = room.currentTrack
        if (targetTrack == null) {
            scheduledStartJob?.cancel()
            runAsRemoteAction {
                playbackBridge.setPlaybackSpeed(1.0f)
                if (playbackBridge.isPlaying()) playbackBridge.pause()
            }
            loadedTrackId = null
            return
        }

        val currentQuality = _uiState.value.selectedQuality
        val needsTrackLoad = loadedTrackId != targetTrack.trackId ||
            playbackBridge.currentSong()?.filePath != targetTrack.trackId

        lastAppliedRevision = maxOf(lastAppliedRevision, room.playback.stateRevision)

        if (needsTrackLoad) {
            trackLoadJob?.cancel()
            trackLoadJob = scope.launch {
                loadTrackForRoom(
                    room = room,
                    track = targetTrack,
                    forceReload = false,
                    requestedQuality = currentQuality
                )
            }
        } else {
            evaluateDriftAndApply(room, forceImmediateSeek = forceImmediateSeek)
        }
    }

    private suspend fun loadTrackForRoom(
        room: ListenTogetherRoomState,
        track: ListenTogetherTrack,
        forceReload: Boolean,
        requestedQuality: StreamingQuality
    ) {
        val cacheKey = "${track.trackId}|${requestedQuality.key}"
        val resolvedEntry = if (!forceReload && resolvedTrackCache.containsKey(cacheKey)) {
            resolvedTrackCache[cacheKey]!!
        } else {
            val result = withContext(Dispatchers.IO) {
                repository.resolveCanonicalListenTogetherTrack(
                    trackId = track.trackId,
                    provider = track.provider,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.artworkUrl.takeIf { it.isNotBlank() },
                    durationMs = track.durationMs,
                    quality = requestedQuality
                )
            }
            result.fold(
                onSuccess = { (song, resolvedStream) ->
                    val entry = ResolvedTrackEntry(
                        song = song,
                        url = resolvedStream.url,
                        actualLabel = resolvedStream.actualQualityLabel
                    )
                    resolvedTrackCache[cacheKey] = entry
                    entry
                },
                onFailure = { err ->
                    Log.e(TAG, "Failed to resolve canonical Listen Together track '${track.title}': ${err.message}")
                    _uiState.update {
                        it.copy(
                            errorMessage = err.message ?: "Unable to stream '${track.title}' on this device."
                        )
                    }
                    return
                }
            )
        }

        // Re-read freshest authoritative room state after async stream resolution finished
        val latestRoom = _uiState.value.room ?: return
        if (latestRoom.currentTrack?.trackId != track.trackId) return

        val serverNow = clockSyncEngine.estimatedServerTimeMs()
        val projectedPos = driftController.projectTargetPositionMs(
            state = latestRoom.playback,
            serverNowMs = serverNow,
            trackDurationMs = track.durationMs
        )
        val hasFutureStart = latestRoom.playback.isPlaying &&
            latestRoom.playback.scheduledStartServerTimeMs > serverNow + 25L

        loadedTrackId = track.trackId
        loadedQuality = requestedQuality
        awaitingBufferReadyForTrackId = track.trackId

        _uiState.update {
            it.copy(
                activeStreamQualityLabel = resolvedEntry.actualLabel,
                errorMessage = null
            )
        }

        scheduledStartJob?.cancel()
        runAsRemoteAction {
            playbackBridge.setPlaybackSpeed(1.0f)
            playbackBridge.loadAndCueTrack(
                song = resolvedEntry.song,
                resolvedStreamUrl = resolvedEntry.url,
                positionMs = projectedPos,
                playImmediately = latestRoom.playback.isPlaying && !hasFutureStart
            )
        }

        if (hasFutureStart) {
            val delayMs = (latestRoom.playback.scheduledStartServerTimeMs - serverNow).coerceIn(25L, 3000L)
            scheduleFutureStart(track.trackId, projectedPos, delayMs)
        }
    }

    private fun evaluateDriftAndApply(
        room: ListenTogetherRoomState,
        forceImmediateSeek: Boolean
    ) {
        val track = room.currentTrack ?: return
        if (loadedTrackId != track.trackId) return

        val serverNow = clockSyncEngine.estimatedServerTimeMs()
        val localNow = System.currentTimeMillis()
        val localPos = playbackBridge.currentPositionMs()
        val localPlaying = playbackBridge.isPlaying()

        val action = driftController.evaluate(
            state = room.playback,
            trackDurationMs = track.durationMs,
            localPositionMs = localPos,
            localIsPlaying = localPlaying,
            serverNowMs = serverNow,
            localNowMs = localNow,
            forceImmediateSeek = forceImmediateSeek
        )

        val projectedPos = driftController.projectTargetPositionMs(room.playback, serverNow, track.durationMs)
        val driftMs = projectedPos - localPos

        when (action) {
            is PlaybackDriftController.CorrectionAction.InSync -> {
                scheduledStartJob?.cancel()
                runAsRemoteAction { playbackBridge.setPlaybackSpeed(1.0f) }
                refreshDiagnosticsState(driftMs = driftMs, statusLabel = "Synced")
            }

            is PlaybackDriftController.CorrectionAction.PauseAt -> {
                scheduledStartJob?.cancel()
                runAsRemoteAction {
                    playbackBridge.setPlaybackSpeed(1.0f)
                    if (playbackBridge.isPlaying()) playbackBridge.pause()
                    if (kotlin.math.abs(playbackBridge.currentPositionMs() - action.targetPositionMs) > 120L) {
                        playbackBridge.seekTo(action.targetPositionMs)
                    }
                }
                refreshDiagnosticsState(driftMs = 0L, statusLabel = "Paused in sync")
            }

            is PlaybackDriftController.CorrectionAction.ScheduleStart -> {
                runAsRemoteAction {
                    playbackBridge.setPlaybackSpeed(1.0f)
                    if (playbackBridge.isPlaying()) playbackBridge.pause()
                    playbackBridge.seekTo(action.targetPositionMs)
                }
                scheduleFutureStart(track.trackId, action.targetPositionMs, action.delayMs)
                refreshDiagnosticsState(driftMs = 0L, statusLabel = "Starting in sync…")
            }

            is PlaybackDriftController.CorrectionAction.AdjustSpeed -> {
                scheduledStartJob?.cancel()
                runAsRemoteAction {
                    playbackBridge.setPlaybackSpeed(action.speed)
                    if (!playbackBridge.isPlaying()) playbackBridge.play()
                }
                refreshDiagnosticsState(
                    driftMs = action.driftMs,
                    statusLabel = "Fine-tuning (${action.driftMs}ms)"
                )
            }

            is PlaybackDriftController.CorrectionAction.HardSeek -> {
                scheduledStartJob?.cancel()
                runAsRemoteAction {
                    playbackBridge.setPlaybackSpeed(1.0f)
                    playbackBridge.seekTo(action.targetPositionMs)
                    if (!playbackBridge.isPlaying()) playbackBridge.play()
                }
                refreshDiagnosticsState(
                    driftMs = action.driftMs,
                    statusLabel = "Resynced (${action.driftMs}ms)"
                )
            }
        }
    }

    private fun scheduleFutureStart(trackId: String, targetPosMs: Long, delayMs: Long) {
        scheduledStartJob?.cancel()
        scheduledStartJob = scope.launch {
            delay(delayMs)
            val latestRoom = _uiState.value.room ?: return@launch
            if (!latestRoom.playback.isPlaying || latestRoom.currentTrack?.trackId != trackId) return@launch
            runAsRemoteAction {
                playbackBridge.setPlaybackSpeed(1.0f)
                if (kotlin.math.abs(playbackBridge.currentPositionMs() - targetPosMs) > 180L) {
                    playbackBridge.seekTo(targetPosMs)
                }
                playbackBridge.play()
            }
            refreshDiagnosticsState(driftMs = 0L, statusLabel = "Synced")
        }
    }

    private fun startSyncLoops() {
        stopSyncLoops()
        clockPingLoopJob = scope.launch {
            runClockSyncBurst(count = 5)
            while (isInRoom) {
                delay(25_000L)
                if (wsClient.connectionStatus.value == ListenTogetherConnectionStatus.CONNECTED) {
                    wsClient.sendTimeSyncPing()
                }
            }
        }
        driftMonitorJob = scope.launch {
            while (isInRoom) {
                delay(1_500L)
                val room = _uiState.value.room
                if (room != null && room.playback.isPlaying && loadedTrackId != null) {
                    evaluateDriftAndApply(room, forceImmediateSeek = false)
                }
            }
        }
    }

    private suspend fun runClockSyncBurst(count: Int) {
        repeat(count) { idx ->
            if (wsClient.connectionStatus.value == ListenTogetherConnectionStatus.CONNECTED) {
                wsClient.sendTimeSyncPing()
            }
            if (idx < count - 1) delay(120L)
        }
    }

    private fun stopSyncLoops() {
        scheduledStartJob?.cancel()
        scheduledStartJob = null
        trackLoadJob?.cancel()
        trackLoadJob = null
        clockPingLoopJob?.cancel()
        clockPingLoopJob = null
        driftMonitorJob?.cancel()
        driftMonitorJob = null
    }

    private fun resetLocalRoomState(infoMessage: String? = null, errorMessage: String? = null) {
        StereoSplitAudioController.resetToNormalStereo()
        runAsRemoteAction { playbackBridge.setPlaybackSpeed(1.0f) }
        loadedTrackId = null
        loadedQuality = null
        awaitingBufferReadyForTrackId = null
        lastAppliedRevision = 0L
        clockSyncEngine.reset()
        driftController.reset()
        _uiState.update { state ->
            state.copy(
                participantId = null,
                room = null,
                isBusy = false,
                isQualitySwitching = false,
                localSpeakerRole = ListenTogetherSpeakerRole.STEREO,
                stereoSplitNotice = null,
                diagnostics = SyncDiagnostics(),
                infoBanner = infoMessage,
                errorMessage = errorMessage
            )
        }
    }

    private fun refreshDiagnosticsState(
        driftMs: Long = _uiState.value.diagnostics.lastMeasuredDriftMs,
        statusLabel: String = _uiState.value.diagnostics.syncStatusLabel
    ) {
        _uiState.update { state ->
            state.copy(
                diagnostics = SyncDiagnostics(
                    estimatedRttMs = clockSyncEngine.estimatedRttMs,
                    clockOffsetMs = clockSyncEngine.clockOffsetMs,
                    lastMeasuredDriftMs = driftMs,
                    isClockSynced = clockSyncEngine.isSynced,
                    syncStatusLabel = statusLabel
                )
            )
        }
    }

    private inline fun runAsRemoteAction(block: () -> Unit) {
        isApplyingRemoteState = true
        try {
            block()
        } finally {
            isApplyingRemoteState = false
        }
    }

    private fun registerNetworkObserver() {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (isInRoom && wsClient.connectionStatus.value != ListenTogetherConnectionStatus.CONNECTED) {
                        wsClient.forceReconnectNow()
                    }
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        }
    }

    private fun unregisterNetworkObserver() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        }
    }

    companion object {
        private const val TAG = "ListenTogetherCoord"
        private const val PREFS_NAME = "usman_listen_together_prefs"
        private const val KEY_SERVER_URL = "server_ws_url"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_STREAMING_QUALITY = "streaming_quality"
        const val DEFAULT_DEPLOYED_WS_URL = "wss://usman-music-listen-together.onrender.com/ws"
    }
}
