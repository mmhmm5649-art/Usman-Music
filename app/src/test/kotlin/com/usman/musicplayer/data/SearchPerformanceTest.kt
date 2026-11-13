package com.usman.musicplayer.data

import com.usman.musicplayer.data.repository.LocalSearchIndexer
import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureNanoTime

class SearchPerformanceTest {

    private lateinit var indexer: LocalSearchIndexer

    @Before
    fun setUp() {
        indexer = LocalSearchIndexer()
    }

    @Test
    fun testSearchPerformanceWith10kItems() = runBlocking {
        // Generate 10,000 synthetic songs, albums, artists, folders
        val songs = (1..10000).map { i ->
            Song(
                id = i.toLong(),
                title = "Track Title $i Special Hit",
                artist = "Artist Name ${i % 500}",
                album = "Album Collection ${i % 200}",
                durationMs = 180000L,
                filePath = "/storage/emulated/0/Music/track_$i.mp3",
                folderName = "Folder_${i % 50}"
            )
        }
        val albums = (1..200).map { i ->
            Album(
                id = i.toLong(),
                title = "Album Collection $i",
                artist = "Artist Name ${i * 2}",
                songCount = 50
            )
        }
        val artists = (1..500).map { i ->
            Artist(
                name = "Artist Name $i",
                songCount = 20,
                albumCount = 2
            )
        }
        val folders = (1..50).map { i ->
            MusicFolder(
                name = "Folder_$i",
                path = "/storage/emulated/0/Music/Folder_$i",
                songCount = 200
            )
        }

        // Measure indexing time for 10,000 items
        val indexTimeMs = measureNanoTime {
            indexer.updateIndex(songs, albums, artists, folders)
        } / 1_000_000.0

        println("Indexed 10,000 songs + metadata in ${indexTimeMs}ms")
        assertEquals(10000, indexer.songCount)

        // Warm up JIT compiler
        indexer.search("Track", limit = 10)

        // Test Query 1: Single token search ("Track")
        var results1: com.usman.musicplayer.data.repository.LocalSearchResults
        val query1TimeMs = measureNanoTime {
            results1 = indexer.search("Track", limit = 100)
        } / 1_000_000.0

        println("Query 'Track' across 10,000 items returned ${results1.songs.size} results in ${query1TimeMs}ms")
        assertTrue("Search should return results", results1.songs.isNotEmpty())
        assertTrue("Search latency must be reasonable on CI (actual: ${query1TimeMs}ms)", query1TimeMs < 250.0)

        // Test Query 2: Multi-token search ("Artist Name 42")
        var results2: com.usman.musicplayer.data.repository.LocalSearchResults
        val query2TimeMs = measureNanoTime {
            results2 = indexer.search("Artist Name 42", limit = 100)
        } / 1_000_000.0

        println("Query 'Artist Name 42' returned ${results2.songs.size} songs in ${query2TimeMs}ms")
        assertTrue("Multi-token search should return results", results2.songs.isNotEmpty())
        assertTrue("Multi-token search latency must be reasonable (actual: ${query2TimeMs}ms)", query2TimeMs < 200.0)

        // Test Query 3: LRU Cache Hit
        val cacheHitTimeMs = measureNanoTime {
            indexer.search("Artist Name 42", limit = 100)
        } / 1_000_000.0

        println("LRU Cache Hit for 'Artist Name 42' executed in ${cacheHitTimeMs}ms")
        assertTrue("LRU Cache hit must be fast (actual: ${cacheHitTimeMs}ms)", cacheHitTimeMs < 50.0)
    }

    @Test
    fun testCacheEvictionOnIndexUpdate() = runBlocking {
        val initialSongs = listOf(
            Song(id = 1L, title = "Alpha Song", artist = "Artist 1", album = "Album 1", durationMs = 120000L, filePath = "/path/1")
        )
        indexer.updateIndex(initialSongs, emptyList(), emptyList(), emptyList())

        val res1 = indexer.search("Alpha")
        assertEquals(1, res1.songs.size)

        // Update index with new songs
        val newSongs = listOf(
            Song(id = 2L, title = "Beta Song", artist = "Artist 2", album = "Album 2", durationMs = 120000L, filePath = "/path/2")
        )
        indexer.updateIndex(newSongs, emptyList(), emptyList(), emptyList())

        // Cache should be evicted, Alpha should now return 0 results
        val res2 = indexer.search("Alpha")
        assertEquals(0, res2.songs.size)

        val res3 = indexer.search("Beta")
        assertEquals(1, res3.songs.size)
    }
}
