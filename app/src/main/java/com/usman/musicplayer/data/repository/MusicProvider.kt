package com.usman.musicplayer.data.repository

import com.usman.musicplayer.sdk.Song

interface MusicProvider {
    val id: String
    val name: String
    val isFullPlaybackSupported: Boolean
    
    suspend fun search(query: String, limit: Int = 30): Result<List<Song>>
    suspend fun getStreamUrl(song: Song): Result<String>
}
