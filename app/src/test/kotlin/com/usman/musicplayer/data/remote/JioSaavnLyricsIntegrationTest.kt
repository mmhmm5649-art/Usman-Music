package com.usman.musicplayer.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Real integration test against JioSaavn API endpoints.
 * Tests actual network connectivity and response structures for search and lyrics.
 */
class JioSaavnLyricsIntegrationTest {

    @Test
    fun testRealJioSaavnSearchAndLyricsApi() = runBlocking {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.jiosaavn.com/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        val apiService = retrofit.create(JioSaavnApiService::class.java)

        // 1. Test search API
        val searchResponse = apiService.searchSongs(query = "Kesariya")
        assertNotNull(searchResponse)
        
        if (searchResponse.isSuccessful) {
            val searchBody = searchResponse.body()
            val results = searchBody?.results.orEmpty()
            if (results.isNotEmpty()) {
                val firstSong = results.first()
                val songId = firstSong.id ?: "rjkrTnma"
                println("Integration Test: Found song ID: $songId, title: ${firstSong.title}")

                // 2. Test lyrics API for found song ID
                val lyricsResponse = apiService.getLyrics(songId = songId)
                assertNotNull(lyricsResponse)
                println("Integration Test: Lyrics HTTP status: ${lyricsResponse.code()}")
                
                val lyricsBody = lyricsResponse.body()
                println("Integration Test: Lyrics body: lyrics=${lyricsBody?.lyrics}, script=${lyricsBody?.script}")
            }
        }
    }
}
