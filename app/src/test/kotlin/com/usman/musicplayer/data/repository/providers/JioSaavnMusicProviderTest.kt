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

        provider = JioSaavnMusicProvider(apiService, moshi)
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

    /*
    @Test
    fun testGetStreamUrlSuccess() = runBlocking {
        val detailsResponse = """
            {
                "12345": {
                    "id": "12345",
                    "encrypted_media_url": "36vM6YgG1S6Z7Y3Z4Y6Z7Y3Z4Y6Z7Y3Z4Y6Z7Y3Z4Y6Z7Y3Z"
                }
            }
        """.trimIndent()
        
        mockWebServer.enqueue(MockResponse().setBody(detailsResponse).setResponseCode(200))

        val song = Song(
            id = 1L, title = "Test", artist = "Artist", album = "Album", durationMs = 240000L,
            filePath = "12345", folderName = "JioSaavn", isOnline = true, 
            playbackType = PlaybackType.FULL, sourceProvider = "JioSaavn"
        )

        val result = provider.getStreamUrl(song)
        assertTrue(result.isSuccess)
    }
    */

    @Test
    fun testGetStreamUrlFailsWithEmptyResponseStructure() = runBlocking {
        val detailsResponse = """
            {
                "12345": {
                    "id": "12345",
                    "more_info": {
                        "something_else": "here"
                    }
                }
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
}
