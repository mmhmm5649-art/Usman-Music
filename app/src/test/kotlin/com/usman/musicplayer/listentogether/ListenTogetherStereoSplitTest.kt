package com.usman.musicplayer.listentogether

import com.usman.musicplayer.data.listentogether.ListenTogetherControlMode
import com.usman.musicplayer.data.listentogether.ListenTogetherListeningMode
import com.usman.musicplayer.data.listentogether.ListenTogetherServerEvent
import com.usman.musicplayer.data.listentogether.ListenTogetherSpeakerRole
import com.usman.musicplayer.data.listentogether.ListenTogetherTrack
import com.usman.musicplayer.data.listentogether.ListenTogetherWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 * End-to-end Android WebSocket client <-> real Node.js Listen Together server test
 * verifying Stereo Split Speaker Mode (2-device limit, LEFT/RIGHT channel assignment,
 * swap, 3rd device rejection, and returning to Normal Stereo mode).
 */
class ListenTogetherStereoSplitTest {

    private var serverProcess: Process? = null
    private var wsUrl: String = ""

    private val sampleTrack = ListenTogetherTrack(
        trackId = "saavn_stereo_001",
        provider = "JIO_SAAVN",
        title = "Bohemian Stereo Test",
        artist = "Studio Band",
        album = "Acoustic Sessions",
        artworkUrl = "https://c.saavncdn.com/001/stereo-500x500.jpg",
        durationMs = 240_000L
    )

    @Before
    fun startRealNodeServer() {
        val port = ServerSocket(0).use { it.localPort }
        val candidates = listOf(
            File("listen-together-server/server.mjs"),
            File("../listen-together-server/server.mjs")
        )
        val serverScript = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate listen-together-server/server.mjs")

        val pb = ProcessBuilder("node", serverScript.absolutePath).redirectErrorStream(true)
        pb.environment()["PORT"] = port.toString()
        pb.environment()["HOST"] = "127.0.0.1"
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
        assertTrue("Node.js Listen Together server failed to start", started)
        wsUrl = "ws://127.0.0.1:$port/ws"
    }

    @After
    fun stopServer() {
        serverProcess?.destroyForcibly()
        serverProcess = null
    }

    @Test
    fun stereoSplitMode_assignsLeftRight_swaps_rejectsThirdDevice_andRestoresNormalStereo() = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scopeC = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val phoneA = ListenTogetherWebSocketClient(scopeA)
        val phoneB = ListenTogetherWebSocketClient(scopeB)
        val phoneC = ListenTogetherWebSocketClient(scopeC)

        try {
            // 1. Phone A creates Normal room (default 10 max participants)
            val joinedADeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) { phoneA.events.filterIsInstance<ListenTogetherServerEvent.RoomJoined>().first() }
            }
            val createPayload = ListenTogetherWebSocketClient.buildCreateRoomPayload(
                displayName = "Phone A",
                qualityLabel = "HD",
                controlMode = ListenTogetherControlMode.HOST_ONLY,
                initialTrack = sampleTrack,
                initialQueue = listOf(sampleTrack),
                initialIndex = 0,
                initialPositionMs = 5_000L,
                initialIsPlaying = true
            )
            assertTrue(phoneA.connectAndSend(wsUrl, createPayload.toString()).isSuccess)
            val joinedA = joinedADeferred.await()
            assertEquals(ListenTogetherListeningMode.NORMAL, joinedA.room.listeningMode)
            assertEquals(10, joinedA.room.maxParticipants)
            assertEquals(ListenTogetherSpeakerRole.STEREO, joinedA.room.participants.first().speakerRole)

            // 2. Phone B joins with independent Data Saver quality
            val joinedBDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) { phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomJoined>().first() }
            }
            val joinB = ListenTogetherWebSocketClient.buildJoinRoomPayload(joinedA.room.roomCode, "Phone B", "Data Saver")
            assertTrue(phoneB.connectAndSend(wsUrl, joinB.toString()).isSuccess)
            val joinedB = joinedBDeferred.await()

            // 3. Host enables STEREO_SPLIT mode -> Phone A gets LEFT, Phone B gets RIGHT
            val phoneBSplitDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomStateUpdated>()
                        .first { it.room.listeningMode == ListenTogetherListeningMode.STEREO_SPLIT }
                }
            }
            assertTrue(phoneA.sendSetListeningMode(ListenTogetherListeningMode.STEREO_SPLIT))
            val splitRoom = phoneBSplitDeferred.await().room
            assertEquals(2, splitRoom.maxParticipants)
            assertEquals(
                ListenTogetherSpeakerRole.LEFT,
                splitRoom.participants.first { it.participantId == joinedA.participantId }.speakerRole
            )
            assertEquals(
                ListenTogetherSpeakerRole.RIGHT,
                splitRoom.participants.first { it.participantId == joinedB.participantId }.speakerRole
            )
            // Independent streaming qualities preserved
            assertEquals("HD", splitRoom.participants.first { it.participantId == joinedA.participantId }.qualityLabel)
            assertEquals("Data Saver", splitRoom.participants.first { it.participantId == joinedB.participantId }.qualityLabel)

            // 4. Phone C attempting to join STEREO_SPLIT room is rejected with STEREO_ROOM_FULL
            val thirdRejectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) { phoneC.events.filterIsInstance<ListenTogetherServerEvent.ServerError>().first() }
            }
            val joinC = ListenTogetherWebSocketClient.buildJoinRoomPayload(joinedA.room.roomCode, "Phone C", "HD")
            assertTrue(phoneC.connectAndSend(wsUrl, joinC.toString()).isSuccess)
            assertEquals("STEREO_ROOM_FULL", thirdRejectedDeferred.await().code)

            // 5. Swap LEFT / RIGHT roles atomically
            val phoneBSwappedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomStateUpdated>()
                        .first {
                            it.room.participants.firstOrNull { p -> p.participantId == joinedB.participantId }?.speakerRole ==
                                ListenTogetherSpeakerRole.LEFT
                        }
                }
            }
            assertTrue(
                phoneA.sendAssignSpeakerRole(
                    targetParticipantId = joinedA.participantId,
                    role = ListenTogetherSpeakerRole.RIGHT,
                    swap = true
                )
            )
            val swappedRoom = phoneBSwappedDeferred.await().room
            assertEquals(
                ListenTogetherSpeakerRole.RIGHT,
                swappedRoom.participants.first { it.participantId == joinedA.participantId }.speakerRole
            )
            assertEquals(
                ListenTogetherSpeakerRole.LEFT,
                swappedRoom.participants.first { it.participantId == joinedB.participantId }.speakerRole
            )

            // 6. Switch back to NORMAL -> restores STEREO on both devices & 10-device cap
            val restoredNormalDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000L) {
                    phoneB.events.filterIsInstance<ListenTogetherServerEvent.RoomStateUpdated>()
                        .first { it.room.listeningMode == ListenTogetherListeningMode.NORMAL }
                }
            }
            assertTrue(phoneA.sendSetListeningMode(ListenTogetherListeningMode.NORMAL))
            val normalRoom = restoredNormalDeferred.await().room
            assertEquals(10, normalRoom.maxParticipants)
            assertTrue(normalRoom.participants.all { it.speakerRole == ListenTogetherSpeakerRole.STEREO })
        } finally {
            phoneA.disconnect(clearSession = true)
            phoneB.disconnect(clearSession = true)
            phoneC.disconnect(clearSession = true)
            scopeA.cancel()
            scopeB.cancel()
            scopeC.cancel()
        }
    }
}
