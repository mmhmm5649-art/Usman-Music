package com.usman.musicplayer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val coverArtUri: String? = null
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId"), Index("songId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val songTitle: String,
    val songArtist: String,
    val songAlbum: String,
    val durationMs: Long,
    val filePath: String,
    val albumArtUri: String? = null,
    val isOnline: Boolean = false,
    val contentUriString: String? = null,
    val position: Int = 0
)
