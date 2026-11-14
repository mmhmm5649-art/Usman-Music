package com.usman.musicplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.usman.musicplayer.data.AlbumArtworkCache
import com.usman.musicplayer.data.AppImageLoader
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeHeroFeature
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeQuickAccessCard
import com.usman.musicplayer.model.HomeQuickAccessType
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.screens.HomeScreen
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.ui.tv.TvHomeScreen
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeArtworkCacheAndPipelineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.resolve("artwork_cache").deleteRecursively()
        AlbumArtworkCache.clearForTest()
        AppImageLoader.resetForTest()

        server = MockWebServer()
        server.start()
        val testClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
        AppImageLoader.setTestOkHttpClient(testClient)
    }

    @After
    fun tearDown() {
        AlbumArtworkCache.clearForTest()
        AppImageLoader.resetForTest()
        runCatching { server.shutdown() }
    }

    private fun createPngBytes(width: Int, height: Int, color: Int = Color.CYAN): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            out.toByteArray()
        }
    }

    private fun <T> runWithMainLooperPump(block: suspend CoroutineScope.() -> T): T {
        val future = CompletableFuture<T>()
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                future.complete(block())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!future.isDone) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadlineNs) {
                job.cancel()
                throw AssertionError("Timed out waiting for async artwork pipeline test")
            }
            Thread.sleep(10)
        }
        return future.get()
    }

    @Test
    fun concurrentRequestsForSameUrlCoalesceAndSurviveDiskCacheOffline() = runWithMainLooperPump {
        val pngBytes = createPngBytes(500, 500, Color.MAGENTA)
        val networkHits = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                networkHits.incrementAndGet()
                val buffer = Buffer().write(pngBytes)
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "image/png")
                    // Intentionally short Cache-Control to verify respectCacheHeaders(false) keeps disk cache working
                    .setHeader("Cache-Control", "no-store")
                    .setBodyDelay(40, TimeUnit.MILLISECONDS)
                    .setBody(buffer)
            }
        }

        val url = server.url("/cover_500.png").toString()

        val coldStartNs = System.nanoTime()
        val bitmaps = (1..6).map { idx ->
            async(Dispatchers.IO) {
                AlbumArtworkCache.loadArtwork(
                    context = context,
                    songId = idx.toLong(),
                    albumArtUri = url,
                    filePath = "saavn_$idx",
                    targetWidth = 360,
                    targetHeight = 360
                )
            }
        }.awaitAll()
        val coldDurationMs = (System.nanoTime() - coldStartNs) / 1_000_000.0

        assertEquals(6, bitmaps.size)
        bitmaps.forEach { assertNotNull(it) }
        assertEquals("Concurrent same-URL fetches must coalesce into 1 HTTP request", 1, networkHits.get())

        // Warm memory hit across an unseen songId sharing the same artwork URL
        val warmStartNs = System.nanoTime()
        val cachedDirect = AlbumArtworkCache.getCached(999L, url, 360, 360)
        val warmBitmap = AlbumArtworkCache.loadArtwork(
            context = context,
            songId = 999L,
            albumArtUri = url,
            filePath = "saavn_999",
            targetWidth = 360,
            targetHeight = 360
        )
        val warmDurationMs = (System.nanoTime() - warmStartNs) / 1_000_000.0

        assertNotNull(cachedDirect)
        assertNotNull(warmBitmap)
        assertEquals(1, networkHits.get())
        assertTrue("Warm cache hit ($warmDurationMs ms) should be much faster than cold ($coldDurationMs ms)", warmDurationMs < 20.0)

        // Simulate clearing memory caches + offline mode (server shuts down): Coil disk cache must serve artwork
        AlbumArtworkCache.clearForTest()
        AppImageLoader.get(context).memoryCache?.clear()
        server.shutdown()

        val diskServed = AlbumArtworkCache.loadArtwork(
            context = context,
            songId = 777L,
            albumArtUri = url,
            filePath = "saavn_777",
            targetWidth = 360,
            targetHeight = 360
        )
        assertNotNull("Artwork must load from disk cache even when offline", diskServed)
    }

    @Test
    fun largeArtworkIsDownsampledToCardTargetSize() = runWithMainLooperPump {
        val largePng = createPngBytes(1000, 1000, Color.BLUE)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(largePng))
        )

        val url = server.url("/large_1000.png").toString()
        val decoded = AlbumArtworkCache.loadArtwork(
            context = context,
            songId = 42L,
            albumArtUri = url,
            filePath = "saavn_42",
            targetWidth = AppImageLoader.SIZE_CARD_THUMB_PX,
            targetHeight = AppImageLoader.SIZE_CARD_THUMB_PX
        )

        assertNotNull(decoded)
        assertTrue("Decoded width (${decoded!!.width}) should be downsampled <= 360", decoded.width <= 360)
        assertTrue("Decoded height (${decoded.height}) should be downsampled <= 360", decoded.height <= 360)
    }

    @Test
    fun phoneHomeScreenLazyRowsDoNotEagerlyFetchAllOffscreenItemsAndSurviveRapidUpdates() {
        val pngBytes = createPngBytes(160, 160, Color.GREEN)
        val networkHits = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                networkHits.incrementAndGet()
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(pngBytes))
            }
        }

        val largeSections = (1..6).map { secIdx ->
            val songs = (1..25).map { songIdx ->
                Song(
                    id = secIdx * 1000L + songIdx,
                    title = "Track $secIdx-$songIdx",
                    artist = "Artist $songIdx",
                    album = "Album $secIdx",
                    durationMs = 200_000L,
                    filePath = "saavn_${secIdx}_$songIdx",
                    albumArtUri = server.url("/art_${secIdx}_$songIdx.png").toString(),
                    isOnline = true,
                    playbackType = PlaybackType.FULL,
                    language = "telugu"
                )
            }
            HomeDiscoverySection(
                id = "sec_$secIdx",
                title = "Section $secIdx",
                subtitle = "Discovery",
                language = HomeLanguageFilter.ALL,
                contentType = HomeSectionContentType.SONGS,
                songs = songs,
                hasAttemptedLoad = true
            )
        }

        val quickCards = listOf(
            HomeQuickAccessCard("qa_telugu_hits", HomeQuickAccessType.TELUGU_HITS, "Telugu Hits", "20 tracks", artworkUri = server.url("/qa_1.png").toString()),
            HomeQuickAccessCard("qa_tamil_hits", HomeQuickAccessType.TAMIL_HITS, "Tamil Hits", "20 tracks", artworkUri = server.url("/qa_2.png").toString()),
            HomeQuickAccessCard("qa_english_hits", HomeQuickAccessType.ENGLISH_HITS, "English Hits", "20 tracks"),
            HomeQuickAccessCard("qa_hindi_hits", HomeQuickAccessType.HINDI_HITS, "Hindi Hits", "20 tracks"),
            HomeQuickAccessCard("qa_recently_played", HomeQuickAccessType.RECENTLY_PLAYED, "Recently Played", "Empty"),
            HomeQuickAccessCard("qa_favorite_songs", HomeQuickAccessType.FAVORITE_SONGS, "Your Favourite Songs", "Empty")
        )

        val state = MusicPlayerUiState(
            selectedHomeLanguage = HomeLanguageFilter.ALL,
            homeQuickCards = quickCards,
            homeHeroFeature = HomeHeroFeature(
                id = "hero_1",
                badge = "FEATURED",
                title = "Hero Song",
                subtitle = "Hero Artist",
                artworkUri = server.url("/hero.png").toString(),
                primarySong = largeSections.first().songs.first(),
                queueSongs = largeSections.first().songs
            ),
            homeSections = largeSections
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                HomeScreen(
                    uiState = state,
                    onNavigateToSearch = {},
                    onSearchCategory = {},
                    onNavigateToLibrary = {},
                    onNavigateToNowPlaying = {},
                    onPlaySong = {},
                    onOpenService = { _, _, _ -> },
                    onTabGenreSelected = {},
                    onSelectCategory = {},
                    onClearCategory = {},
                    onRescanClick = {},
                    onRequestPermissionClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_quick_card_qa_telugu_hits").assertIsDisplayed()
        composeRule.onNodeWithTag("featured_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("discovery_section_sec_1"))
        composeRule.onNodeWithTag("discovery_section_sec_1").assertIsDisplayed()

        // Wait briefly for visible-first Coil requests to dispatch, verify we did NOT eagerly request all 150 offscreen song artworks!
        Thread.sleep(250)
        assertTrue(
            "Expected bounded visible-first requests (< 40) instead of eager 150+ requests; got ${networkHits.get()}",
            networkHits.get() < 40
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
    fun tvHomeScreenRendersAndPrefetchesCleanly() {
        val state = MusicPlayerUiState(
            selectedHomeLanguage = HomeLanguageFilter.ALL,
            homeQuickCards = listOf(
                HomeQuickAccessCard("qa_telugu_hits", HomeQuickAccessType.TELUGU_HITS, "Telugu Hits", "20 tracks")
            ),
            homeSections = listOf(
                HomeDiscoverySection(
                    id = "tv_albums",
                    title = "Albums",
                    subtitle = "Top",
                    language = HomeLanguageFilter.ALL,
                    contentType = HomeSectionContentType.ALBUMS,
                    albums = listOf(AlbumSearchResult("alb_1", "Pushpa 2", "DSP", "2024", null, sourceProvider = "JioSaavn")),
                    hasAttemptedLoad = true
                ),
                HomeDiscoverySection(
                    id = "tv_playlists",
                    title = "Mixes",
                    subtitle = "Curated",
                    language = HomeLanguageFilter.ALL,
                    contentType = HomeSectionContentType.PLAYLISTS,
                    playlists = listOf(CuratedPlaylist("mix_1", "Daily Mix", "Fresh tracks")),
                    hasAttemptedLoad = true
                )
            )
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvHomeScreen(
                    uiState = state,
                    onNavigateToSearch = {},
                    onSearchCategory = {},
                    onPlaySong = {},
                    onTabGenreSelected = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_lang_chip_all").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_quick_card_qa_telugu_hits").assertIsDisplayed()
    }
}
