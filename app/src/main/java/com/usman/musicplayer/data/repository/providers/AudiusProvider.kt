package com.usman.musicplayer.data.repository.providers

import com.usman.musicplayer.data.remote.AudiusApiService
import com.usman.musicplayer.data.remote.AudiusTrackDto
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class AudiusProvider(private val apiService: AudiusApiService) : MusicProvider {
    
    override val metadata = ProviderMetadata(
        id = "audius_provider",
        name = "Audius",
        version = "1.0.0",
        isFullPlaybackSupported = true,
        authRequired = false
    )

    private val AUDIUS_BASE_URL = "https://discoveryprovider.audius.co/v1/"

    override suspend fun search(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchTracks(query = query, limit = limit)
            val songs = response.data.map { mapDtoToSong(it) }
            Result.success(songs)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> {
        return if (!song.contentUriString.isNullOrBlank()) {
            Result.success(song.contentUriString)
        } else {
            Result.failure(Exception("Stream not available"))
        }
    }

    private fun mapDtoToSong(dto: AudiusTrackDto): Song {
        val trackId = dto.trackId ?: dto.id?.hashCode()?.toLong() ?: abs(("${dto.title}_${dto.user?.name}").hashCode().toLong())
        val streamUrl = "${AUDIUS_BASE_URL}tracks/${dto.id}/stream?app_name=USMAN_MUSIC_PLAYER"
        val highResArt = dto.artwork?.art1000 ?: dto.artwork?.art480 ?: dto.artwork?.art150

        return Song(
            id = trackId,
            title = dto.title ?: "Unknown Audius Track",
            artist = dto.user?.name ?: "Unknown Artist",
            album = "Audius",
            durationMs = (dto.duration ?: 180).toLong() * 1000L,
            filePath = streamUrl,
            folderName = "Audius Discovery",
            albumArtUri = highResArt,
            contentUriString = streamUrl,
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            isFavorite = false,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            externalUrl = "https://audius.co${dto.permalink}",
            genre = dto.genre,
            sourceProvider = "Audius",
            license = "Audius Platform License",
            attribution = dto.user?.name,
            isStreamable = dto.isStreamable == true,
            isFullTrackVerified = false, 
            isRightsVerified = false,
            playbackRestrictions = if (dto.isStreamable != true) "Stream not allowed" else null
        )
    }
}
