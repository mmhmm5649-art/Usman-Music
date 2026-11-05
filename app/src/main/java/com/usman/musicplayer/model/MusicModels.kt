package com.usman.musicplayer.model

enum class PlaybackType {
    FULL,            // Full track playback (local audio, authorized full stream)
    PREVIEW,         // High-quality official audio preview stream (30s) playable in-app via ExoPlayer
    EXTERNAL_SERVICE // Deep link to official service (YouTube, Spotify, Apple Music)
}

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val folderName: String = "Internal Storage/Music",
    val albumArtUri: String? = null,
    val contentUriString: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    val isOnline: Boolean = false,
    val playbackType: PlaybackType = PlaybackType.FULL,
    val externalUrl: String? = null,
    val genre: String? = null,
    val language: String? = null,
    val sourceProvider: String? = null,
    val licenseCcUrl: String? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

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

