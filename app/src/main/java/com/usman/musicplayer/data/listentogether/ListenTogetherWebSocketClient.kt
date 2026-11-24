package com.usman.musicplayer.data.listentogether

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

sealed class ListenTogetherServerEvent {
    data class RoomJoined(
        val participantId: String,
        val sessionToken: String,
        val reconnected: Boolean,
        val serverTimeMs: Long,
        val room: ListenTogetherRoomState
    ) : ListenTogetherServerEvent()

    data class RoomStateUpdated(
        val type: String,
        val serverTimeMs: Long,
        val room: ListenTogetherRoomState
    ) : ListenTogetherServerEvent()

    data class HostChanged(
        val newHostId: String,
        val newHostName: String,
        val reason: String,
        val serverTimeMs: Long,
        val room: ListenTogetherRoomState
    ) : ListenTogetherServerEvent()

    data class SyncPulse(
        val roomCode: String,
        val serverTimeMs: Long,
        val stateRevision: Long,
        val queueRevision: Long,
        val currentIndex: Int,
        val currentTrackId: String,
        val isPlaying: Boolean,
        val basePositionMs: Long,
        val scheduledStartServerTimeMs: Long,
        val effectivePositionMs: Long,
        val playbackRate: Float
    ) : ListenTogetherServerEvent()

    data class TimeSyncPong(
        val seq: Int,
        val clientSendTimeMs: Long,
        val serverReceiveTimeMs: Long,
        val serverSendTimeMs: Long,
        val clientReceiveTimeMs: Long
    ) : ListenTogetherServerEvent()

    data class RoomLeft(val roomCode: String) : ListenTogetherServerEvent()
    data class RoomClosed(val roomCode: String, val reason: String, val message: String) : ListenTogetherServerEvent()
    data class ServerError(val code: String, val message: String) : ListenTogetherServerEvent()
}

data class ActiveSessionCredentials(
    val roomCode: String,
    val participantId: String,
    val sessionToken: String,
    val displayName: String,
    val qualityLabel: String
)

/**
 * Production OkHttp WebSocket transport for USMAN MUSIC Listen Together.
 * Uses pure kotlinx.serialization.json so it runs identically on Android devices and JVM tests.
 *
 * Security:
 * - Enforces WSS (`wss://`) for all public internet hosts.
 * - Allows `ws://` strictly for local loopback / private LAN development addresses.
 * - Automatically reconnects with exponential backoff and authenticates using the
 *   server-issued opaque `sessionToken`.
 */
class ListenTogetherWebSocketClient(
    private val scope: CoroutineScope,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient()
) {
    private val _connectionStatus = MutableStateFlow(ListenTogetherConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ListenTogetherConnectionStatus> = _connectionStatus.asStateFlow()

    private val _events = MutableSharedFlow<ListenTogetherServerEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<ListenTogetherServerEvent> = _events.asSharedFlow()

    private val lock = Any()
    private var webSocket: WebSocket? = null
    private var targetUrl: String = ""
    private var apiKey: String = ""
    private var pendingOnOpenPayload: String? = null
    private var activeCredentials: ActiveSessionCredentials? = null
    private var manuallyDisconnected: Boolean = false
    private var reconnectAttempt: Int = 0
    private var reconnectJob: Job? = null
    private val pingSeq = AtomicInteger(1)

    fun validateEndpointUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "Listen Together server URL is not configured. Set LISTEN_TOGETHER_WS_URL or configure it in Server Settings."
                )
            )
        }
        val normalized = when {
            trimmed.startsWith("https://", ignoreCase = true) ->
                "wss://" + trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) ->
                "ws://" + trimmed.substring(7)
            else -> trimmed
        }.let { url ->
            val parsed = runCatching { URI(url) }.getOrNull()
            if (parsed != null && (parsed.path.isNullOrBlank() || parsed.path == "/")) {
                url.trimEnd('/') + "/ws"
            } else {
                url
            }
        }

        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid WebSocket server URL."))
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme != "wss" && scheme != "ws") {
            return Result.failure(IllegalArgumentException("Server URL must start with wss:// (or ws:// for local LAN dev)."))
        }
        if (host.isBlank()) {
            return Result.failure(IllegalArgumentException("Server URL is missing a valid hostname."))
        }
        if (scheme == "ws" && !isLocalOrLanHost(host)) {
            return Result.failure(
                IllegalArgumentException("Public Listen Together servers must use secure wss:// (TLS).")
            )
        }
        return Result.success(normalized)
    }

    fun connectAndSend(
        serverUrl: String,
        initialPayloadJson: String,
        apiKeyHeader: String = ""
    ): Result<Unit> {
        val validated = validateEndpointUrl(serverUrl)
        val normalizedUrl = validated.getOrElse { return Result.failure(it) }

        synchronized(lock) {
            manuallyDisconnected = false
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            targetUrl = normalizedUrl
            apiKey = apiKeyHeader.trim()

            // Reuse open socket if already connected to the same endpoint
            val existing = webSocket
            if (existing != null && _connectionStatus.value == ListenTogetherConnectionStatus.CONNECTED) {
                existing.send(initialPayloadJson)
                return Result.success(Unit)
            }

            pendingOnOpenPayload = initialPayloadJson
            closeSocketInternal(1000, "Switching connection")
            _connectionStatus.value = ListenTogetherConnectionStatus.CONNECTING
            openSocketLocked(normalizedUrl)
        }
        return Result.success(Unit)
    }

    fun updateSessionCredentials(credentials: ActiveSessionCredentials?) {
        synchronized(lock) {
            activeCredentials = credentials
        }
    }

    fun sendJson(payload: JsonObject): Boolean {
        val serialized = payload.toString()
        synchronized(lock) {
            val ws = webSocket ?: return false
            if (_connectionStatus.value != ListenTogetherConnectionStatus.CONNECTED) return false
            return ws.send(serialized)
        }
    }

    fun sendTimeSyncPing(localNowMs: Long = System.currentTimeMillis()): Boolean {
        val seq = pingSeq.getAndIncrement()
        val json = buildJsonObject {
            put("type", "time_sync_ping")
            put("seq", seq)
            put("clientSendTimeMs", localNowMs)
        }
        return sendJson(json)
    }

    fun sendSyncRequest(): Boolean {
        return sendJson(buildJsonObject { put("type", "sync_request") })
    }

    fun sendPlaybackCommand(
        action: String,
        commandId: String = UUID.randomUUID().toString(),
        positionMs: Long? = null,
        track: ListenTogetherTrack? = null,
        queue: List<ListenTogetherTrack>? = null,
        queueIndex: Int? = null,
        autoPlay: Boolean? = null,
        fromTrackId: String? = null
    ): Boolean {
        val json = buildJsonObject {
            put("type", "playback_command")
            put("commandId", commandId)
            put("action", action)
            if (positionMs != null) put("positionMs", positionMs)
            if (track != null) put("track", trackToJson(track))
            if (queue != null) {
                put("queue", buildJsonArray { queue.forEach { add(trackToJson(it)) } })
            }
            if (queueIndex != null) put("queueIndex", queueIndex)
            if (autoPlay != null) put("autoPlay", autoPlay)
            if (!fromTrackId.isNullOrBlank()) put("trackId", fromTrackId)
        }
        return sendJson(json)
    }

    fun sendSetControlMode(mode: ListenTogetherControlMode): Boolean {
        return sendJson(
            buildJsonObject {
                put("type", "set_control_mode")
                put("controlMode", mode.wireValue)
            }
        )
    }

    fun sendSetListeningMode(mode: ListenTogetherListeningMode): Boolean {
        return sendJson(
            buildJsonObject {
                put("type", "set_listening_mode")
                put("listeningMode", mode.wireValue)
            }
        )
    }

    fun sendAssignSpeakerRole(
        targetParticipantId: String,
        role: ListenTogetherSpeakerRole,
        swap: Boolean = false
    ): Boolean {
        return sendJson(
            buildJsonObject {
                put("type", "assign_speaker_role")
                put("targetParticipantId", targetParticipantId)
                put("speakerRole", role.wireValue)
                put("swap", swap)
            }
        )
    }

    fun sendUpdateQuality(qualityLabel: String): Boolean {
        return sendJson(
            buildJsonObject {
                put("type", "update_quality")
                put("qualityLabel", qualityLabel)
            }
        )
    }

    fun sendLeaveRoom(): Boolean {
        val sent = sendJson(buildJsonObject { put("type", "leave_room") })
        synchronized(lock) {
            activeCredentials = null
        }
        return sent
    }

    fun sendEndRoom(): Boolean {
        val sent = sendJson(buildJsonObject { put("type", "end_room") })
        synchronized(lock) {
            activeCredentials = null
        }
        return sent
    }

    fun forceReconnectNow() {
        synchronized(lock) {
            if (manuallyDisconnected) return
            val creds = activeCredentials ?: return
            if (targetUrl.isBlank()) return
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            pendingOnOpenPayload = buildReconnectPayload(creds).toString()
            closeSocketInternal(1000, "Network transition reconnect")
            _connectionStatus.value = ListenTogetherConnectionStatus.RECONNECTING
            openSocketLocked(targetUrl)
        }
    }

    fun disconnect(clearSession: Boolean = true) {
        synchronized(lock) {
            manuallyDisconnected = true
            reconnectJob?.cancel()
            reconnectJob = null
            pendingOnOpenPayload = null
            if (clearSession) {
                activeCredentials = null
            }
            closeSocketInternal(1000, "Client disconnected")
            _connectionStatus.value = ListenTogetherConnectionStatus.DISCONNECTED
        }
    }

    private fun closeSocketInternal(code: Int, reason: String) {
        val existing = webSocket
        webSocket = null
        runCatching { existing?.close(code, reason) }
    }

    private fun openSocketLocked(url: String) {
        val reqBuilder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) {
            reqBuilder.header("x-api-key", apiKey)
        }
        val request = reqBuilder.build()

        lateinit var openedSocket: WebSocket
        openedSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val payloadToSend: String?
                synchronized(lock) {
                    if (webSocket !== ws) {
                        ws.close(1000, "Superseded socket")
                        return
                    }
                    reconnectAttempt = 0
                    _connectionStatus.value = ListenTogetherConnectionStatus.CONNECTED
                    payloadToSend = pendingOnOpenPayload
                    pendingOnOpenPayload = null
                }
                if (!payloadToSend.isNullOrBlank()) {
                    ws.send(payloadToSend)
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val receiveNow = System.currentTimeMillis()
                parseAndEmitEvent(text, receiveNow)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                handleSocketDrop(ws, "Connection closed ($code): $reason")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val httpCode = response?.code
                val msg = if (httpCode != null) {
                    "Server rejected WebSocket (HTTP $httpCode): ${t.message ?: "connection failed"}"
                } else {
                    t.message ?: "Unable to reach Listen Together server"
                }
                runCatching { Log.w(TAG, "WebSocket failure: $msg") }
                handleSocketDrop(ws, msg)
            }
        })
        webSocket = openedSocket
    }

    private fun handleSocketDrop(ws: WebSocket, reason: String) {
        var shouldReconnect = false
        var attempt = 0
        synchronized(lock) {
            if (webSocket !== ws) return
            webSocket = null
            if (manuallyDisconnected) {
                _connectionStatus.value = ListenTogetherConnectionStatus.DISCONNECTED
                return
            }
            val creds = activeCredentials
            if (creds != null && targetUrl.isNotBlank()) {
                shouldReconnect = true
                reconnectAttempt += 1
                attempt = reconnectAttempt
                pendingOnOpenPayload = buildReconnectPayload(creds).toString()
                _connectionStatus.value = ListenTogetherConnectionStatus.RECONNECTING
            } else {
                _connectionStatus.value = ListenTogetherConnectionStatus.ERROR
            }
        }

        if (!shouldReconnect) {
            _events.tryEmit(ListenTogetherServerEvent.ServerError("CONNECTION_FAILED", reason))
            return
        }

        val backoffMs = min(15_000L, 1000L * (1L shl min(attempt - 1, 4)))
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            synchronized(lock) {
                if (manuallyDisconnected || activeCredentials == null || targetUrl.isBlank()) return@launch
                if (webSocket != null) return@launch
                openSocketLocked(targetUrl)
            }
        }
    }

    private fun parseAndEmitEvent(raw: String, localReceiveMs: Long) {
        val json = runCatching { jsonParser.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (val type = json.optString("type")) {
            "time_sync_pong" -> {
                _events.tryEmit(
                    ListenTogetherServerEvent.TimeSyncPong(
                        seq = json.optInt("seq", 0),
                        clientSendTimeMs = json.optLong("clientSendTimeMs", 0L),
                        serverReceiveTimeMs = json.optLong("serverReceiveTimeMs", 0L),
                        serverSendTimeMs = json.optLong("serverSendTimeMs", 0L),
                        clientReceiveTimeMs = localReceiveMs
                    )
                )
            }

            "room_joined" -> {
                val roomObj = json.optObject("room") ?: return
                val room = parseRoom(roomObj)
                val participantId = json.optString("participantId")
                val sessionToken = json.optString("sessionToken")
                val me = room.participants.firstOrNull { it.participantId == participantId }
                synchronized(lock) {
                    activeCredentials = ActiveSessionCredentials(
                        roomCode = room.roomCode,
                        participantId = participantId,
                        sessionToken = sessionToken,
                        displayName = me?.displayName ?: "Listener",
                        qualityLabel = me?.qualityLabel ?: "HD"
                    )
                }
                _events.tryEmit(
                    ListenTogetherServerEvent.RoomJoined(
                        participantId = participantId,
                        sessionToken = sessionToken,
                        reconnected = json.optBoolean("reconnected", false),
                        serverTimeMs = json.optLong("serverTimeMs", localReceiveMs),
                        room = room
                    )
                )
            }

            "playback_state", "room_state", "participant_updated", "listening_mode_updated", "speaker_role_updated" -> {
                val roomObj = json.optObject("room") ?: return
                _events.tryEmit(
                    ListenTogetherServerEvent.RoomStateUpdated(
                        type = type,
                        serverTimeMs = json.optLong("serverTimeMs", localReceiveMs),
                        room = parseRoom(roomObj)
                    )
                )
            }

            "host_changed" -> {
                val roomObj = json.optObject("room") ?: return
                _events.tryEmit(
                    ListenTogetherServerEvent.HostChanged(
                        newHostId = json.optString("newHostId"),
                        newHostName = json.optString("newHostName"),
                        reason = json.optString("reason"),
                        serverTimeMs = json.optLong("serverTimeMs", localReceiveMs),
                        room = parseRoom(roomObj)
                    )
                )
            }

            "sync_pulse" -> {
                _events.tryEmit(
                    ListenTogetherServerEvent.SyncPulse(
                        roomCode = json.optString("roomCode"),
                        serverTimeMs = json.optLong("serverTimeMs", localReceiveMs),
                        stateRevision = json.optLong("stateRevision", 0L),
                        queueRevision = json.optLong("queueRevision", 0L),
                        currentIndex = json.optInt("currentIndex", -1),
                        currentTrackId = json.optString("currentTrackId"),
                        isPlaying = json.optBoolean("isPlaying", false),
                        basePositionMs = json.optLong("basePositionMs", 0L),
                        scheduledStartServerTimeMs = json.optLong("scheduledStartServerTimeMs", 0L),
                        effectivePositionMs = json.optLong("effectivePositionMs", 0L),
                        playbackRate = json.optDouble("playbackRate", 1.0).toFloat()
                    )
                )
            }

            "room_left" -> {
                synchronized(lock) { activeCredentials = null }
                _events.tryEmit(ListenTogetherServerEvent.RoomLeft(json.optString("roomCode")))
            }

            "room_closed" -> {
                synchronized(lock) { activeCredentials = null }
                _events.tryEmit(
                    ListenTogetherServerEvent.RoomClosed(
                        roomCode = json.optString("roomCode"),
                        reason = json.optString("reason"),
                        message = json.optString("message", "Listening room closed")
                    )
                )
            }

            "error" -> {
                val code = json.optString("code", "UNKNOWN_ERROR")
                val message = json.optString("message", "Server returned an error")
                if (code == "ROOM_NOT_FOUND" || code == "ROOM_EXPIRED" || code == "UNAUTHORIZED") {
                    synchronized(lock) { activeCredentials = null }
                }
                _events.tryEmit(ListenTogetherServerEvent.ServerError(code, message))
            }
        }
    }

    companion object {
        private const val TAG = "ListenTogetherWS"
        private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .pingInterval(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun isLocalOrLanHost(host: String): Boolean {
            if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host == "::1") return true
            if (host.startsWith("192.168.") || host.startsWith("10.")) return true
            val match172 = Regex("""^172\.(\d{1,3})\.\d{1,3}\.\d{1,3}$""").matchEntire(host)
            if (match172 != null) {
                val secondOctet = match172.groupValues[1].toIntOrNull() ?: return false
                return secondOctet in 16..31
            }
            return host.endsWith(".local")
        }

        fun buildCreateRoomPayload(
            displayName: String,
            qualityLabel: String,
            controlMode: ListenTogetherControlMode,
            initialTrack: ListenTogetherTrack?,
            initialQueue: List<ListenTogetherTrack>,
            initialIndex: Int,
            initialPositionMs: Long,
            initialIsPlaying: Boolean
        ): JsonObject {
            return buildJsonObject {
                put("type", "create_room")
                put("displayName", displayName)
                put("qualityLabel", qualityLabel)
                put("controlMode", controlMode.wireValue)
                put("initialPositionMs", initialPositionMs.coerceAtLeast(0L))
                put("initialIsPlaying", initialIsPlaying)
                put("initialIndex", initialIndex)
                if (initialTrack != null) {
                    put("initialTrack", trackToJson(initialTrack))
                }
                if (initialQueue.isNotEmpty()) {
                    put("initialQueue", buildJsonArray { initialQueue.forEach { add(trackToJson(it)) } })
                }
            }
        }

        fun buildJoinRoomPayload(
            roomCode: String,
            displayName: String,
            qualityLabel: String
        ): JsonObject {
            return buildJsonObject {
                put("type", "join_room")
                put("roomCode", ListenTogetherQrCodec.normalizeRoomCode(roomCode))
                put("displayName", displayName)
                put("qualityLabel", qualityLabel)
            }
        }

        fun buildReconnectPayload(creds: ActiveSessionCredentials): JsonObject {
            return buildJsonObject {
                put("type", "reconnect_room")
                put("roomCode", creds.roomCode)
                put("participantId", creds.participantId)
                put("sessionToken", creds.sessionToken)
                put("displayName", creds.displayName)
                put("qualityLabel", creds.qualityLabel)
            }
        }

        fun trackToJson(track: ListenTogetherTrack): JsonObject {
            return buildJsonObject {
                put("trackId", track.trackId)
                put("provider", track.provider)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("artworkUrl", track.artworkUrl)
                put("durationMs", track.durationMs)
                put("addedByParticipantId", track.addedByParticipantId)
                put("addedByName", track.addedByName)
            }
        }

        private fun parseTrack(obj: JsonObject?): ListenTogetherTrack? {
            if (obj == null) return null
            val trackId = obj.optString("trackId")
            val title = obj.optString("title")
            if (trackId.isBlank() || title.isBlank()) return null
            return ListenTogetherTrack(
                trackId = trackId,
                provider = obj.optString("provider", "JIO_SAAVN"),
                title = title,
                artist = obj.optString("artist", "Unknown Artist"),
                album = obj.optString("album", ""),
                artworkUrl = obj.optString("artworkUrl", ""),
                durationMs = obj.optLong("durationMs", 0L),
                addedByParticipantId = obj.optString("addedByParticipantId", ""),
                addedByName = obj.optString("addedByName", "")
            )
        }

        private fun parseRoom(obj: JsonObject): ListenTogetherRoomState {
            val queue = obj.optArray("queue")?.mapNotNull { el ->
                runCatching { parseTrack(el.jsonObject) }.getOrNull()
            }.orEmpty()

            val participants = obj.optArray("participants")?.mapNotNull { el ->
                val p = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                ListenTogetherParticipant(
                    participantId = p.optString("participantId"),
                    displayName = p.optString("displayName", "Listener"),
                    isHost = p.optBoolean("isHost", false),
                    connectionState = ParticipantConnectionState.fromWire(p.optString("connectionState")),
                    joinedAt = p.optLong("joinedAt", 0L),
                    qualityLabel = p.optString("qualityLabel", "HD"),
                    speakerRole = ListenTogetherSpeakerRole.fromWire(p.optString("speakerRole"))
                )
            }.orEmpty()

            val pb = obj.optObject("playback") ?: buildJsonObject {}
            val playback = AuthoritativePlaybackState(
                isPlaying = pb.optBoolean("isPlaying", false),
                basePositionMs = pb.optLong("basePositionMs", 0L),
                anchorServerTimeMs = pb.optLong("anchorServerTimeMs", 0L),
                scheduledStartServerTimeMs = pb.optLong("scheduledStartServerTimeMs", 0L),
                effectivePositionMs = pb.optLong("effectivePositionMs", 0L),
                playbackRate = pb.optDouble("playbackRate", 1.0).toFloat(),
                stateRevision = pb.optLong("stateRevision", 0L),
                queueRevision = pb.optLong("queueRevision", 0L),
                lastCommandId = pb.optString("lastCommandId").takeIf { it.isNotBlank() },
                lastUpdatedBy = pb.optString("lastUpdatedBy").takeIf { it.isNotBlank() },
                lastAction = pb.optString("lastAction").takeIf { it.isNotBlank() }
            )
            val currentIndex = obj.optInt("currentIndex", -1)
            val currentTrack = parseTrack(obj.optObject("currentTrack"))
                ?: queue.getOrNull(currentIndex)
            val listeningMode = ListenTogetherListeningMode.fromWire(obj.optString("listeningMode"))
            val defaultMax = if (listeningMode == ListenTogetherListeningMode.STEREO_SPLIT) 2 else 10

            return ListenTogetherRoomState(
                roomId = obj.optString("roomId"),
                roomCode = obj.optString("roomCode"),
                createdAt = obj.optLong("createdAt", 0L),
                expiresAt = obj.optLong("expiresAt", 0L),
                hostId = obj.optString("hostId"),
                controlMode = ListenTogetherControlMode.fromWire(obj.optString("controlMode")),
                listeningMode = listeningMode,
                maxParticipants = obj.optInt("maxParticipants", defaultMax).coerceAtLeast(2),
                currentIndex = currentIndex,
                currentTrack = currentTrack,
                queue = queue,
                participants = participants,
                playback = playback
            )
        }

        private fun JsonObject.optString(key: String, default: String = ""): String =
            runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: default

        private fun JsonObject.optInt(key: String, default: Int = 0): Int =
            runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull() ?: default

        private fun JsonObject.optLong(key: String, default: Long = 0L): Long =
            runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull() ?: default

        private fun JsonObject.optDouble(key: String, default: Double = 0.0): Double =
            runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull() ?: default

        private fun JsonObject.optBoolean(key: String, default: Boolean = false): Boolean =
            runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: default

        private fun JsonObject.optObject(key: String): JsonObject? =
            runCatching { this[key]?.jsonObject }.getOrNull()

        private fun JsonObject.optArray(key: String): JsonArray? =
            runCatching { this[key]?.jsonArray }.getOrNull()
    }
}
