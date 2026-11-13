package com.usman.musicplayer.data.repository

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song

data class PersistedSongRecord(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val folderName: String = "JioSaavn",
    val albumArtUri: String? = null,
    val contentUriString: String? = null,
    val isOnline: Boolean = true,
    val externalUrl: String? = null,
    val language: String? = null,
    val sourceProvider: String? = "JioSaavn",
    val albumId: String? = null,
    val releaseYear: String? = null,
    val albumUrl: String? = null,
    val trackNumber: Int? = null
)

data class PersistedPlayStat(
    val song: PersistedSongRecord,
    val count: Int,
    val lastPlayedAtMs: Long
)

class HomeUserSignalsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val songListType = Types.newParameterizedType(List::class.java, PersistedSongRecord::class.java)
    private val songListAdapter = moshi.adapter<List<PersistedSongRecord>>(songListType)

    private val playStatsType = Types.newParameterizedType(List::class.java, PersistedPlayStat::class.java)
    private val playStatsAdapter = moshi.adapter<List<PersistedPlayStat>>(playStatsType)

    fun loadFavorites(): List<Song> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            songListAdapter.fromJson(raw).orEmpty()
                .filter(::isAllowedPersistedRecord)
                .map { it.toSong(isFavorite = true) }
        }.getOrDefault(emptyList())
    }

    fun saveFavorites(favorites: List<Song>) {
        val filtered = favorites
            .filter { !it.isOnline || (it.sourceProvider.equals("JioSaavn", true) && it.playbackType == PlaybackType.FULL) }
            .distinctBy { songIdentityKey(it) }
            .take(MAX_FAVORITES)
            .map { it.toRecord() }
        prefs.edit().putString(KEY_FAVORITES, songListAdapter.toJson(filtered)).apply()
    }

    fun loadRecentlyPlayed(): List<Song> {
        val raw = prefs.getString(KEY_RECENTLY_PLAYED, null) ?: return emptyList()
        return runCatching {
            songListAdapter.fromJson(raw).orEmpty()
                .filter(::isAllowedPersistedRecord)
                .map { it.toSong(isFavorite = false) }
        }.getOrDefault(emptyList())
    }

    fun saveRecentlyPlayed(recent: List<Song>) {
        val filtered = recent
            .filter { !it.isOnline || (it.sourceProvider.equals("JioSaavn", true) && it.playbackType == PlaybackType.FULL) }
            .distinctBy { songIdentityKey(it) }
            .take(MAX_RECENT)
            .map { it.toRecord() }
        prefs.edit().putString(KEY_RECENTLY_PLAYED, songListAdapter.toJson(filtered)).apply()
    }

    fun recordSongPlay(song: Song): List<PersistedPlayStat> {
        if (song.isOnline && (!song.sourceProvider.equals("JioSaavn", true) || song.playbackType != PlaybackType.FULL)) {
            return loadPlayStats()
        }
        val existing = loadPlayStats().toMutableList()
        val key = songIdentityKey(song)
        val idx = existing.indexOfFirst { recordIdentityKey(it.song) == key }
        val now = System.currentTimeMillis()
        if (idx >= 0) {
            val prev = existing[idx]
            existing[idx] = prev.copy(
                song = song.toRecord(),
                count = prev.count + 1,
                lastPlayedAtMs = now
            )
        } else {
            existing.add(PersistedPlayStat(song = song.toRecord(), count = 1, lastPlayedAtMs = now))
        }
        val sorted = existing
            .sortedWith(compareByDescending<PersistedPlayStat> { it.count }.thenByDescending { it.lastPlayedAtMs })
            .take(MAX_PLAY_STATS)
        prefs.edit().putString(KEY_PLAY_STATS, playStatsAdapter.toJson(sorted)).apply()
        return sorted
    }

    fun loadPlayStats(): List<PersistedPlayStat> {
        val raw = prefs.getString(KEY_PLAY_STATS, null) ?: return emptyList()
        return runCatching {
            playStatsAdapter.fromJson(raw).orEmpty().filter { isAllowedPersistedRecord(it.song) }
        }.getOrDefault(emptyList())
    }

    private fun isAllowedPersistedRecord(record: PersistedSongRecord): Boolean {
        if (!record.isOnline) return true
        val provider = record.sourceProvider.orEmpty().trim()
        if (provider.equals("iTunes", ignoreCase = true)) return false
        if (record.contentUriString?.contains("itunes.apple.com", ignoreCase = true) == true) return false
        return provider.equals("JioSaavn", ignoreCase = true)
    }

    private fun Song.toRecord(): PersistedSongRecord = PersistedSongRecord(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        filePath = filePath,
        folderName = folderName,
        albumArtUri = albumArtUri,
        contentUriString = contentUriString,
        isOnline = isOnline,
        externalUrl = externalUrl,
        language = language,
        sourceProvider = sourceProvider,
        albumId = albumId,
        releaseYear = releaseYear,
        albumUrl = albumUrl,
        trackNumber = trackNumber
    )

    private fun PersistedSongRecord.toSong(isFavorite: Boolean): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        filePath = filePath,
        folderName = folderName,
        albumArtUri = albumArtUri,
        contentUriString = contentUriString,
        isFavorite = isFavorite,
        isOnline = isOnline,
        playbackType = PlaybackType.FULL,
        externalUrl = externalUrl,
        language = language,
        sourceProvider = sourceProvider,
        attribution = if (isOnline) "$artist • ${sourceProvider ?: "JioSaavn"}" else null,
        isStreamable = !isOnline || !contentUriString.isNullOrBlank() || filePath.isNotBlank(),
        isFullTrackVerified = true,
        isRightsVerified = true,
        albumId = albumId,
        releaseYear = releaseYear,
        albumUrl = albumUrl,
        trackNumber = trackNumber
    )

    companion object {
        private const val PREFS_NAME = "usman_home_signals_prefs"
        private const val KEY_FAVORITES = "persisted_favorite_songs_v1"
        private const val KEY_RECENTLY_PLAYED = "persisted_recently_played_v1"
        private const val KEY_PLAY_STATS = "persisted_play_stats_v1"
        private const val MAX_FAVORITES = 200
        private const val MAX_RECENT = 25
        private const val MAX_PLAY_STATS = 80

        fun songIdentityKey(song: Song): String {
            return if (song.isOnline) {
                "online|${song.sourceProvider.orEmpty().lowercase()}|${song.filePath.ifBlank { song.id.toString() }}"
            } else {
                "local|${song.id}|${song.filePath}"
            }
        }

        private fun recordIdentityKey(record: PersistedSongRecord): String {
            return if (record.isOnline) {
                "online|${record.sourceProvider.orEmpty().lowercase()}|${record.filePath.ifBlank { record.id.toString() }}"
            } else {
                "local|${record.id}|${record.filePath}"
            }
        }
    }
}
