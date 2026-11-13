package com.usman.musicplayer.data.repository

import android.content.Context
import com.usman.musicplayer.data.local.AppDatabase
import com.usman.musicplayer.data.local.SavedAlbumEntity
import com.usman.musicplayer.data.local.SavedAlbumTrackEntity
import com.usman.musicplayer.model.AlbumDetails
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlbumLibraryRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).savedAlbumDao()

    val savedAlbums: Flow<List<AlbumDetails>> = dao.getAllSavedAlbums().map { entities ->
        entities
            .filter { !it.sourceProvider.equals("iTunes", ignoreCase = true) }
            .mapNotNull { entity ->
                val trackEntities = dao.getAlbumTracksSync(entity.albumKey)
                    .filter { !it.sourceProvider.equals("iTunes", ignoreCase = true) }
                if (trackEntities.isEmpty()) null else entity.toAlbumDetails(trackEntities)
            }
    }

    fun albumKey(sourceProvider: String, albumId: String): String {
        return "${sourceProvider.lowercase().trim()}|${albumId.trim()}"
    }

    suspend fun isAlbumSaved(sourceProvider: String, albumId: String): Boolean {
        if (sourceProvider.equals("iTunes", ignoreCase = true)) return false
        return dao.isAlbumSaved(albumKey(sourceProvider, albumId))
    }

    suspend fun saveAlbum(details: AlbumDetails) {
        if (details.sourceProvider.equals("iTunes", ignoreCase = true) && !details.isSoundtrackCollection) {
            return
        }
        val allowedTracks = details.tracks.filter {
            !it.sourceProvider.equals("iTunes", ignoreCase = true) &&
                (!it.isOnline || it.playbackType == PlaybackType.FULL)
        }
        if (allowedTracks.isEmpty()) return

        val normalizedProvider = if (details.sourceProvider.equals("iTunes", ignoreCase = true)) {
            "JioSaavn"
        } else {
            details.sourceProvider
        }
        val key = albumKey(normalizedProvider, details.id)
        dao.insertAlbum(
            SavedAlbumEntity(
                albumKey = key,
                albumId = details.id,
                title = details.title,
                movieName = details.movieName,
                artist = details.artist,
                year = details.year,
                songCount = maxOf(details.songCount, allowedTracks.size),
                artworkUri = details.artworkUri,
                sourceProvider = normalizedProvider,
                externalUrl = details.externalUrl,
                language = details.language,
                isComplete = details.isComplete,
                savedAt = System.currentTimeMillis()
            )
        )
        dao.deleteAlbumTracks(key)
        val trackEntities = allowedTracks.mapIndexed { index, track ->
            SavedAlbumTrackEntity(
                albumKey = key,
                trackOrder = index,
                songId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                filePath = track.filePath,
                albumArtUri = track.albumArtUri,
                contentUriString = track.contentUriString,
                isOnline = track.isOnline,
                externalUrl = track.externalUrl,
                sourceProvider = track.sourceProvider ?: normalizedProvider,
                releaseYear = track.releaseYear ?: details.year
            )
        }
        if (trackEntities.isNotEmpty()) {
            dao.insertAlbumTracks(trackEntities)
        }
    }

    suspend fun removeSavedAlbum(sourceProvider: String, albumId: String) {
        val key = albumKey(sourceProvider, albumId)
        dao.deleteAlbumTracks(key)
        dao.deleteAlbum(key)
    }

    suspend fun getSavedAlbumDetailsSync(album: AlbumSearchResult): AlbumDetails? {
        if (album.sourceProvider.equals("iTunes", ignoreCase = true)) return null
        val key = albumKey(album.sourceProvider, album.id)
        val tracks = dao.getAlbumTracksSync(key)
            .filter { !it.sourceProvider.equals("iTunes", ignoreCase = true) }
        if (tracks.isEmpty()) return null
        return AlbumDetails(
            id = album.id,
            title = album.title,
            movieName = album.movieName,
            artist = album.artist,
            year = album.year,
            songCount = maxOf(album.songCount ?: tracks.size, tracks.size),
            artworkUri = album.artworkUri,
            sourceProvider = album.sourceProvider,
            externalUrl = album.externalUrl,
            language = album.language,
            isComplete = album.isCompleteAlbum,
            tracks = tracks.map { it.toSong(album.id) }
        )
    }

    private fun SavedAlbumEntity.toAlbumDetails(tracks: List<SavedAlbumTrackEntity>): AlbumDetails {
        val isSoundtrack = albumId.startsWith("soundtrack_")
        val cleanProvider = if (sourceProvider.equals("iTunes", ignoreCase = true)) "JioSaavn" else sourceProvider
        return AlbumDetails(
            id = albumId,
            title = title,
            movieName = movieName,
            artist = artist,
            year = year,
            songCount = maxOf(songCount, tracks.size),
            artworkUri = artworkUri,
            sourceProvider = cleanProvider,
            externalUrl = externalUrl,
            language = language,
            isComplete = isComplete,
            tracks = tracks.map { it.toSong(albumId) },
            discoveredCount = tracks.size,
            officialExpectedCount = if (isComplete) songCount else null,
            contributingAlbumsCount = tracks.map { it.album }.distinct().size.coerceAtLeast(1),
            contributingProviders = tracks.map { it.sourceProvider }.filter { !it.equals("iTunes", true) }.distinct().ifEmpty { listOf(cleanProvider) },
            isSoundtrackCollection = isSoundtrack
        )
    }

    private fun SavedAlbumTrackEntity.toSong(albumId: String): Song {
        val cleanProvider = if (sourceProvider.equals("iTunes", ignoreCase = true)) "JioSaavn" else sourceProvider
        val streamable = !contentUriString.isNullOrBlank() || !isOnline
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filePath = filePath,
            folderName = cleanProvider,
            albumArtUri = albumArtUri,
            contentUriString = contentUriString,
            isOnline = isOnline,
            playbackType = PlaybackType.FULL,
            externalUrl = externalUrl,
            sourceProvider = cleanProvider,
            attribution = "$artist • $cleanProvider",
            isStreamable = streamable,
            isFullTrackVerified = true,
            isRightsVerified = true,
            albumId = albumId,
            releaseYear = releaseYear
        )
    }
}
