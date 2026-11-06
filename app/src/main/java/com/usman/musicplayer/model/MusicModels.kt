package com.usman.musicplayer.model

import com.usman.musicplayer.sdk.Song
import com.usman.musicplayer.sdk.PlaybackType

typealias Song = com.usman.musicplayer.sdk.Song
typealias PlaybackType = com.usman.musicplayer.sdk.PlaybackType


data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: String? = null
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

data class MusicFolder(
    val name: String,
    val path: String,
    val songCount: Int,
    val isSafFolder: Boolean = false,
    val treeUriString: String? = null
)

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val coverArtUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class CuratedPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val coverArtUri: String? = null,
    val songs: List<Song> = emptyList()
)

enum class LibraryTab(val title: String) {
    ALL_SONGS("All Songs"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders")
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

