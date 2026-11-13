package com.usman.musicplayer.sdk

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
    val license: String? = null,
    val attribution: String? = null,
    val isStreamable: Boolean = false,
    val isFullTrackVerified: Boolean = false,
    val isRightsVerified: Boolean = false,
    val playbackRestrictions: String? = null,
    val albumId: String? = null,
    val releaseYear: String? = null,
    val albumUrl: String? = null,
    val trackNumber: Int? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
