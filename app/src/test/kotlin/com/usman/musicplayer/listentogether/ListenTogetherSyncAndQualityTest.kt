package com.usman.musicplayer.listentogether

import com.usman.musicplayer.data.listentogether.AuthoritativePlaybackState
import com.usman.musicplayer.data.listentogether.ClockSyncEngine
import com.usman.musicplayer.data.listentogether.ListenTogetherQrCodec
import com.usman.musicplayer.data.listentogether.ListenTogetherWebSocketClient
import com.usman.musicplayer.data.listentogether.PlaybackDriftController
import com.usman.musicplayer.data.repository.TrackCompatibilityException
import com.usman.musicplayer.data.repository.UnifiedMusicRepository
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.model.StreamingQualitySelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTogetherSyncAndQualityTest {

    @Test
    fun clockSyncEngine_estimatesOffsetAndRejectsHighLatencyOutliers() {
        val engine = ClockSyncEngine()
        // True offset: server is +500ms ahead of client
        // Normal low-RTT samples (~40ms RTT)
        engine.recordSample(clientSendTimeMs = 1000, serverReceiveTimeMs = 1520, serverSendTimeMs = 1522, clientReceiveTimeMs = 1042)
        engine.recordSample(clientSendTimeMs = 2000, serverReceiveTimeMs = 2518, serverSendTimeMs = 2520, clientReceiveTimeMs = 2038)
        engine.recordSample(clientSendTimeMs = 3000, serverReceiveTimeMs = 3521, serverSendTimeMs = 3523, clientReceiveTimeMs = 3044)
        engine.recordSample(clientSendTimeMs = 4000, serverReceiveTimeMs = 4519, serverSendTimeMs = 4521, clientReceiveTimeMs = 4040)
        // Asymmetric mobile spike outlier (+900ms jitter, 700ms RTT)
        engine.recordSample(clientSendTimeMs = 5000, serverReceiveTimeMs = 6100, serverSendTimeMs = 6102, clientReceiveTimeMs = 5702)

        assertTrue(engine.isSynced)
        assertTrue("Expected clock offset near 500ms, got ${engine.clockOffsetMs}", engine.clockOffsetMs in 495L..505L)
        assertTrue("Expected RTT near 40ms, got ${engine.estimatedRttMs}", engine.estimatedRttMs in 30L..50L)
        assertEquals(10_500L, engine.estimatedServerTimeMs(10_000L).let { (it / 10L) * 10L })
    }

    @Test
    fun driftController_appliesDeadbandSoftSpeedNudgeHardSeekAndCooldown() {
        val controller = PlaybackDriftController(
            deadbandMs = 80L,
            softCorrectionMaxDriftMs = 450L,
            seekCooldownMs = 1500L
        )
        val playingState = AuthoritativePlaybackState(
            isPlaying = true,
            basePositionMs = 10_000L,
            anchorServerTimeMs = 1_000L,
            scheduledStartServerTimeMs = 1_000L,
            playbackRate = 1.0f
        )

        // Projected position at serverNow = 11_000ms is 20_000ms
        // 1. Within deadband (40ms drift) -> InSync
        val inSync = controller.evaluate(
            state = playingState,
            trackDurationMs = 200_000L,
            localPositionMs = 19_960L,
            localIsPlaying = true,
            serverNowMs = 11_000L,
            localNowMs = 5_000L
        )
        assertTrue(inSync is PlaybackDriftController.CorrectionAction.InSync)

        // 2. Behind by 220ms -> gentle speed up (1.04x)
        val behind = controller.evaluate(
            state = playingState,
            trackDurationMs = 200_000L,
            localPositionMs = 19_780L,
            localIsPlaying = true,
            serverNowMs = 11_000L,
            localNowMs = 5_500L
        )
        assertTrue(behind is PlaybackDriftController.CorrectionAction.AdjustSpeed)
        assertEquals(1.04f, (behind as PlaybackDriftController.CorrectionAction.AdjustSpeed).speed)

        // 3. Ahead by 260ms -> gentle slow down (0.96x)
        val ahead = controller.evaluate(
            state = playingState,
            trackDurationMs = 200_000L,
            localPositionMs = 20_260L,
            localIsPlaying = true,
            serverNowMs = 11_000L,
            localNowMs = 6_000L
        )
        assertTrue(ahead is PlaybackDriftController.CorrectionAction.AdjustSpeed)
        assertEquals(0.96f, (ahead as PlaybackDriftController.CorrectionAction.AdjustSpeed).speed)

        // 4. Large drift (900ms behind) -> HardSeek
        val largeDrift = controller.evaluate(
            state = playingState,
            trackDurationMs = 200_000L,
            localPositionMs = 19_100L,
            localIsPlaying = true,
            serverNowMs = 11_000L,
            localNowMs = 8_000L
        )
        assertTrue(largeDrift is PlaybackDriftController.CorrectionAction.HardSeek)
        assertEquals(20_000L, (largeDrift as PlaybackDriftController.CorrectionAction.HardSeek).targetPositionMs)

        // 5. Another 700ms drift inside 1500ms cooldown -> softens via speed instead of thrashing seeks
        val insideCooldown = controller.evaluate(
            state = playingState,
            trackDurationMs = 200_000L,
            localPositionMs = 19_300L,
            localIsPlaying = true,
            serverNowMs = 11_000L,
            localNowMs = 8_500L
        )
        assertTrue(insideCooldown is PlaybackDriftController.CorrectionAction.AdjustSpeed)

        // 6. Future scheduled start -> ScheduleStart with exact delay
        val futureState = playingState.copy(
            basePositionMs = 30_000L,
            scheduledStartServerTimeMs = 12_500L
        )
        val scheduled = controller.evaluate(
            state = futureState,
            trackDurationMs = 200_000L,
            localPositionMs = 0L,
            localIsPlaying = false,
            serverNowMs = 12_000L,
            localNowMs = 9_000L
        )
        assertTrue(scheduled is PlaybackDriftController.CorrectionAction.ScheduleStart)
        assertEquals(500L, (scheduled as PlaybackDriftController.CorrectionAction.ScheduleStart).delayMs)
        assertEquals(30_000L, scheduled.targetPositionMs)
    }

    @Test
    fun streamingQualitySelector_selectsIndependentTiersAndNeverFakesHd() {
        val fullVariants = listOf(
            "96kbps" to "https://aac.saavncdn.com/123/track_96.mp4",
            "160kbps" to "https://aac.saavncdn.com/123/track_160.mp4",
            "320kbps" to "https://aac.saavncdn.com/123/track_320.mp4"
        )

        val saver = StreamingQualitySelector.selectFromVariants(fullVariants, StreamingQuality.DATA_SAVER)
        val low = StreamingQualitySelector.selectFromVariants(fullVariants, StreamingQuality.LOW)
        val hd = StreamingQualitySelector.selectFromVariants(fullVariants, StreamingQuality.HD)

        assertEquals("https://aac.saavncdn.com/123/track_96.mp4", saver?.url)
        assertEquals(96, saver?.actualBitrateKbps)
        assertEquals("AAC", saver?.codecLabel)
        assertEquals("96 kbps • AAC", saver?.actualQualityLabel)
        assertNull(saver?.fallbackNotice)

        assertEquals("https://aac.saavncdn.com/123/track_160.mp4", low?.url)
        assertEquals(160, low?.actualBitrateKbps)
        assertEquals("160 kbps • AAC", low?.actualQualityLabel)
        assertNull(low?.fallbackNotice)

        assertEquals("https://aac.saavncdn.com/123/track_320.mp4", hd?.url)
        assertEquals(320, hd?.actualBitrateKbps)
        assertEquals("320 kbps HD • AAC", hd?.actualQualityLabel)
        assertNull(hd?.fallbackNotice)

        // When provider only offers up to 160 kbps, requesting HD must NEVER fake 320 kbps HD
        val limitedVariants = listOf(
            "96kbps" to "https://aac.saavncdn.com/456/track_96.mp4",
            "160kbps" to "https://aac.saavncdn.com/456/track_160.mp4"
        )
        val honestHdFallback = StreamingQualitySelector.selectFromVariants(limitedVariants, StreamingQuality.HD)
        assertNotNull(honestHdFallback)
        assertEquals(160, honestHdFallback!!.actualBitrateKbps)
        assertEquals("160 kbps • AAC", honestHdFallback.actualQualityLabel)
        assertTrue(honestHdFallback.isDowngradedFromHd)
        assertFalse(honestHdFallback.isExactMatch)
        assertNotNull(honestHdFallback.fallbackNotice)
        assertTrue(honestHdFallback.fallbackNotice!!.contains("160 kbps"))

        // Downward-only CDN adaptation never invents _320.mp4 from _96.mp4
        val noFakeUpgrade = StreamingQualitySelector.adaptVerifiedCdnUrlDownwardOnly(
            url = "https://aac.saavncdn.com/789/song_96.mp4",
            requested = StreamingQuality.HD
        )
        assertEquals("https://aac.saavncdn.com/789/song_96.mp4", noFakeUpgrade.url)
        assertEquals(96, noFakeUpgrade.actualBitrateKbps)
        assertTrue(noFakeUpgrade.isDowngradedFromHd)
        assertNotNull(noFakeUpgrade.fallbackNotice)
    }

    @Test
    fun qrCodecAndEndpointValidation_workAccuratelyAndEnforceWssForPublicHosts() {
        val invite = ListenTogetherQrCodec.parseInvitePayload(
            "usmanmusic://listen-together?code=A7K9P2&server=wss%3A%2F%2Fsync.example.com%2Fws"
        )
        assertNotNull(invite)
        assertEquals("A7K9P2", invite!!.roomCode)
        assertEquals("wss://sync.example.com/ws", invite.serverUrl)

        val fromShareText = ListenTogetherQrCodec.parseInvitePayload(
            "Join my room! Room Code: B3M8Q4"
        )
        assertEquals("B3M8Q4", fromShareText?.roomCode)
        assertNull(ListenTogetherQrCodec.parseInvitePayload("invalid"))

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val client = ListenTogetherWebSocketClient(scope)
            // Public wss:// allowed and /ws appended automatically
            assertEquals(
                "wss://usman-sync.onrender.com/ws",
                client.validateEndpointUrl("wss://usman-sync.onrender.com").getOrThrow()
            )
            // Public insecure ws:// rejected
            assertTrue(client.validateEndpointUrl("ws://usman-sync.onrender.com/ws").isFailure)
            // Local/LAN ws:// allowed for dev & testing
            assertEquals(
                "ws://127.0.0.1:8080/ws",
                client.validateEndpointUrl("ws://127.0.0.1:8080/ws").getOrThrow()
            )
            assertEquals(
                "ws://192.168.1.42:8080/ws",
                client.validateEndpointUrl("ws://192.168.1.42:8080").getOrThrow()
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun canonicalTrackResolver_rejectsLocalAndPreviewSourcesWithoutSubstitution() = runTest {
        val repo = UnifiedMusicRepository(context = null, providersOverride = emptyList())
        val localRejected = repo.resolveCanonicalListenTogetherTrack(
            trackId = "local_123",
            provider = "LOCAL",
            title = "Local Audio",
            artist = "Unknown",
            album = "",
            artworkUrl = null,
            durationMs = 180_000L,
            quality = StreamingQuality.HD
        )
        assertTrue(localRejected.isFailure)
        assertTrue(localRejected.exceptionOrNull() is TrackCompatibilityException)

        val itunesRejected = repo.resolveCanonicalListenTogetherTrack(
            trackId = "itunes_999",
            provider = "iTunes",
            title = "30s Preview",
            artist = "Artist",
            album = "",
            artworkUrl = null,
            durationMs = 30_000L,
            quality = StreamingQuality.HD
        )
        assertTrue(itunesRejected.isFailure)
        assertTrue(itunesRejected.exceptionOrNull() is TrackCompatibilityException)
    }
}
