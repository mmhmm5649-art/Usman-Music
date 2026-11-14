package com.usman.musicplayer.model

import androidx.compose.runtime.Immutable
import com.usman.musicplayer.sdk.Song
import com.usman.musicplayer.sdk.PlaybackType

typealias Song = com.usman.musicplayer.sdk.Song
typealias PlaybackType = com.usman.musicplayer.sdk.PlaybackType
typealias AlbumSearchResult = com.usman.musicplayer.sdk.AlbumSearchResult
typealias AlbumDetails = com.usman.musicplayer.sdk.AlbumDetails


@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: String? = null
)

@Immutable
data class OnlineArtistResult(
    val id: String,
    val name: String,
    val artworkUri: String? = null,
    val subtitle: String? = null,
    val sourceProvider: String = "JioSaavn",
    val languages: List<String> = emptyList()
)

enum class SearchFilterCategory(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists")
}

@Immutable
data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

@Immutable
data class MusicFolder(
    val name: String,
    val path: String,
    val songCount: Int,
    val isSafFolder: Boolean = false,
    val treeUriString: String? = null
)

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val coverArtUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
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
    FOLDERS("Folders"),
    DOWNLOADS("Downloads"),
    DOWNLOAD_CENTRE("Download Centre")
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

