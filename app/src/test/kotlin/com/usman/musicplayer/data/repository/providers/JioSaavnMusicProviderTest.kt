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
}

