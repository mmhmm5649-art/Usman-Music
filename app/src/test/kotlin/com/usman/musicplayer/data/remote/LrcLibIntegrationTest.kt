package com.usman.musicplayer.data.remote

import com.usman.musicplayer.data.repository.providers.LrcLibMusicProvider
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class LrcLibIntegrationTest {

    @Test
    fun testLrclibIntegrationForRequiredSongs() = runBlocking {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        val apiService = retrofit.create(LrcLibApiService::class.java)
        val provider = LrcLibMusicProvider(apiService)

        val testSongs = listOf(
            Song(1L, "Aasa Kooda", "Sai Abhyankkar", "Aasa Kooda", 229000L, "1"),
            Song(2L, "Kesariya", "Arijit Singh", "Brahmastra", 268000L, "2"),
            Song(3L, "Pavazha Malli", "Sai Abhyankkar", "Pavazha Malli", 253000L, "3"),
            Song(4L, "Pavazha Malli", "Vivek, Sai Abhyankkar", "Think Indie", 253000L, "4")
        )

        for (song in testSongs) {
            println("=== Testing LRCLIB for: '${song.title}' by '${song.artist}' ===")
            val result = provider.getLyrics(song)
            assertTrue(result.isSuccess)
            val lyrics = result.getOrNull()
            assertNotNull(lyrics)
            assertTrue("Lyrics should not be blank for ${song.title}", !lyrics.isNullOrBlank())
            println("SUCCESS: Retrieved lyrics for '${song.title}' by '${song.artist}' (Length: ${lyrics?.length})")
            println("Snippet: ${lyrics?.take(100)}...")
        }
    }
}
