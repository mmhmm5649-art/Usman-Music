package com.example

import com.example.data.LocalMusicScanner
import com.example.model.LibraryTab
import com.example.model.RepeatMode
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicModelsAndScannerUnitTest {

    @Test
    fun testSongFormattedDuration() {
        val song = Song(
            id = 1L,
            title = "Midnight Dreams",
            artist = "USMAN",
            album = "Cosmic Vibes",
            durationMs = 277000L, // 4 mins 37 secs
            filePath = "/storage/emulated/0/Music/track1.mp3"
        )
        assertEquals("04:37", song.formattedDuration)
    }

    @Test
    fun testScannerExtractAlbumsAndArtists() {
        val songs = listOf(
            Song(1L, "Track 1", "Artist A", "Album 1", 180000L, "/path/1.mp3", "Rock"),
            Song(2L, "Track 2", "Artist A", "Album 1", 200000L, "/path/2.mp3", "Rock"),
            Song(3L, "Track 3", "Artist B", "Album 2", 210000L, "/path/3.mp3", "Pop")
        )

        val albums = LocalMusicScanner.extractAlbums(songs)
        assertEquals(2, albums.size)
        assertEquals("Album 1", albums[0].title)
        assertEquals(2, albums[0].songCount)

        val artists = LocalMusicScanner.extractArtists(songs)
        assertEquals(2, artists.size)
        val artistA = artists.find { it.name == "Artist A" }
        assertEquals(2, artistA?.songCount)
        assertEquals(1, artistA?.albumCount)

        val folders = LocalMusicScanner.extractFolders(songs)
        assertEquals(2, folders.size)
    }

    @Test
    fun testLibraryTabsAndRepeatModes() {
        assertEquals("All Songs", LibraryTab.ALL_SONGS.title)
        assertEquals("Albums", LibraryTab.ALBUMS.title)
        assertEquals("Artists", LibraryTab.ARTISTS.title)
        assertEquals("Folders", LibraryTab.FOLDERS.title)

        val repeatOff = RepeatMode.OFF
        val repeatAll = RepeatMode.ALL
        val repeatOne = RepeatMode.ONE
        assertEquals(RepeatMode.OFF, repeatOff)
        assertEquals(RepeatMode.ALL, repeatAll)
        assertEquals(RepeatMode.ONE, repeatOne)
    }
}
