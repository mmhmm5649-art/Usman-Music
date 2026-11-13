package com.usman.musicplayer.data.repository.providers

import com.usman.musicplayer.data.remote.JioSaavnApiService
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.sdk.PlaybackType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@RunWith(RobolectricTestRunner::class)
class JioSaavnMusicProviderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var provider: JioSaavnMusicProvider
    private lateinit var apiService: JioSaavnApiService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JioSaavnApiService::class.java)

        provider = JioSaavnMusicProvider(apiService, moshi, publicApiBases = emptyList())
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testSearchSongsSuccess() = runBlocking {
        val jsonResponse = """
            {
                "results": [
                    {
                        "id": "12345",
                        "song": "Test Song",
                        "album": "Test Album",
                        "primary_artists": "Test Artist",
                        "image": "http://example.com/image.jpg",
                        "duration": "240",
                        "perma_url": "http://jiosaavn.com/song/12345"
                    }
                ],
                "total": 1
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val result = provider.search("Test", 10)
        assertTrue(result.isSuccess)
        val songs = result.getOrNull()
        assertNotNull(songs)
        assertEquals(1, songs!!.size)
        assertEquals("Test Song", songs[0].title)
        assertEquals("Test Artist", songs[0].artist)
        assertEquals("JioSaavn", songs[0].sourceProvider)
        assertEquals("12345", songs[0].filePath) // PID stored here
    }

    @Test
    fun testComplexMetadataParsing() = runBlocking {
        val jsonResponse = """
            {
                "results": [
                    {
                        "id": "99999",
                        "title": "Complex Song",
                        "album": {"album": "Complex Album"},
                        "primary_artists": [{"name": "Artist One"}, {"name": "Artist Two"}],
                        "image": "http://example.com/150x150.jpg",
                        "duration": 300
                    }
                ],
                "total": 1
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val result = provider.search("Complex", 10)
        assertTrue(result.isSuccess)
        val songs = result.getOrNull()
        assertNotNull(songs)
        assertEquals(1, songs!!.size)
        assertEquals("Complex Song", songs[0].title)
        assertEquals("Artist One, Artist Two", songs[0].artist)
        assertEquals("Complex Album", songs[0].album)
        assertEquals(300000L, songs[0].durationMs)
        assertEquals("https://example.com/500x500.jpg", songs[0].albumArtUri)
    }

    @Test
    fun testGetStreamUrlSuccessWithSongsArray() = runBlocking {
        // Encrypted URL that decrypts to a valid saavncdn URL
        val detailsResponse = """
            {
                "songs": [
                    {
                        "id": "12345",
                        "title": "Test Song",
                        "more_info": {
                            "encrypted_media_url": "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyhG7Q4EE4gXPUMxXBqCfbGqS2zdigiLU7KoeGK03QQiciQQvruZRn0hw7tS9a8Gtq"
                        }
                    }
                ]
            }
        """.trimIndent()
        
        mockWebServer.enqueue(MockResponse().setBody(detailsResponse).setResponseCode(200))

        val song = Song(
            id = 1L, title = "Test Song", artist = "Artist", album = "Album", durationMs = 240000L,
            filePath = "12345", folderName = "JioSaavn", isOnline = true, 
            playbackType = PlaybackType.FULL, sourceProvider = "JioSaavn"
        )

        val result = provider.getStreamUrl(song)
        assertTrue(result.isSuccess)
        val url = result.getOrNull()
        assertNotNull(url)
        assertTrue(url!!.contains("saavncdn.com"))
    }

    @Test
    fun testGetStreamUrlFailsWithEmptyResponseStructure() = runBlocking {
        val detailsResponse = """
            {
                "songs": [
                    {
                        "id": "12345",
                        "more_info": {
                            "something_else": "here"
                        }
                    }
                ]
            }
        """.trimIndent()
        
        mockWebServer.enqueue(MockResponse().setBody(detailsResponse).setResponseCode(200))

        val song = Song(
            id = 1L, title = "Test", artist = "Artist", album = "Album", durationMs = 240000L,
            filePath = "12345", folderName = "JioSaavn", isOnline = true, 
            playbackType = PlaybackType.FULL, sourceProvider = "JioSaavn"
        )
        val result = provider.getStreamUrl(song)
        assertTrue(result.isFailure)
    }

    @Test
    fun testPreResolvedStreamFastPath() = runBlocking {
        val song = Song(
            id = 1L, title = "Test Song", artist = "Artist", album = "Album", durationMs = 240000L,
            filePath = "12345", folderName = "JioSaavn", isOnline = true, 
            contentUriString = "https://aac.saavncdn.com/177/sample_320.mp4",
            playbackType = PlaybackType.FULL, sourceProvider = "JioSaavn"
        )

        // MockWebServer has NO requests enqueued; if it attempts network, it will fail
        val result = provider.getStreamUrl(song)
        assertTrue(result.isSuccess)
        assertEquals("https://aac.saavncdn.com/177/sample_320.mp4", result.getOrNull())
    }

    @Test
    fun testSaavnDevSearchSuccess() = runBlocking {
        val mockBase = mockWebServer.url("/").toString().removeSuffix("/")
        val saavnDevProvider = JioSaavnMusicProvider(apiService, Moshi.Builder().add(KotlinJsonAdapterFactory()).build(), publicApiBases = listOf(mockBase))

        val saavnDevJson = """
            {
                "success": true,
                "data": {
                    "total": 1,
                    "start": 0,
                    "results": [
                        {
                            "id": "abc123",
                            "name": "Boom Boom",
                            "album": { "name": "Boom Album" },
                            "artists": { "primary": [ { "name": "Sai Abhyankkar" } ] },
                            "duration": 180,
                            "image": [
                                { "quality": "500x500", "url": "https://example.com/art_500.jpg" }
                            ],
                            "downloadUrl": [
                                { "quality": "160kbps", "url": "https://aac.saavncdn.com/160.mp4" },
                                { "quality": "320kbps", "url": "https://aac.saavncdn.com/320.mp4" }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(saavnDevJson).setResponseCode(200))

        val result = saavnDevProvider.search("Boom Boom", 10)
        assertTrue(result.isSuccess)
        val songs = result.getOrNull()
        assertNotNull(songs)
        assertEquals(1, songs!!.size)
        assertEquals("Boom Boom", songs[0].title)
        assertEquals("Sai Abhyankkar", songs[0].artist)
        assertEquals("Boom Album", songs[0].album)
        assertEquals("https://example.com/art_500.jpg", songs[0].albumArtUri)
        // Verify pre-resolved stream URL prefers 320kbps!
        assertEquals("https://aac.saavncdn.com/320.mp4", songs[0].contentUriString)
    }

    @Test
    fun testSaavnDevGetStreamUrlSuccess() = runBlocking {
        val mockBase = mockWebServer.url("/").toString().removeSuffix("/")
        val saavnDevProvider = JioSaavnMusicProvider(apiService, Moshi.Builder().add(KotlinJsonAdapterFactory()).build(), publicApiBases = listOf(mockBase))

        val detailsJson = """
            {
                "success": true,
                "data": [
                    {
                        "id": "track_99",
                        "name": "Track 99",
                        "downloadUrl": [
                            { "quality": "320kbps", "url": "https://aac.saavncdn.com/track_99_320.mp4" }
                        ]
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(detailsJson).setResponseCode(200))

        val song = Song(
            id = 99L, title = "Track 99", artist = "Artist", album = "Album", durationMs = 180000L,
            filePath = "track_99", folderName = "JioSaavn", isOnline = true, 
            contentUriString = null, // not pre-resolved
            playbackType = PlaybackType.FULL, sourceProvider = "JioSaavn"
        )

        val result = saavnDevProvider.getStreamUrl(song)
        assertTrue(result.isSuccess)
        assertEquals("https://aac.saavncdn.com/track_99_320.mp4", result.getOrNull())
    }

    @Test
    fun testSearchAlbumsPublicApiParsesSoundtrackMetadata() = runBlocking {
        val mockBase = mockWebServer.url("/").toString().removeSuffix("/")
        val albumProvider = JioSaavnMusicProvider(
            apiService,
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
            publicApiBases = listOf(mockBase)
        )

        val albumsJson = """
            {
                "success": true,
                "data": {
                    "results": [
                        {
                            "id": "alb_777",
                            "name": "Dhurandhar (Original Motion Picture Soundtrack)",
                            "year": "2025",
                            "songCount": 6,
                            "artists": { "primary": [ { "name": "Shashwat Sachdev" } ] },
                            "image": [
                                { "quality": "500x500", "url": "https://c.saavncdn.com/dhurandhar_500.jpg" }
                            ],
                            "url": "https://www.jiosaavn.com/album/dhurandhar/777"
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(albumsJson).setResponseCode(200))

        val result = albumProvider.searchAlbums("Dhurandhar", 10)
        assertTrue(result.isSuccess)
        val albums = result.getOrNull().orEmpty()
        assertEquals(1, albums.size)
        val album = albums[0]
        assertEquals("alb_777", album.id)
        assertEquals("Dhurandhar (Original Motion Picture Soundtrack)", album.title)
        assertEquals("Dhurandhar", album.movieName)
        assertEquals("Shashwat Sachdev", album.artist)
        assertEquals("2025", album.year)
        assertEquals(6, album.songCount)
        assertEquals("https://c.saavncdn.com/dhurandhar_500.jpg", album.artworkUri)
        assertTrue(album.isCompleteAlbum)
    }

    @Test
    fun testSearchAlbumsOfficialFallbackDecodesEntitiesAndExplicitMovie() = runBlocking {
        val officialJson = """
            {
                "results": [
                    {
                        "id": "alb_off_1",
                        "title": "Rock &amp; Roll &quot;Edition&quot;",
                        "image": "http://example.com/150x150.jpg",
                        "more_info": {
                            "year": "2024",
                            "song_count": "5",
                            "movie": "Rock Star &amp; Co",
                            "music": "A. R. Rahman"
                        }
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(officialJson).setResponseCode(200))

        val result = provider.searchAlbums("Rock Star", 10)
        assertTrue(result.isSuccess)
        val albums = result.getOrNull().orEmpty()
        assertEquals(1, albums.size)
        assertEquals("Rock & Roll \"Edition\"", albums[0].title)
        assertEquals("Rock Star & Co", albums[0].movieName)
        assertEquals("A. R. Rahman", albums[0].artist)
        assertEquals("2024", albums[0].year)
        assertEquals(5, albums[0].songCount)
        assertEquals("https://example.com/500x500.jpg", albums[0].artworkUri)
    }

    @Test
    fun testGetAlbumDetailsPreservesOrderDeduplicatesAndMarksComplete() = runBlocking {
        val officialAlbumDetails = """
            {
                "id": "alb_900",
                "title": "Movie Album",
                "year": "2025",
                "image": "http://example.com/150x150.jpg",
                "more_info": {
                    "movie": "Movie Album",
                    "song_count": "2",
                    "music": "Composer X"
                },
                "list": [
                    {
                        "id": "t1",
                        "song": "Track One",
                        "more_info": {
                            "album_id": "alb_900",
                            "encrypted_media_url": "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyhG7Q4EE4gXPUMxXBqCfbGqS2zdigiLU7KoeGK03QQiciQQvruZRn0hw7tS9a8Gtq",
                            "duration": "210"
                        }
                    },
                    {
                        "id": "t1",
                        "song": "Track One Duplicate",
                        "more_info": {
                            "album_id": "alb_900",
                            "duration": "210"
                        }
                    },
                    {
                        "id": "t2",
                        "song": "Track Two",
                        "more_info": {
                            "album_id": "alb_900",
                            "media_url": "http://aac.saavncdn.com/track2.mp4",
                            "duration": "185"
                        }
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(officialAlbumDetails).setResponseCode(200))

        val albumRef = com.usman.musicplayer.sdk.AlbumSearchResult(
            id = "alb_900",
            title = "Movie Album",
            artist = "Composer X",
            sourceProvider = "JioSaavn"
        )
        val result = provider.getAlbumDetails(albumRef)
        assertTrue(result.isSuccess)
        val details = result.getOrNull()
        assertNotNull(details)
        assertTrue(details!!.isComplete)
        assertEquals(2, details.tracks.size)
        assertEquals("Track One", details.tracks[0].title)
        assertEquals("Track Two", details.tracks[1].title)
        assertTrue(details.tracks[0].contentUriString?.contains("saavncdn.com") == true)
        assertEquals("https://aac.saavncdn.com/track2.mp4", details.tracks[1].contentUriString)
        assertEquals("alb_900", details.tracks[0].albumId)
        assertEquals("2025", details.tracks[0].releaseYear)
    }

    @Test
    fun testGetAlbumDetailsMarksPartialWhenDeclaredCountExceedsTracks() = runBlocking {
        val partialAlbumDetails = """
            {
                "id": "alb_partial",
                "title": "Partial OST",
                "more_info": { "song_count": "5" },
                "list": [
                    { "id": "p1", "song": "Available Single", "more_info": { "duration": "200" } }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(partialAlbumDetails).setResponseCode(200))

        val albumRef = com.usman.musicplayer.sdk.AlbumSearchResult(
            id = "alb_partial",
            title = "Partial OST",
            artist = "Various",
            sourceProvider = "JioSaavn"
        )
        val details = provider.getAlbumDetails(albumRef).getOrNull()
        assertNotNull(details)
        assertFalse(details!!.isComplete)
        assertEquals(5, details.songCount)
        assertEquals(1, details.tracks.size)
    }

    @Test
    fun testSaavnDevSearchExtractsSongLanguage() = runBlocking {
        val mockBase = mockWebServer.url("/").toString().removeSuffix("/")
        val langProvider = JioSaavnMusicProvider(
            apiService,
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
            publicApiBases = listOf(mockBase)
        )

        val json = """
            {
                "success": true,
                "data": {
                    "results": [
                        {
                            "id": "te_101",
                            "name": "Sooseki",
                            "language": "Telugu",
                            "album": { "name": "Pushpa 2" },
                            "artists": { "primary": [ { "name": "Shreya Ghoshal" } ] },
                            "duration": 250,
                            "downloadUrl": [
                                { "quality": "320kbps", "url": "https://aac.saavncdn.com/te_101_320.mp4" }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val songs = langProvider.search("Sooseki", 5).getOrNull().orEmpty()
        assertEquals(1, songs.size)
        assertEquals("telugu", songs[0].language)
    }

    @Test
    fun testSearchPlaylistsAndGetPlaylistDetailsSuccess() = runBlocking {
        val mockBase = mockWebServer.url("/").toString().removeSuffix("/")
        val playlistProvider = JioSaavnMusicProvider(
            apiService,
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
            publicApiBases = listOf(mockBase)
        )

        val searchJson = """
            {
                "success": true,
                "data": {
                    "results": [
                        {
                            "id": "pl_te_1",
                            "name": "Telugu Top Hits",
                            "subtitle": "20 Tracks",
                            "image": [
                                { "quality": "500x500", "url": "https://c.saavncdn.com/pl_te_1.jpg" }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()

        val detailsJson = """
            {
                "success": true,
                "data": {
                    "id": "pl_te_1",
                    "name": "Telugu Top Hits",
                    "description": "Most streamed Telugu hits",
                    "songs": [
                        {
                            "id": "s_1",
                            "name": "Track One",
                            "language": "telugu",
                            "artists": { "primary": [ { "name": "Sid Sriram" } ] },
                            "downloadUrl": [
                                { "quality": "320kbps", "url": "https://aac.saavncdn.com/s1.mp4" }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(searchJson).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody(detailsJson).setResponseCode(200))

        val playlists = playlistProvider.searchPlaylists("telugu", 5).getOrNull().orEmpty()
        assertEquals(1, playlists.size)
        assertEquals("pl_te_1", playlists[0].id)
        assertEquals("Telugu Top Hits", playlists[0].title)
        assertEquals("https://c.saavncdn.com/pl_te_1.jpg", playlists[0].coverArtUri)

        val details = playlistProvider.getPlaylistDetails("pl_te_1").getOrNull()
        assertNotNull(details)
        assertEquals(1, details!!.songs.size)
        assertEquals("telugu", details.songs[0].language)
        assertEquals("https://aac.saavncdn.com/s1.mp4", details.songs[0].contentUriString)
    }

    @Test
    fun testGetArtistDetailsPublicApiParsesTopSongsAlbumsAndLatestSingles() = runBlocking {
        val mockBase = mockWebServer.url("/").toString().removeSuffix("/")
        val artistProvider = JioSaavnMusicProvider(
            apiService,
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
            publicApiBases = listOf(mockBase)
        )

        val publicArtistJson = """
            {
                "success": true,
                "data": {
                    "id": "459320",
                    "name": "Arijit Singh",
                    "image": [
                        { "quality": "500x500", "url": "https://c.saavncdn.com/artists/arijit_500x500.jpg" }
                    ],
                    "topSongs": [
                        {
                            "id": "s_arijit_1",
                            "name": "Tum Hi Ho",
                            "language": "Hindi",
                            "year": "2013",
                            "album": { "id": "alb_1", "name": "Aashiqui 2" },
                            "artists": { "primary": [ { "name": "Arijit Singh" } ] },
                            "duration": 262,
                            "image": [ { "quality": "500x500", "url": "https://c.saavncdn.com/tum_hi_ho.jpg" } ],
                            "downloadUrl": [ { "quality": "320kbps", "url": "https://aac.saavncdn.com/tumhiho_320.mp4" } ]
                        }
                    ],
                    "singles": [
                        {
                            "id": "s_arijit_2",
                            "name": "O Maahi",
                            "language": "Hindi",
                            "year": "2024",
                            "album": { "id": "alb_2", "name": "Dunki" },
                            "artists": { "primary": [ { "name": "Arijit Singh" }, { "name": "Pritam" } ] },
                            "duration": 233,
                            "downloadUrl": [ { "quality": "320kbps", "url": "https://aac.saavncdn.com/omaahi_320.mp4" } ]
                        }
                    ],
                    "topAlbums": [
                        {
                            "id": "alb_2",
                            "name": "Dunki",
                            "year": "2024",
                            "songCount": 5,
                            "artists": { "primary": [ { "name": "Arijit Singh" } ] },
                            "image": [ { "quality": "500x500", "url": "https://c.saavncdn.com/dunki_500.jpg" } ]
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(publicArtistJson).setResponseCode(200))

        val ref = com.usman.musicplayer.model.OnlineArtistResult(
            id = "459320",
            name = "Arijit Singh",
            sourceProvider = "JioSaavn",
            languages = listOf("Hindi")
        )
        val result = artistProvider.getArtistDetails(ref)
        assertTrue(result.isSuccess)
        val details = result.getOrNull()
        assertNotNull(details)
        assertEquals("Arijit Singh", details!!.name)
        assertEquals("https://c.saavncdn.com/artists/arijit_500x500.jpg", details.artworkUri)
        assertEquals(1, details.popularSongs.size)
        assertEquals("Tum Hi Ho", details.popularSongs[0].title)
        assertEquals("https://aac.saavncdn.com/tumhiho_320.mp4", details.popularSongs[0].contentUriString)
        assertEquals(1, details.latestReleases.size)
        assertEquals("O Maahi", details.latestReleases[0].title)
        assertEquals(1, details.albums.size)
        assertEquals("Dunki", details.albums[0].title)
    }

    @Test
    fun testGetArtistDetailsOfficialFallbackDecodesEntitiesAndParsesPlaylists() = runBlocking {
        val officialArtistJson = """
            {
                "artistId": "456323",
                "name": "Anirudh &amp; Crew",
                "image": "http://c.saavncdn.com/artists/anirudh_150x150.jpg",
                "topSongs": [
                    {
                        "id": "an_1",
                        "song": "Chuttamalle",
                        "more_info": {
                            "album_id": "alb_devara",
                            "album": "Devara Part 1",
                            "year": "2024",
                            "language": "telugu",
                            "artistMap": {
                                "primary_artists": [ { "name": "Anirudh Ravichander" }, { "name": "Shilpa Rao" } ]
                            },
                            "media_url": "http://aac.saavncdn.com/chuttamalle.mp4",
                            "duration": "222"
                        }
                    }
                ],
                "topAlbums": [
                    {
                        "id": "alb_devara",
                        "title": "Devara Part 1",
                        "year": "2024",
                        "more_info": {
                            "music": "Anirudh Ravichander",
                            "song_count": "6",
                            "language": "telugu"
                        }
                    }
                ],
                "dedicated_artist_playlist": [
                    {
                        "id": "pl_ani_1",
                        "title": "Best of Anirudh &amp; Hits",
                        "subtitle": "JioSaavn Playlist",
                        "image": "http://c.saavncdn.com/pl_ani_150x150.jpg"
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(officialArtistJson).setResponseCode(200))

        val ref = com.usman.musicplayer.model.OnlineArtistResult(
            id = "456323",
            name = "Anirudh Ravichander",
            sourceProvider = "JioSaavn",
            languages = listOf("Telugu", "Tamil")
        )
        val details = provider.getArtistDetails(ref).getOrNull()
        assertNotNull(details)
        assertEquals("https://c.saavncdn.com/artists/anirudh_500x500.jpg", details!!.artworkUri)
        assertEquals(1, details.popularSongs.size)
        assertEquals("Chuttamalle", details.popularSongs[0].title)
        assertEquals("https://aac.saavncdn.com/chuttamalle.mp4", details.popularSongs[0].contentUriString)
        assertEquals(1, details.albums.size)
        assertEquals("Devara Part 1", details.albums[0].title)
        assertEquals(1, details.playlists.size)
        assertEquals("Best of Anirudh & Hits", details.playlists[0].title)
        assertEquals("https://c.saavncdn.com/pl_ani_500x500.jpg", details.playlists[0].coverArtUri)
    }
}




