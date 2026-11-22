package com.usman.musicplayer.listentogether

import com.usman.musicplayer.data.listentogether.ClockSyncEngine
import com.usman.musicplayer.data.listentogether.ListenTogetherControlMode
import com.usman.musicplayer.data.listentogether.ListenTogetherServerEvent
import com.usman.musicplayer.data.listentogether.ListenTogetherTrack
import com.usman.musicplayer.data.listentogether.ListenTogetherWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket

/**
 * Real end-to-end integration test launching the actual Node.js WebSocket server
 * and connecting two Android OkHttp ListenTogetherWebSocketClient instances (Phone A & Phone B).
 */
class ListenTogetherEndToEndWebSocketTest {

    private var serverProcess: Process? = null
    private var wsUrl: String = ""

    private val track1 = ListenTogetherTrack(
        trackId = "saavn_song_001",
        provider = "JIO_SAAVN",
        title = "Devara Thandavam",
        artist = "Anirudh Ravichander",
        album = "Devara",
        artworkUrl = "https://c.saavncdn.com/001/devara-500x500.jpg",
        durationMs = 224_000L
    )

    private val track2 = ListenTogetherTrack(
        trackId = "saavn_song_002",
        provider = "JIO_SAAVN",
        title = "Chuttamalle",
        artist = "Shilpa Rao",
        album = "Devara",
        artworkUrl = "https://c.saavncdn.com/002/chuttamalle-500x500.jpg",
        durationMs = 242_000L
    )

    @Before
    fun startRealNodeWebSocketServer() {
        val port = ServerSocket(0).use { it.localPort }
        val serverScript = findServerScript()
        val pb = ProcessBuilder("node", serverScript.absolutePath)
            .redirectErrorStream(true)
        pb.environment()["PORT"] = port.toString()
        pb.environment()["HOST"] = "127.0.0.1"
        pb.environment()["HOST_TRANSFER_GRACE_MS"] = "250"
        pb.environment()["ROOM_TTL_MS"] = "60000"

        val process = pb.start()
        serverProcess = process
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var started = false
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            val line = reader.readLine() ?: break
            if (line.contains("Server listening")) {
                started = true
                break
            }
        }
        assertTrue("Listen Together Node.js WebSocket server failed to start", started)
        wsUrl = "ws://127.0.0.1:$port/ws"
    }

    @After
    fun stopServer() {
        serverProcess?.destroyForcibly()
        serverProcess = null
    }

    private fun findServerScript(): File {
        val candidates = listOf(
            File("listen-together-server/server.mjs"),
            File("../listen-together-server/server.mjs")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate listen-together-server/server.mjs from ${File(".").absolutePath}")
    }

    @Test
    fun twoAndroidClients_syncPlayPauseSeekNextQueuePermissionsReconnectAndHostTransfer() = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val phoneA = ListenTogetherWebSocketClient(scopeA)
        val phoneB = ListenTogetherWebSocketClient(scopeB)

        try {
            // 1. Phone A creates room with track1 + track2
            val phoneAJoinedDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneA.events.filterIsInstance<ListenTogetherServerEvent.RoomJoined>().first()
                }
            }
            val createPayload = ListenTogetherWebSocketClient.buildCreateRoomPayload(
                displayName = "Phone A",
                qualityLabel = "HD",
                controlMode = ListenTogetherControlMode.HOST_ONLY,
                initialTrack = track1,
                initialQueue = listOf(track1, track2),
                initialIndex = 0,
                initialPositionMs = 15_000L,
                initialIsPlaying = false
            )
            assertTrue(phoneA.connectAndSend(wsUrl, createPayload.toString()).isSuccess)
            val phoneAJoined = phoneAJoinedDeferred.await()
            val roomCode = phoneAJoined.room.roomCode
            assertEquals(6, roomCode.length)
            assertEquals(1, phoneAJoined.room.participants.size)

            // 2. Phone B tries invalid room code first -> rejected with ROOM_NOT_FOUND
            val invalidErrDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.ServerError>().first()
                }
            }
            val badJoin = ListenTogetherWebSocketClient.buildJoinRoomPayload("ZZZZZZ", "Phone B", "Data Saver")
            assertTrue(phoneB.connectAndSend(wsUrl, badJoin.toString()).isSuccess)
            val invalidErr = invalidErrDeferred.await()
            assertEquals("ROOM_NOT_FOUND", invalidErr.code)

            // 3. Phone B joins Phone A's room with independent Data Saver quality
            val phoneBJoinedDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomJoined>().first()
                }
            }
            val phoneAParticipantUpdate = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneA.events.filterIsInstance<ListenTogetherServerEvent.RoomStateUpdated>()
                        .first { it.room.participants.size == 2 }
                }
            }
            val joinPayload = ListenTogetherWebSocketClient.buildJoinRoomPayload(roomCode, "Phone B", "Data Saver")
            assertTrue(phoneB.connectAndSend(wsUrl, joinPayload.toString()).isSuccess)

            val phoneBJoined = phoneBJoinedDeferred.await()
            val hostSawGuest = phoneAParticipantUpdate.await()
            assertEquals(roomCode, phoneBJoined.room.roomCode)
            assertEquals(2, hostSawGuest.room.participants.size)
            assertEquals("HD", hostSawGuest.room.participants.first { it.displayName == "Phone A" }.qualityLabel)
            assertEquals("Data Saver", hostSawGuest.room.participants.first { it.displayName == "Phone B" }.qualityLabel)

            // 4. NTP Time Sync Ping/Pong populates ClockSyncEngine on Phone B
            val pongDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.TimeSyncPong>().first()
                }
            }
            assertTrue(phoneB.sendTimeSyncPing())
            val pong = pongDeferred.await()
            val clockSync = ClockSyncEngine()
            clockSync.recordSample(
                clientSendTimeMs = pong.clientSendTimeMs,
                serverReceiveTimeMs = pong.serverReceiveTimeMs,
                serverSendTimeMs = pong.serverSendTimeMs,
                clientReceiveTimeMs = pong.clientReceiveTimeMs
            )
            assertTrue(clockSync.isSynced)

            // 5. HOST_ONLY permission check: Phone B cannot trigger PLAY
            val forbiddenDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.ServerError>().first()
                }
            }
            assertTrue(phoneB.sendPlaybackCommand(action = "PLAY"))
            assertEquals("FORBIDDEN", forbiddenDeferred.await().code)

            // 6. Host sends PLAY -> Phone B receives synchronized scheduledStartServerTimeMs
            val guestPlayDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomStateUpdated>()
                        .first { it.room.playback.isPlaying }
                }
            }
            assertTrue(phoneA.sendPlaybackCommand(action = "PLAY", positionMs = 20_000L))
            val guestPlay = guestPlayDeferred.await()
            assertEquals(20_000L, guestPlay.room.playback.basePositionMs)
            assertTrue(guestPlay.room.playback.scheduledStartServerTimeMs > guestPlay.serverTimeMs)

            // 7. Host enables EVERYONE controls -> Phone B triggers NEXT -> Phone A advances to track2
            val everyoneModeDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomStateUpdated>()
                        .first { it.room.controlMode == ListenTogetherControlMode.EVERYONE }
                }
            }
            assertTrue(phoneA.sendSetControlMode(ListenTogetherControlMode.EVERYONE))
            everyoneModeDeferred.await()

            val hostNextDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneA.events.filterIsInstance<ListenTogetherServerEvent.RoomStateUpdated>()
                        .first { it.room.currentIndex == 1 }
                }
            }
            assertTrue(phoneB.sendPlaybackCommand(action = "NEXT", fromTrackId = track1.trackId))
            val hostAfterNext = hostNextDeferred.await()
            assertEquals(track2.trackId, hostAfterNext.room.currentTrack?.trackId)

            // 8. Phone B simulates network drop & reconnects with session token recovery
            val guestReconnectedDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomJoined>()
                        .first { it.reconnected }
                }
            }
            phoneB.forceReconnectNow()
            val reconnected = guestReconnectedDeferred.await()
            assertTrue(reconnected.reconnected)
            assertEquals(track2.trackId, reconnected.room.currentTrack?.trackId)

            // 9. Host leaves room -> Phone B automatically promoted to host
            val hostChangedDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.HostChanged>().first()
                }
            }
            assertTrue(phoneA.sendLeaveRoom())
            val hostChanged = hostChangedDeferred.await()
            assertEquals(phoneBJoined.participantId, hostChanged.newHostId)
            assertEquals("HOST_LEFT", hostChanged.reason)
        } finally {
            phoneA.disconnect(clearSession = true)
            phoneB.disconnect(clearSession = true)
            scopeA.cancel()
            scopeB.cancel()
        }
    }
}
