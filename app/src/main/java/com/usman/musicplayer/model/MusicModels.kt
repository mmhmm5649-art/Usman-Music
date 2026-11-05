package com.usman.musicplayer.model

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
    val isFavorite: Boolean = false
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

enum class LibraryTab(val title: String) {
    ALL_SONGS("All Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders")
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
