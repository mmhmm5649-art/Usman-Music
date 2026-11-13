package com.usman.musicplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        DownloadEntity::class,
        SavedAlbumEntity::class,
        SavedAlbumTrackEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun savedAlbumDao(): SavedAlbumDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_albums` (
                        `albumKey` TEXT NOT NULL,
                        `albumId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `movieName` TEXT,
                        `artist` TEXT NOT NULL,
                        `year` TEXT,
                        `songCount` INTEGER NOT NULL,
                        `artworkUri` TEXT,
                        `sourceProvider` TEXT NOT NULL,
                        `externalUrl` TEXT,
                        `language` TEXT,
                        `isComplete` INTEGER NOT NULL,
                        `savedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`albumKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_album_tracks` (
                        `albumKey` TEXT NOT NULL,
                        `trackOrder` INTEGER NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `albumArtUri` TEXT,
                        `contentUriString` TEXT,
                        `isOnline` INTEGER NOT NULL,
                        `externalUrl` TEXT,
                        `sourceProvider` TEXT NOT NULL,
                        `releaseYear` TEXT,
                        PRIMARY KEY(`albumKey`, `trackOrder`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_album_tracks_albumKey` ON `saved_album_tracks` (`albumKey`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "usman_music_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
