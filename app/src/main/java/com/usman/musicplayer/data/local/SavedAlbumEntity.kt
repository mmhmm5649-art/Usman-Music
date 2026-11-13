package com.usman.musicplayer.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey
    val albumKey: String, // "${sourceProvider.lowercase()}|$albumId"
    val albumId: String,
    val title: String,
    val movieName: String? = null,
    val artist: String,
    val year: String? = null,
    val songCount: Int,
    val artworkUri: String? = null,
    val sourceProvider: String,
    val externalUrl: String? = null,
    val language: String? = null,
    val isComplete: Boolean = true,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_album_tracks",
    primaryKeys = ["albumKey", "trackOrder"],
    indices = [Index("albumKey")]
)
data class SavedAlbumTrackEntity(
    val albumKey: String,
    val trackOrder: Int,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val albumArtUri: String? = null,
    val contentUriString: String? = null,
    val isOnline: Boolean = true,
    val externalUrl: String? = null,
    val sourceProvider: String,
    val releaseYear: String? = null
)

@Dao
interface SavedAlbumDao {

    @Query("SELECT * FROM saved_albums ORDER BY savedAt DESC")
    fun getAllSavedAlbums(): Flow<List<SavedAlbumEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE albumKey = :albumKey)")
    suspend fun isAlbumSaved(albumKey: String): Boolean

    @Query("SELECT * FROM saved_album_tracks WHERE albumKey = :albumKey ORDER BY trackOrder ASC")
    suspend fun getAlbumTracksSync(albumKey: String): List<SavedAlbumTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: SavedAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumTracks(tracks: List<SavedAlbumTrackEntity>)

    @Query("DELETE FROM saved_album_tracks WHERE albumKey = :albumKey")
    suspend fun deleteAlbumTracks(albumKey: String)

    @Query("DELETE FROM saved_albums WHERE albumKey = :albumKey")
    suspend fun deleteAlbum(albumKey: String)
}
