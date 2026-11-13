package com.usman.musicplayer.data.repository.providers

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.data.remote.MusicApiService
import com.usman.musicplayer.sdk.AlbumSearchResult
import com.usman.musicplayer.sdk.PlaybackType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@RunWith(RobolectricTestRunner::class)
class ITunesMusicProviderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var provider: ITunesMusicProvider

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MusicApiService::class.java)

        provider = ITunesMusicProvider(apiService)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testLookupAlbumParsesOrderedTracksAndOfficialExpectedCount() = runBlocking {
        val lookupJson = """
            {
                "resultCount": 4,
                "results": [
                    {
                        "wrapperType": "collection",
                        "collectionType": "Album",
                        "collectionId": 555001,
                        "collectionName": "Animal (Original Motion Picture Soundtrack)",
                        "artistName": "Pritam, Vishal Mishra",
                        "trackCount": 3,
                        "releaseDate": "2023-12-01T08:00:00Z",
                        "artworkUrl100": "http://example.com/100x100bb.jpg",
                        "primaryGenreName": "Soundtrack"
                    },
                    {
                        "wrapperType": "track",
                        "trackId": 1002,
                        "collectionId": 555001,
                        "collectionName": "Animal (Original Motion Picture Soundtrack)",
                        "trackName": "Pehle Bhi Main",
                        "artistName": "Vishal Mishra",
                        "trackNumber": 2,
                        "discNumber": 1,
                        "previewUrl": "http://audio.itunes.example/2.m4a",
                        "trackTimeMillis": 250000
                    },
                    {
                        "wrapperType": "track",
                        "trackId": 1001,
                        "collectionId": 555001,
                        "collectionName": "Animal (Original Motion Picture Soundtrack)",
                        "trackName": "Hua Main",
                        "artistName": "Pritam, Raghav Chaitanya",
                        "trackNumber": 1,
                        "discNumber": 1,
                        "previewUrl": "http://audio.itunes.example/1.m4a",
                        "trackTimeMillis": 270000
                    },
                    {
                        "wrapperType": "track",
                        "trackId": 1003,
                        "collectionId": 555001,
                        "collectionName": "Animal (Original Motion Picture Soundtrack)",
                        "trackName": "Satranga",
                        "artistName": "Arijit Singh",
                        "trackNumber": 3,
                        "discNumber": 1,
                        "previewUrl": "http://audio.itunes.example/3.m4a",
                        "trackTimeMillis": 271000
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(lookupJson).setResponseCode(200))

        val albumRef = AlbumSearchResult(
            id = "555001",
            title = "Animal (Original Motion Picture Soundtrack)",
            artist = "Pritam",
            sourceProvider = "iTunes"
        )
        val details = provider.getAlbumDetails(albumRef).getOrNull()
        assertNotNull(details)
        assertTrue(details!!.isComplete)
        assertEquals(3, details.officialExpectedCount)
        assertEquals("Animal", details.movieName)
        assertEquals("2023", details.year)
        assertEquals("https://example.com/600x600bb.jpg", details.artworkUri)
        assertEquals(listOf("Hua Main", "Pehle Bhi Main", "Satranga"), details.tracks.map { it.title })
        assertEquals(PlaybackType.PREVIEW, details.tracks[0].playbackType)
        assertEquals("https://audio.itunes.example/1.m4a", details.tracks[0].contentUriString)
    }

    @Test
    fun testLookupAlbumMarksPartialWhenTracksMissingFromOfficialCount() = runBlocking {
        val partialLookup = """
            {
                "resultCount": 2,
                "results": [
                    {
                        "wrapperType": "collection",
                        "collectionId": 777002,
                        "collectionName": "Pushpa 2 The Rule (Soundtrack)",
                        "artistName": "Devi Sri Prasad",
                        "trackCount": 6
                    },
                    {
                        "wrapperType": "track",
                        "trackId": 2001,
                        "collectionId": 777002,
                        "collectionName": "Pushpa 2 The Rule (Soundtrack)",
                        "trackName": "Pushpa Pushpa",
                        "artistName": "Devi Sri Prasad",
                        "trackNumber": 1,
                        "previewUrl": "https://audio.itunes.example/p1.m4a"
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(partialLookup).setResponseCode(200))

        val details = provider.getAlbumDetails(
            AlbumSearchResult(id = "777002", title = "Pushpa 2", artist = "DSP", sourceProvider = "iTunes")
        ).getOrNull()

        assertNotNull(details)
        assertFalse(details!!.isComplete)
        assertEquals(6, details.officialExpectedCount)
        assertEquals(1, details.discoveredCount)
    }
}
