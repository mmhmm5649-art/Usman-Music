package com.usman.musicplayer.data.repository

import com.usman.musicplayer.model.Song

interface MusicSource {
    val name: String
    suspend fun search(query: String, limit: Int = 30): Result<List<Song>>
    suspend fun getStreamUrl(trackId: String): Result<String>
}
