package com.usman.musicplayer.data.repository

import android.content.Context
import com.usman.musicplayer.data.LocalMusicScanner
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicProvider(context: Context) : MusicProvider {
    private val scanner = LocalMusicScanner(context)
    @Volatile
    private var cachedSongs: List<Song>? = null
    
    override val metadata = ProviderMetadata(
        id = "local_device",
        name = "Local Device",
        version = "1.0.0",
        isFullPlaybackSupported = true,
        authRequired = false
    )

    fun updateSongs(songs: List<Song>) {
        cachedSongs = songs
    }

    override suspend fun search(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val allSongs = cachedSongs ?: scanner.scanLocalAudio().also { cachedSongs = it }
            val filtered = if (query.isBlank()) {
                allSongs
            } else {
                val q = query.trim().lowercase()
                allSongs.filter { 
                    it.title.lowercase().contains(q) || 
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
                }
            }
            Result.success(filtered.take(limit))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> {
        return if (!song.contentUriString.isNullOrBlank()) {
            Result.success(song.contentUriString)
        } else {
            Result.failure(Exception("No content URI for local track"))
        }
    }
}
