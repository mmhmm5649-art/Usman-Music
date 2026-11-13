package com.usman.musicplayer.data.repository

import com.usman.musicplayer.sdk.AlbumDetails
import com.usman.musicplayer.sdk.AlbumSearchResult
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnifiedMusicRepositoryTest {

    private class FakeProvider(
        override val metadata: ProviderMetadata,
        private val songsByQuery: List<Song> = emptyList(),
        private val albumsByQuery: List<AlbumSearchResult> = emptyList(),
        private val albumDetailsById: Map<String, AlbumDetails> = emptyMap()
    ) : MusicProvider {
        override suspend fun search(query: String, limit: Int): Result<List<Song>> =
            Result.success(songsByQuery.take(limit))

        override suspend fun searchAlbums(query: String, limit: Int): Result<List<AlbumSearchResult>> =
            Result.success(albumsByQuery.take(limit))

        override suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> =
            Result.success(albumDetailsById[album.id])

        override suspend fun getStreamUrl(song: Song): Result<String> =
            Result.success(song.contentUriString ?: "https://example.com/${song.filePath}.mp4")

        override suspend fun getLyrics(song: Song): Result<String?> = Result.success(null)
    }

    private fun testSong(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        albumId: String? = null,
        provider: String = "JioSaavn"
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 210_000L,
        filePath = "pid_$id",
        folderName = provider,
        contentUriString = "https://cdn.example.com/$id.mp4",
        isOnline = true,
        playbackType = PlaybackType.FULL,
        sourceProvider = provider,
        albumId = albumId,
        releaseYear = "2025"
    )

    @Test
    fun testMovieSoundtrackSearchRanksCompleteSoundtrackAndHandlesSpellingVariation() = runBlocking {
        val officialSoundtrack = AlbumSearchResult(
            id = "alb_dhurandhar",
            title = "Dhurandhar (Original Motion Picture Soundtrack)",
            movieName = "Dhurandhar",
            artist = "Shashwat Sachdev",
            year = "2025",
            songCount = 7,
            artworkUri = "https://example.com/dhurandhar.jpg",
            sourceProvider = "JioSaavn",
            isCompleteAlbum = true
        )
        val unrelatedRemix = AlbumSearchResult(
            id = "alb_remix",
            title = "DJ Remix Hits",
            movieName = null,
            artist = "DJ Mix",
            year = "2022",
            songCount = 2,
            artworkUri = null,
            sourceProvider = "JioSaavn",
            isCompleteAlbum = true
        )

        val repo = UnifiedMusicRepository(
            context = null,
            providersOverride = listOf(
                FakeProvider(
                    metadata = ProviderMetadata("jiosaavn", "JioSaavn", "1.0", isFullPlaybackSupported = true, authRequired = false),
                    albumsByQuery = listOf(unrelatedRemix, officialSoundtrack)
                )
            )
        )

        // Exact movie + soundtrack noise words
        val exactResults = repo.searchAlbums("Dhurandhar movie soundtrack", limit = 10)
        assertTrue(exactResults.isNotEmpty())
        assertEquals("alb_dhurandhar", exactResults.first().id)

        // Close spelling variation (1-char edit distance)
        val typoResults = repo.searchAlbums("Dhurandar", limit = 10)
        assertTrue(typoResults.any { it.id == "alb_dhurandhar" })
    }

    @Test
    fun testSearchAlbumsKeepsDistinctAlbumsWithSameTitleSeparate() = runBlocking {
        val album1995 = AlbumSearchResult(
            id = "alb_1995",
            title = "Dhadkan",
            movieName = "Dhadkan",
            artist = "Nadeem-Shravan",
            year = "2000",
            songCount = 8,
            artworkUri = "https://example.com/1.jpg",
            sourceProvider = "JioSaavn",
            isCompleteAlbum = true
        )
        val album2025 = AlbumSearchResult(
            id = "alb_2025",
            title = "Dhadkan",
            movieName = "Dhadkan",
            artist = "New Composer",
            year = "2025",
            songCount = 5,
            artworkUri = "https://example.com/2.jpg",
            sourceProvider = "JioSaavn",
            isCompleteAlbum = true
        )

        val repo = UnifiedMusicRepository(
            context = null,
            providersOverride = listOf(
                FakeProvider(
                    metadata = ProviderMetadata("jiosaavn", "JioSaavn", "1.0", isFullPlaybackSupported = true, authRequired = false),
                    albumsByQuery = listOf(album1995, album2025)
                )
            )
        )

        val results = repo.searchAlbums("Dhadkan", limit = 10)
        assertEquals(2, results.size)
        assertEquals(setOf("alb_1995", "alb_2025"), results.map { it.id }.toSet())
    }

    @Test
    fun testInternetArchiveBroadcastNoiseIsExcludedUnlessExplicitlyQueried() = runBlocking {
        val jioSong = testSong(1L, "Nazar Lagi", artist = "Asees Kaur", album = "Single", provider = "JioSaavn")
        val radioCeylonNoise = testSong(2L, "Radio Ceylon Old Broadcast Nazar", artist = "Archive Host", album = "Radio Ceylon", provider = "Internet Archive")
        val vividhNoise = testSong(3L, "Vividh Bharati Programme", artist = "All India Radio", album = "AIR", provider = "Internet Archive")

        val repo = UnifiedMusicRepository(
            context = null,
            providersOverride = listOf(
                FakeProvider(ProviderMetadata("jiosaavn", "JioSaavn", "1.0", isFullPlaybackSupported = true, authRequired = false), songsByQuery = listOf(jioSong)),
                FakeProvider(ProviderMetadata("archive", "Internet Archive", "1.0", isFullPlaybackSupported = true, authRequired = false), songsByQuery = listOf(radioCeylonNoise, vividhNoise))
            )
        )

        val results = repo.searchAll("Nazar Lagi", limit = 10)
        assertEquals(1, results.size)
        assertEquals("JioSaavn", results.first().sourceProvider)
        assertFalse(results.any { it.title.contains("Radio Ceylon", ignoreCase = true) })
    }

    @Test
    fun testGetAlbumDetailsFiltersForeignAlbumIdsAndDeduplicates() = runBlocking {
        val validTrack1 = testSong(10L, "Opening Theme", album = "Target OST", albumId = "alb_target")
        val duplicateTrack1 = validTrack1.copy(title = "Opening Theme (Dup)")
        val foreignTrack = testSong(11L, "Wrong Album Song", album = "Other OST", albumId = "alb_other")
        val validTrack2 = testSong(12L, "Finale", album = "Target OST", albumId = "alb_target")

        val targetAlbum = AlbumSearchResult(
            id = "alb_target",
            title = "Target OST",
            movieName = "Target",
            artist = "Composer",
            year = "2025",
            songCount = 2,
            sourceProvider = "JioSaavn",
            isCompleteAlbum = true
        )
        val rawDetails = AlbumDetails(
            id = "alb_target",
            title = "Target OST",
            movieName = "Target",
            artist = "Composer",
            year = "2025",
            songCount = 2,
            artworkUri = null,
            sourceProvider = "JioSaavn",
            isComplete = true,
            tracks = listOf(validTrack1, duplicateTrack1, foreignTrack, validTrack2)
        )

        val repo = UnifiedMusicRepository(
            context = null,
            providersOverride = listOf(
                FakeProvider(
                    metadata = ProviderMetadata("jiosaavn", "JioSaavn", "1.0", isFullPlaybackSupported = true, authRequired = false),
                    albumDetailsById = mapOf("alb_target" to rawDetails)
                )
            )
        )

        val details = repo.getAlbumDetails(targetAlbum).getOrNull()
        assertNotNull(details)
        assertEquals(2, details!!.tracks.size)
        assertEquals(listOf("Opening Theme", "Finale"), details.tracks.map { it.title })
    }

    @Test
    fun testMovieSoundtrackAggregationCombinesSingleTrackAlbumsSinglesAndMultiProviders() = runBlocking {
        // Provider 1 (Full tracks): only has 1-track album for Dhurandhar + a separate single album + 1 search song
        val singleAlbumA = AlbumSearchResult(
            id = "jio_alb_1",
            title = "Dhurandhar",
            movieName = "Dhurandhar",
            artist = "Shashwat Sachdev",
            year = "2025",
            songCount = 1,
            artworkUri = "https://example.com/single_art.jpg",
            sourceProvider = "JioSaavn",
            isCompleteAlbum = false
        )
        val singleAlbumB = AlbumSearchResult(
            id = "jio_alb_2",
            title = "Ishq Jalakar (From \"Dhurandhar\")",
            movieName = "Dhurandhar",
            artist = "Shashwat Sachdev, Arijit Singh",
            year = "2025",
            songCount = 1,
            sourceProvider = "JioSaavn",
            isCompleteAlbum = false
        )

        val jioTrack1 = testSong(101L, "Dhurandhar Title Track", artist = "Hanumankind, Shashwat Sachdev", album = "Dhurandhar", albumId = "jio_alb_1", provider = "JioSaavn")
        val jioTrack2 = testSong(102L, "Ishq Jalakar (From \"Dhurandhar\")", artist = "Arijit Singh", album = "Ishq Jalakar (From \"Dhurandhar\")", albumId = "jio_alb_2", provider = "JioSaavn")
        val jioTrack3FromSearch = testSong(103L, "Gehra Hua", artist = "Jubin Nautiyal", album = "Dhurandhar", albumId = "jio_alb_3", provider = "JioSaavn")
        val jioTrack4Upgraded = testSong(104L, "Run Down The City", artist = "Reble, Shashwat Sachdev", album = "Dhurandhar", albumId = "jio_alb_4", provider = "JioSaavn")

        // Provider 2 (Hidden internal OST metadata verifier — NEVER surfaced directly!)
        val itunesOst = AlbumSearchResult(
            id = "999888",
            title = "Dhurandhar (Original Motion Picture Soundtrack)",
            movieName = "Dhurandhar",
            artist = "Shashwat Sachdev",
            year = "2025",
            songCount = 4,
            artworkUri = "https://example.com/ost_600.jpg",
            sourceProvider = "iTunes",
            isCompleteAlbum = true
        )
        val itunesTrack1 = testSong(901L, "Dhurandhar Title Track", artist = "Shashwat Sachdev", album = "Dhurandhar (Original Motion Picture Soundtrack)", albumId = "999888", provider = "iTunes")
            .copy(playbackType = PlaybackType.PREVIEW, durationMs = 30_000L, trackNumber = 1)
        val itunesTrack2 = testSong(902L, "Ishq Jalakar", artist = "Arijit Singh", album = "Dhurandhar (Original Motion Picture Soundtrack)", albumId = "999888", provider = "iTunes")
            .copy(playbackType = PlaybackType.PREVIEW, durationMs = 30_000L, trackNumber = 2)
        val itunesTrack3 = testSong(903L, "Gehra Hua", artist = "Jubin Nautiyal", album = "Dhurandhar (Original Motion Picture Soundtrack)", albumId = "999888", provider = "iTunes")
            .copy(playbackType = PlaybackType.PREVIEW, durationMs = 30_000L, trackNumber = 3)
        val itunesTrack4 = testSong(904L, "Run Down The City", artist = "Reble", album = "Dhurandhar (Original Motion Picture Soundtrack)", albumId = "999888", provider = "iTunes")
            .copy(playbackType = PlaybackType.PREVIEW, durationMs = 30_000L, trackNumber = 4)

        val jioFake = FakeProvider(
            metadata = ProviderMetadata("jiosaavn_native_provider", "JioSaavn", "2.1", isFullPlaybackSupported = true, authRequired = false),
            songsByQuery = listOf(jioTrack1, jioTrack2, jioTrack3FromSearch, jioTrack4Upgraded),
            albumsByQuery = listOf(singleAlbumA, singleAlbumB),
            albumDetailsById = mapOf(
                "jio_alb_1" to AlbumDetails("jio_alb_1", "Dhurandhar", "Dhurandhar", "Shashwat Sachdev", "2025", 1, sourceProvider = "JioSaavn", isComplete = false, tracks = listOf(jioTrack1)),
                "jio_alb_2" to AlbumDetails("jio_alb_2", "Ishq Jalakar (From \"Dhurandhar\")", "Dhurandhar", "Arijit Singh", "2025", 1, sourceProvider = "JioSaavn", isComplete = false, tracks = listOf(jioTrack2))
            )
        )
        val itunesFake = FakeProvider(
            metadata = ProviderMetadata("itunes_catalog_provider", "iTunes", "1.1", isFullPlaybackSupported = false, authRequired = false),
            songsByQuery = listOf(itunesTrack1, itunesTrack2),
            albumsByQuery = listOf(itunesOst),
            albumDetailsById = mapOf(
                "999888" to AlbumDetails(
                    id = "999888",
                    title = "Dhurandhar (Original Motion Picture Soundtrack)",
                    movieName = "Dhurandhar",
                    artist = "Shashwat Sachdev",
                    year = "2025",
                    songCount = 4,
                    artworkUri = "https://example.com/ost_600.jpg",
                    sourceProvider = "iTunes",
                    isComplete = true,
                    officialExpectedCount = 4,
                    tracks = listOf(itunesTrack1, itunesTrack2, itunesTrack3, itunesTrack4)
                )
            )
        )

        val repo = UnifiedMusicRepository(
            context = null,
            providersOverride = listOf(jioFake, itunesFake),
            internalVerifierOverride = itunesFake
        )

        val albumResults = repo.searchAlbumsAndSoundtracks("Dhurandhar", limit = 10, seedSongs = listOf(jioTrack1, jioTrack3FromSearch))
        assertTrue(albumResults.isNotEmpty())
        assertTrue(albumResults.none { it.sourceProvider.equals("iTunes", ignoreCase = true) })
        val topCard = albumResults.first()
        assertTrue(topCard.isSoundtrackCollection)
        assertEquals("JioSaavn", topCard.sourceProvider)
        assertEquals(4, topCard.songCount)
        assertTrue(topCard.isCompleteAlbum)

        val collectionDetails = repo.getAlbumDetails(topCard).getOrNull()
        assertNotNull(collectionDetails)
        assertEquals("JioSaavn", collectionDetails!!.sourceProvider)
        assertEquals(listOf("JioSaavn"), collectionDetails.contributingProviders)
        assertEquals(4, collectionDetails.tracks.size)
        assertEquals(4, collectionDetails.discoveredCount)
        assertEquals(4, collectionDetails.officialExpectedCount)
        assertTrue(collectionDetails.isComplete)
        // Must preserve FULL JioSaavn tracks only — never iTunes or 30s previews!
        assertTrue(collectionDetails.tracks.all {
            it.playbackType == PlaybackType.FULL &&
                it.sourceProvider.equals("JioSaavn", ignoreCase = true) &&
                it.durationMs > 30_000L
        })
    }

    @Test
    fun testSoundtrackCollectionIsMarkedPartialAndExcludesUnavailableItunesPreviews() = runBlocking {
        val jioTrack1 = testSong(201L, "Pushpa Pushpa", artist = "DSP", album = "Pushpa 2", albumId = "p_1", provider = "JioSaavn")
        val jioSingle = AlbumSearchResult(
            id = "p_1",
            title = "Pushpa 2",
            movieName = "Pushpa 2",
            artist = "Devi Sri Prasad",
            year = "2024",
            songCount = 1,
            sourceProvider = "JioSaavn",
            isCompleteAlbum = false
        )
        val itunesPartial = AlbumSearchResult(
            id = "it_p2",
            title = "Pushpa 2 (Original Motion Picture Soundtrack)",
            movieName = "Pushpa 2",
            artist = "Devi Sri Prasad",
            year = "2024",
            songCount = 6,
            sourceProvider = "iTunes",
            isCompleteAlbum = true
        )
        val itunesTrack2 = testSong(920L, "Sooseki", artist = "Shreya Ghoshal", album = "Pushpa 2", albumId = "it_p2", provider = "iTunes")
            .copy(playbackType = PlaybackType.PREVIEW, durationMs = 30_000L, trackNumber = 2)

        val repo = UnifiedMusicRepository(
            context = null,
            providersOverride = listOf(
                FakeProvider(
                    ProviderMetadata("jio", "JioSaavn", "1.0", true, false),
                    songsByQuery = listOf(jioTrack1),
                    albumsByQuery = listOf(jioSingle),
                    albumDetailsById = mapOf("p_1" to AlbumDetails("p_1", "Pushpa 2", "Pushpa 2", "DSP", "2024", 1, sourceProvider = "JioSaavn", isComplete = false, tracks = listOf(jioTrack1)))
                ),
                FakeProvider(
                    ProviderMetadata("itunes_catalog_provider", "iTunes", "1.0", false, false),
                    albumsByQuery = listOf(itunesPartial),
                    albumDetailsById = mapOf("it_p2" to AlbumDetails("it_p2", "Pushpa 2 (Original Motion Picture Soundtrack)", "Pushpa 2", "DSP", "2024", 6, sourceProvider = "iTunes", isComplete = false, officialExpectedCount = 6, tracks = listOf(itunesTrack2)))
                )
            )
        )

        val albums = repo.searchAlbumsAndSoundtracks("Pushpa 2", limit = 10)
        assertTrue(albums.none { it.sourceProvider.equals("iTunes", ignoreCase = true) })
        val soundtrack = albums.first { it.isSoundtrackCollection }
        val details = repo.getAlbumDetails(soundtrack).getOrNull()!!

        // Preview-only iTunes track "Sooseki" must NEVER leak into soundtrack details!
        assertEquals(1, details.tracks.size)
        assertEquals("JioSaavn", details.tracks.first().sourceProvider)
        assertTrue(details.tracks.none { it.playbackType == PlaybackType.PREVIEW || it.sourceProvider.equals("iTunes", ignoreCase = true) })
        assertEquals(1, details.discoveredCount)
        assertEquals(6, details.officialExpectedCount)
        assertFalse(details.isComplete)
        assertFalse(soundtrack.isCompleteAlbum)
    }

    @Test
    fun testOrdinarySearchExcludesItunesAndPreviewTracksCompletely() = runBlocking {
        val jioFull = testSong(301L, "Nazar Lagi", artist = "Asees Kaur", album = "Nazar Lagi", provider = "JioSaavn")
        val jioPreview = testSong(302L, "Nazar Lagi Clip", artist = "Asees Kaur", album = "Clip", provider = "JioSaavn")
            .copy(playbackType = PlaybackType.PREVIEW, durationMs = 30_000L)
        val itunesPreview = testSong(303L, "Nazar Lagi", artist = "Asees Kaur", album = "Nazar Lagi", provider = "iTunes")
            .copy(playbackType = PlaybackType.PREVIEW, durationMs = 30_000L, contentUriString = "https://audio-ssl.itunes.apple.com/preview.m4a")
        val itunesAlbum = AlbumSearchResult(
            id = "it_alb_1",
            title = "Nazar Lagi - Single",
            artist = "Asees Kaur",
            songCount = 1,
            sourceProvider = "iTunes",
            isCompleteAlbum = true
        )
        val jioAlbum = AlbumSearchResult(
            id = "jio_alb_nazar",
            title = "Nazar Lagi",
            artist = "Asees Kaur",
            songCount = 1,
            sourceProvider = "JioSaavn",
            isCompleteAlbum = true
        )

        val repo = UnifiedMusicRepository(
            context = null,
            providersOverride = listOf(
                FakeProvider(
                    ProviderMetadata("jiosaavn_native_provider", "JioSaavn", "2.1", true, false),
                    songsByQuery = listOf(jioFull, jioPreview),
                    albumsByQuery = listOf(jioAlbum)
                ),
                FakeProvider(
                    ProviderMetadata("itunes_catalog_provider", "iTunes", "1.1", false, false),
                    songsByQuery = listOf(itunesPreview),
                    albumsByQuery = listOf(itunesAlbum)
                )
            )
        )

        val songs = repo.searchAll("Nazar Lagi", limit = 10)
        assertEquals(1, songs.size)
        assertEquals(301L, songs.first().id)
        assertEquals("JioSaavn", songs.first().sourceProvider)
        assertEquals(PlaybackType.FULL, songs.first().playbackType)

        val albums = repo.searchAlbums("Nazar Lagi", limit = 10)
        assertEquals(1, albums.size)
        assertEquals("JioSaavn", albums.first().sourceProvider)
    }

    @Test
    fun testPlayAllQueueAndProviderRegistryBlockPreviewAndItunesTracks() = runBlocking {
        val jioFull = com.usman.musicplayer.model.Song(
            id = 401L,
            title = "Kesariya",
            artist = "Arijit Singh",
            album = "Brahmastra",
            durationMs = 268_000L,
            filePath = "jio_401",
            folderName = "JioSaavn",
            contentUriString = "https://aac.saavncdn.com/401/kesariya_320.mp4",
            isOnline = true,
            playbackType = com.usman.musicplayer.model.PlaybackType.FULL,
            sourceProvider = "JioSaavn",
            isStreamable = true
        )
        val itunesPreview = jioFull.copy(
            id = 402L,
            title = "Kesariya (30s Preview)",
            folderName = "iTunes Discovery",
            durationMs = 30_000L,
            contentUriString = "https://audio-ssl.itunes.apple.com/preview.m4a",
            playbackType = com.usman.musicplayer.model.PlaybackType.PREVIEW,
            sourceProvider = "iTunes"
        )
        val localTrack = com.usman.musicplayer.model.Song(
            id = 403L,
            title = "Local Track",
            artist = "Local Artist",
            album = "My Album",
            durationMs = 190_000L,
            filePath = "/storage/emulated/0/Music/track.mp3",
            isOnline = false,
            playbackType = com.usman.musicplayer.model.PlaybackType.FULL
        )

        val filteredQueue = com.usman.musicplayer.viewmodel.MusicPlayerViewModel.filterPlayableQueueTracks(
            listOf(itunesPreview, jioFull, localTrack)
        )
        assertEquals(listOf(401L, 403L), filteredQueue.map { it.id })
        assertFalse(com.usman.musicplayer.viewmodel.MusicPlayerViewModel.isAllowedUserFacingSong(itunesPreview))
        assertTrue(com.usman.musicplayer.viewmodel.MusicPlayerViewModel.isAllowedUserFacingSong(jioFull))
        assertTrue(com.usman.musicplayer.viewmodel.MusicPlayerViewModel.isAllowedUserFacingSong(localTrack))

        val streamBlocked = com.usman.musicplayer.sdk.ProviderRegistry.getStreamUrl(itunesPreview)
        assertTrue(streamBlocked.isFailure)

        val itunesAlbumBlocked = com.usman.musicplayer.sdk.ProviderRegistry.getAlbumDetails(
            AlbumSearchResult(id = "999", title = "OST", artist = "Composer", sourceProvider = "iTunes")
        )
        assertTrue(itunesAlbumBlocked.isFailure)
    }
}

