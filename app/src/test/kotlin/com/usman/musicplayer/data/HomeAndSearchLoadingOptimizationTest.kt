package com.usman.musicplayer.data

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.usman.musicplayer.data.repository.HomeDiscoveryRepository
import com.usman.musicplayer.data.repository.HomeMusicGateway
import com.usman.musicplayer.data.repository.HomeUserSignalsStore
import com.usman.musicplayer.data.repository.UnifiedMusicRepository
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.sdk.AlbumDetails
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeAndSearchLoadingOptimizationTest {

    private fun sampleSong(
        id: Long,
        title: String,
        artist: String = "Arijit Singh",
        album: String = "Latest Hits",
        language: String = "telugu"
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 215_000L,
        filePath = "saavn_$id",
        folderName = "JioSaavn",
        albumArtUri = "https://c.saavncdn.com/test_$id.jpg",
        contentUriString = "https://aac.saavncdn.com/$id.mp4",
        isOnline = true,
        playbackType = PlaybackType.FULL,
        language = language,
        sourceProvider = "JioSaavn",
        isStreamable = true,
        isFullTrackVerified = true
    )

    private fun sampleAlbum(id: String, title: String) = AlbumSearchResult(
        id = id,
        title = title,
        movieName = title,
        artist = "Devi Sri Prasad",
        year = "2025",
        songCount = 5,
        artworkUri = "https://c.saavncdn.com/alb_$id.jpg",
        sourceProvider = "JioSaavn",
        isCompleteAlbum = true
    )

    private class TimedFakeHomeGateway(
        private val stage0DelayMs: Long = 10L,
        private val backgroundDelayMs: Long = 140L,
        private val rotatedSongPrefix: String = "Fresh"
    ) : HomeMusicGateway {
        val searchCalls = AtomicInteger(0)
        val queryCounts = ConcurrentHashMap<String, AtomicInteger>()

        override suspend fun searchSongs(query: String, limit: Int, page: Int): List<Song> {
            searchCalls.incrementAndGet()
            queryCounts.computeIfAbsent(query) { AtomicInteger(0) }.incrementAndGet()
            val isFastQuery = query.contains("trending", ignoreCase = true) ||
                query.contains("telugu", ignoreCase = true) ||
                query.contains("hits", ignoreCase = true)
            delay(if (isFastQuery) stage0DelayMs else backgroundDelayMs)
            val baseId = (query.hashCode().toLong() and 0xFFFFL) + 1000L
            return listOf(
                makeSong(baseId, "$rotatedSongPrefix Hit $query 1"),
                makeSong(baseId + 1, "$rotatedSongPrefix Hit $query 2"),
                makeSong(baseId + 2, "$rotatedSongPrefix Hit $query 3")
            )
        }

        override suspend fun searchAlbums(query: String, limit: Int, page: Int): List<AlbumSearchResult> {
            searchCalls.incrementAndGet()
            delay(backgroundDelayMs)
            return listOf(makeAlbum("alb_${query.hashCode()}", "$rotatedSongPrefix Album"))
        }

        override suspend fun searchArtists(query: String, limit: Int): List<OnlineArtistResult> {
            searchCalls.incrementAndGet()
            delay(backgroundDelayMs)
            return listOf(
                OnlineArtistResult(
                    id = "art_1",
                    name = "Sid Sriram",
                    artworkUri = "https://c.saavncdn.com/art_1.jpg",
                    sourceProvider = "JioSaavn"
                )
            )
        }

        override suspend fun searchPlaylists(query: String, limit: Int): List<CuratedPlaylist> {
            searchCalls.incrementAndGet()
            delay(stage0DelayMs)
            return listOf(
                CuratedPlaylist(
                    id = "pl_${query.hashCode()}",
                    title = "$rotatedSongPrefix Mix",
                    description = "Curated Telugu & Hindi Hits",
                    coverArtUri = "https://c.saavncdn.com/pl_1.jpg",
                    songs = listOf(makeSong(901L, "$rotatedSongPrefix Mix Track"))
                )
            )
        }

        override suspend fun getPlaylistDetails(playlist: CuratedPlaylist): CuratedPlaylist? = playlist

        private fun makeSong(id: Long, title: String) = Song(
            id = id,
            title = title,
            artist = "Shreya Ghoshal",
            album = "JioSaavn Hits",
            durationMs = 210_000L,
            filePath = "saavn_$id",
            folderName = "JioSaavn",
            albumArtUri = "https://c.saavncdn.com/$id.jpg",
            contentUriString = "https://aac.saavncdn.com/$id.mp4",
            isOnline = true,
            playbackType = PlaybackType.FULL,
            language = "telugu",
            sourceProvider = "JioSaavn",
            isStreamable = true,
            isFullTrackVerified = true
        )

        private fun makeAlbum(id: String, title: String) = AlbumSearchResult(
            id = id,
            title = title,
            artist = "Thaman S",
            year = "2025",
            songCount = 4,
            artworkUri = "https://c.saavncdn.com/$id.jpg",
            sourceProvider = "JioSaavn",
            isCompleteAlbum = true
        )
    }

    private fun awaitCondition(timeoutMs: Long = 4000L, condition: () -> Boolean): Boolean {
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            looper.idleFor(20L, TimeUnit.MILLISECONDS)
            if (condition()) return true
            Thread.sleep(10L)
        }
        looper.idleFor(20L, TimeUnit.MILLISECONDS)
        return condition()
    }

    @Test
    fun homeSnapshotCacheHydratesWarmStartWithoutSpinnerWhileDynamicRefreshKeepsContentVisible() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = HomeUserSignalsStore(app)
        val cachedTrending = HomeDiscoverySection(
            id = "discovery_trending_now",
            title = "Trending Now",
            subtitle = "Cached Trending",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            songs = listOf(sampleSong(11L, "Cached Trending Track")),
            isLoading = false,
            hasAttemptedLoad = true
        )
        val cachedTelugu = HomeDiscoverySection(
            id = "telugu_top_hits",
            title = "Telugu Top Hits",
            subtitle = "Cached Telugu",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            songs = listOf(sampleSong(12L, "Cached Telugu Track")),
            isLoading = false,
            hasAttemptedLoad = true
        )
        store.saveHomeSectionsSnapshot(listOf(cachedTrending, cachedTelugu))

        val gateway = TimedFakeHomeGateway(stage0DelayMs = 150L, backgroundDelayMs = 200L, rotatedSongPrefix = "RotatedNew")
        val vm = MusicPlayerViewModel(
            application = app,
            musicRepository = UnifiedMusicRepository(app, providersOverride = emptyList()),
            homeDiscoveryRepository = HomeDiscoveryRepository(gateway),
            autoStart = false
        )

        val hydrationElapsedMs = measureTimeMillis {
            vm.startStartupSequenceForTest()
            assertTrue(
                "Cached Home sections should hydrate immediately",
                awaitCondition(timeoutMs = 1500L) {
                    vm.uiState.value.homeSections.any { it.id == "discovery_trending_now" && it.songs.isNotEmpty() }
                }
            )
        }
        println("BENCHMARK: Warm-start Home snapshot cache hydration latency = ${hydrationElapsedMs}ms")

        val hydratedState = vm.uiState.value
        assertFalse("Home should not show full-screen initial spinner when snapshot cache exists", hydratedState.isHomeInitialLoading)
        assertTrue(hydratedState.onlineTrendingSongs.any { it.title == "Cached Trending Track" })
        assertTrue(hydratedState.onlineTeluguSongs.any { it.title == "Cached Telugu Track" })

        assertTrue(
            "Background refresh should update sections dynamically without blanking them",
            awaitCondition(timeoutMs = 3500L) {
                vm.uiState.value.onlineTrendingSongs.any { it.title.startsWith("RotatedNew Hit") }
            }
        )
        assertFalse(vm.uiState.value.homeSections.any { it.id == "discovery_trending_now" && it.songs.isEmpty() })
    }

    @Test
    fun homeStage0PopulatesAboveTheFoldBeforeStage1AndAvoidsDuplicateReload() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("usman_home_signals_prefs", 0).edit().clear().commit()

        val gateway = TimedFakeHomeGateway(stage0DelayMs = 10L, backgroundDelayMs = 220L, rotatedSongPrefix = "StageZero")
        val vm = MusicPlayerViewModel(
            application = app,
            musicRepository = UnifiedMusicRepository(app, providersOverride = emptyList()),
            homeDiscoveryRepository = HomeDiscoveryRepository(gateway),
            autoStart = false
        )

        val coldFirstRenderMs = measureTimeMillis {
            vm.loadInitialOnlineMusic()
            assertTrue(
                "Stage 0 above-the-fold sections should render fast",
                awaitCondition(timeoutMs = 2000L) {
                    !vm.uiState.value.isHomeInitialLoading &&
                        vm.uiState.value.onlineTrendingSongs.isNotEmpty()
                }
            )
        }
        println("BENCHMARK: Cold Stage-0 Home first-render latency (JVM/Robolectric) = ${coldFirstRenderMs}ms")

        awaitCondition(timeoutMs = 3000L) {
            vm.uiState.value.homeSections.count { it.hasAttemptedLoad && it.hasContent } >= 4
        }
        val settledCallCount = gateway.searchCalls.get()

        vm.loadInitialOnlineMusic()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Returning from Search should not trigger duplicate Home loads", settledCallCount, gateway.searchCalls.get())
    }

    @Test
    fun searchDebounceCancellationAndStaleResponseGuardPreventOldResultsOverwritingLatest() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val queriesExecuted = CopyOnWriteArrayList<String>()
        val slowThenFastProvider = object : MusicProvider {
            override val metadata = ProviderMetadata("jiosaavn_native_provider", "JioSaavn", "2.0", true, false)

            override suspend fun search(query: String, limit: Int): Result<List<Song>> {
                queriesExecuted.add(query)
                return when {
                    query.equals("tel", ignoreCase = true) -> {
                        delay(320L)
                        Result.success(listOf(sampleSong(501L, "Wrong Delayed Tel Track")))
                    }
                    query.equals("telugu", ignoreCase = true) -> {
                        delay(20L)
                        Result.success(listOf(sampleSong(502L, "Pushpa Telugu Hit")))
                    }
                    else -> Result.success(emptyList())
                }
            }

            override suspend fun searchAlbums(query: String, limit: Int): Result<List<AlbumSearchResult>> {
                delay(if (query.equals("tel", true)) 320L else 25L)
                return if (query.equals("telugu", true)) {
                    Result.success(listOf(sampleAlbum("alb_telugu", "Pushpa 2 OST")))
                } else {
                    Result.success(emptyList())
                }
            }

            override suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> = Result.success(null)
            override suspend fun getStreamUrl(song: Song): Result<String> = Result.success("https://aac.saavncdn.com/stream.mp4")
            override suspend fun getLyrics(song: Song): Result<String?> = Result.success(null)
        }

        val repo = UnifiedMusicRepository(app, providersOverride = listOf(slowThenFastProvider))
        val vm = MusicPlayerViewModel(app, repo, HomeDiscoveryRepository(TimedFakeHomeGateway()), autoStart = false)

        // Rapid typing below 280ms debounce must drop "t" and "te" without triggering provider
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        vm.updateSearchQuery("t")
        looper.idleFor(40L, TimeUnit.MILLISECONDS)
        vm.updateSearchQuery("te")
        looper.idleFor(40L, TimeUnit.MILLISECONDS)
        vm.updateSearchQuery("tel")
        vm.searchOnline("tel", debounceMs = 0L)
        looper.idleFor(30L, TimeUnit.MILLISECONDS)
        // Supersede slow "tel" with "telugu"
        vm.updateSearchQuery("telugu")
        vm.searchOnline("telugu", debounceMs = 0L)

        val settled = awaitCondition(timeoutMs = 2500L) {
            !vm.uiState.value.isOnlineSearching &&
                vm.uiState.value.onlineSearchResults.any { it.title == "Pushpa Telugu Hit" }
        }
        assertTrue(
            "Latest query 'telugu' results should settle rapidly (isSearching=${vm.uiState.value.isOnlineSearching}, results=${vm.uiState.value.onlineSearchResults.map { it.title }}, error=${vm.uiState.value.onlineSearchError}, queries=$queriesExecuted)",
            settled
        )

        // Advance past "tel"'s 320ms delay to verify stale response does NOT overwrite "telugu"
        looper.idleFor(400L, TimeUnit.MILLISECONDS)

        val finalState = vm.uiState.value
        assertFalse("'t' should never trigger provider call", queriesExecuted.contains("t"))
        assertFalse("'te' should never trigger provider call", queriesExecuted.contains("te"))
        assertEquals(listOf("Pushpa Telugu Hit"), finalState.onlineSearchResults.map { it.title })
        assertTrue(finalState.onlineSearchAlbums.any { it.id == "alb_telugu" })
        assertFalse(finalState.isOnlineSearching)
    }

    @Test
    fun searchProgressiveSongDeliveryCachedRepeatAndOfflineErrorHandling() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val songCalls = AtomicInteger(0)
        var simulateOffline = false

        val stagedProvider = object : MusicProvider {
            override val metadata = ProviderMetadata("jiosaavn_native_provider", "JioSaavn", "2.0", true, false)

            override suspend fun search(query: String, limit: Int): Result<List<Song>> {
                songCalls.incrementAndGet()
                if (simulateOffline) {
                    return Result.failure(IOException("Network unreachable"))
                }
                delay(15L)
                return Result.success(
                    listOf(
                        sampleSong(601L, "Devara Fear Song", language = "telugu"),
                        sampleSong(602L, "Chuttamalle", language = "telugu")
                    )
                )
            }

            override suspend fun searchAlbums(query: String, limit: Int): Result<List<AlbumSearchResult>> {
                if (simulateOffline) return Result.failure(IOException("Network unreachable"))
                delay(220L)
                return Result.success(listOf(sampleAlbum("alb_devara", "Devara Part 1")))
            }

            override suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> = Result.success(null)
            override suspend fun getStreamUrl(song: Song): Result<String> = Result.success("https://aac.saavncdn.com/stream.mp4")
            override suspend fun getLyrics(song: Song): Result<String?> = Result.success(null)
        }

        val repo = UnifiedMusicRepository(app, providersOverride = listOf(stagedProvider))
        val vm = MusicPlayerViewModel(app, repo, HomeDiscoveryRepository(TimedFakeHomeGateway()), autoStart = false)

        val coldSongsMs = measureTimeMillis {
            vm.searchOnline("Devara", debounceMs = 0L)
            assertTrue(
                "Primary songs should emit before slow album query settles",
                awaitCondition(timeoutMs = 200L) {
                    vm.uiState.value.onlineSearchResults.isNotEmpty()
                }
            )
        }
        println("BENCHMARK: Cold search progressive songs emission latency = ${coldSongsMs}ms")

        assertTrue(
            "Albums should arrive cleanly once finished",
            awaitCondition(timeoutMs = 1500L) {
                vm.uiState.value.onlineSearchAlbums.any { it.id == "alb_devara" } && !vm.uiState.value.isOnlineSearching
            }
        )

        val callsAfterCold = songCalls.get()

        vm.updateSearchQuery("")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.uiState.value.onlineSearchResults.isEmpty())
        assertFalse(vm.uiState.value.isOnlineSearching)

        val cachedSearchMs = measureTimeMillis {
            vm.updateSearchQuery("Devara")
            vm.searchOnline("Devara", debounceMs = 0L)
            assertTrue(vm.uiState.value.onlineSearchResults.isNotEmpty())
        }
        println("BENCHMARK: Cached repeat search emission latency = ${cachedSearchMs}ms")
        assertEquals("Repeat cached search must not hit network again", callsAfterCold, songCalls.get())
        assertFalse(vm.uiState.value.isOnlineSearching)

        simulateOffline = true
        vm.updateSearchQuery("OfflineQueryTest")
        vm.searchOnline("OfflineQueryTest", debounceMs = 0L)
        assertTrue(
            "Offline search should terminate loading and surface connectivity guidance",
            awaitCondition(timeoutMs = 1500L) {
                !vm.uiState.value.isOnlineSearching && vm.uiState.value.onlineSearchError != null
            }
        )
        assertTrue(
            vm.uiState.value.onlineSearchError.orEmpty().contains("connection", ignoreCase = true) ||
                vm.uiState.value.onlineSearchError.orEmpty().contains("internet", ignoreCase = true)
        )
    }
}
