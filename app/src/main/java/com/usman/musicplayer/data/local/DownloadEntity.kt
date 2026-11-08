package com.usman.musicplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: String?,
    val streamUrl: String,
    val filePath: String,
    val status: String, // "DOWNLOADING", "QUEUED", "COMPLETED", "PAUSED", "FAILED", "CANCELLED"
    val progress: Int, // 0 to 100
    val downloadedBytes: Long,
    val totalBytes: Long,
    val updatedAt: Long = System.currentTimeMillis()
)
