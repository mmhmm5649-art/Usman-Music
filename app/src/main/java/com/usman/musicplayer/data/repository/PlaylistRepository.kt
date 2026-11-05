package com.usman.musicplayer.data.repository

import android.content.Context
import com.usman.musicplayer.data.local.AppDatabase
import com.usman.musicplayer.data.local.PlaylistEntity
import com.usman.musicplayer.data.local.PlaylistSongEntity
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.playlistDao()

    val allPlaylists: Flow<List<Playlist>> = dao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            val count = dao.getPlaylistSongCount(entity.id)
            Playlist(
                id = entity.id,
                name = entity.name,
                songCount = count,
                coverArtUri = entity.coverArtUri,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun getSongsForPlaylistSync(playlistId: Long): List<Song> {
        val songEntities = dao.getSongsForPlaylistSync(playlistId)
        return songEntities.map { entity ->
            Song(
                id = entity.songId,
                title = entity.songTitle,
                artist = entity.songArtist,
                album = entity.songAlbum,
                durationMs = entity.durationMs,
                filePath = entity.filePath,
                albumArtUri = entity.albumArtUri,
                isOnline = entity.isOnline,
                contentUriString = entity.contentUriString
            )
        }
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return dao.getSongsForPlaylist(playlistId).map { entities ->
            entities.map { entity ->
                Song(
                    id = entity.songId,
                    title = entity.songTitle,
                    artist = entity.songArtist,
                    album = entity.songAlbum,
                    durationMs = entity.durationMs,
                    filePath = entity.filePath,
                    albumArtUri = entity.albumArtUri,
                    isOnline = entity.isOnline,
                    contentUriString = entity.contentUriString
                )
            }
        }
    }

    suspend fun createPlaylist(name: String, initialSong: Song? = null): Long {
        val entity = PlaylistEntity(name = name.trim(), coverArtUri = initialSong?.albumArtUri)
        val id = dao.insertPlaylist(entity)
        if (initialSong != null && id > 0) {
            addSongToPlaylist(id, initialSong, allowDuplicate = true)
        }
        return id
    }

    suspend fun addSongToPlaylist(
        playlistId: Long,
        song: Song,
        allowDuplicate: Boolean = false
    ): Pair<Boolean, String> {
        if (!allowDuplicate) {
            val exists = dao.isSongInPlaylist(playlistId, song.id)
            if (exists > 0) {
                return Pair(false, "\"${song.title}\" is already in this playlist.")
            }
        }

        val count = dao.getPlaylistSongCount(playlistId)
        val songEntity = PlaylistSongEntity(
            playlistId = playlistId,
            songId = song.id,
            songTitle = song.title,
            songArtist = song.artist,
            songAlbum = song.album,
            durationMs = song.durationMs,
            filePath = song.filePath,
            albumArtUri = song.albumArtUri,
            isOnline = song.isOnline,
            contentUriString = song.contentUriString,
            position = count
        )
        dao.addSongToPlaylist(songEntity)
        return Pair(true, "Added \"${song.title}\" to playlist.")
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        dao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylistSongs(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        dao.renamePlaylist(playlistId, newName.trim())
    }
}
