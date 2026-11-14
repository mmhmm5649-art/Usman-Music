package com.usman.musicplayer.data.repository

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.OnlineArtistResult
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

data class PersistedAlbumRecord(
    val id: String,
    val title: String,
    val movieName: String? = null,
    val artist: String,
    val year: String? = null,
    val songCount: Int? = null,
    val artworkUri: String? = null,
    val sourceProvider: String = "JioSaavn",
    val externalUrl: String? = null,
    val language: String? = null,
    val isCompleteAlbum: Boolean = true
)

data class PersistedArtistRecord(
    val id: String,
    val name: String,
    val artworkUri: String? = null,
    val subtitle: String? = null,
    val sourceProvider: String = "JioSaavn",
    val languages: List<String> = emptyList()
)

data class PersistedPlaylistRecord(
    val id: String,
    val title: String,
    val description: String = "",
    val coverArtUri: String? = null,
    val songs: List<PersistedSongRecord> = emptyList()
)

data class PersistedHomeSectionRecord(
    val id: String,
    val title: String,
    val subtitle: String,
    val languageName: String,
    val contentTypeName: String,
    val songs: List<PersistedSongRecord> = emptyList(),
    val albums: List<PersistedAlbumRecord> = emptyList(),
    val artists: List<PersistedArtistRecord> = emptyList(),
    val playlists: List<PersistedPlaylistRecord> = emptyList(),
    val isPersonalized: Boolean = false
)

data class PersistedHomeSnapshot(
    val savedAtMs: Long = 0L,
    val sections: List<PersistedHomeSectionRecord> = emptyList()
)

class HomeUserSignalsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val songListType = Types.newParameterizedType(List::class.java, PersistedSongRecord::class.java)
    private val songListAdapter = moshi.adapter<List<PersistedSongRecord>>(songListType)

    private val playStatsType = Types.newParameterizedType(List::class.java, PersistedPlayStat::class.java)
    private val playStatsAdapter = moshi.adapter<List<PersistedPlayStat>>(playStatsType)

    private val homeSnapshotAdapter = moshi.adapter(PersistedHomeSnapshot::class.java)

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

    fun saveHomeSectionsSnapshot(sections: List<HomeDiscoverySection>) {
        val populated = sections
            .filter { it.hasContent }
            .take(MAX_CACHED_HOME_SECTIONS)
            .map { sec ->
                PersistedHomeSectionRecord(
                    id = sec.id,
                    title = sec.title,
                    subtitle = sec.subtitle,
                    languageName = sec.language.name,
                    contentTypeName = sec.contentType.name,
                    songs = sec.songs
                        .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                        .take(MAX_CACHED_ITEMS_PER_SECTION)
                        .map { it.toRecord() },
                    albums = sec.albums
                        .filter { HomeLanguagePolicy.isAllowedJioSaavnAlbum(it) }
                        .take(MAX_CACHED_ITEMS_PER_SECTION)
                        .map {
                            PersistedAlbumRecord(
                                id = it.id,
                                title = it.title,
                                movieName = it.movieName,
                                artist = it.artist,
                                year = it.year,
                                songCount = it.songCount,
                                artworkUri = it.artworkUri,
                                sourceProvider = it.sourceProvider,
                                externalUrl = it.externalUrl,
                                language = it.language,
                                isCompleteAlbum = it.isCompleteAlbum
                            )
                        },
                    artists = sec.artists
                        .take(MAX_CACHED_ITEMS_PER_SECTION)
                        .map {
                            PersistedArtistRecord(
                                id = it.id,
                                name = it.name,
                                artworkUri = it.artworkUri,
                                subtitle = it.subtitle,
                                sourceProvider = it.sourceProvider,
                                languages = it.languages
                            )
                        },
                    playlists = sec.playlists
                        .take(MAX_CACHED_PLAYLISTS_PER_SECTION)
                        .map { pl ->
                            PersistedPlaylistRecord(
                                id = pl.id,
                                title = pl.title,
                                description = pl.description,
                                coverArtUri = pl.coverArtUri,
                                songs = pl.songs
                                    .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                                    .take(10)
                                    .map { it.toRecord() }
                            )
                        },
                    isPersonalized = sec.isPersonalized
                )
            }
        if (populated.isEmpty()) return
        val snapshot = PersistedHomeSnapshot(
            savedAtMs = System.currentTimeMillis(),
            sections = populated
        )
        runCatching {
            prefs.edit().putString(KEY_HOME_SNAPSHOT, homeSnapshotAdapter.toJson(snapshot)).apply()
        }
    }

    fun loadHomeSectionsSnapshot(maxAgeMs: Long = HOME_SNAPSHOT_MAX_AGE_MS): List<HomeDiscoverySection> {
        val raw = prefs.getString(KEY_HOME_SNAPSHOT, null) ?: return emptyList()
        return runCatching {
            val snapshot = homeSnapshotAdapter.fromJson(raw) ?: return emptyList()
            if (snapshot.savedAtMs <= 0L || (System.currentTimeMillis() - snapshot.savedAtMs) > maxAgeMs) {
                return emptyList()
            }
            snapshot.sections.mapNotNull { rec ->
                val lang = runCatching { HomeLanguageFilter.valueOf(rec.languageName) }.getOrDefault(HomeLanguageFilter.ALL)
                val type = runCatching { HomeSectionContentType.valueOf(rec.contentTypeName) }.getOrNull()
                    ?: return@mapNotNull null
                val songs = rec.songs.filter(::isAllowedPersistedRecord).map { it.toSong(isFavorite = false) }
                val albums = rec.albums
                    .filter { it.sourceProvider.equals("JioSaavn", ignoreCase = true) }
                    .map {
                        AlbumSearchResult(
                            id = it.id,
                            title = it.title,
                            movieName = it.movieName,
                            artist = it.artist,
                            year = it.year,
                            songCount = it.songCount,
                            artworkUri = it.artworkUri,
                            sourceProvider = it.sourceProvider,
                            externalUrl = it.externalUrl,
                            language = it.language,
                            isCompleteAlbum = it.isCompleteAlbum
                        )
                    }
                    .filter { HomeLanguagePolicy.isAllowedJioSaavnAlbum(it) }
                val artists = rec.artists.map {
                    OnlineArtistResult(
                        id = it.id,
                        name = it.name,
                        artworkUri = it.artworkUri,
                        subtitle = it.subtitle,
                        sourceProvider = it.sourceProvider,
                        languages = it.languages
                    )
                }
                val playlists = rec.playlists.map { pl ->
                    CuratedPlaylist(
                        id = pl.id,
                        title = pl.title,
                        description = pl.description,
                        coverArtUri = pl.coverArtUri,
                        songs = pl.songs.filter(::isAllowedPersistedRecord).map { it.toSong(isFavorite = false) }
                    )
                }.filter { HomeLanguagePolicy.isAllowedHomePlaylist(it) }

                val section = HomeDiscoverySection(
                    id = rec.id,
                    title = rec.title,
                    subtitle = rec.subtitle,
                    language = lang,
                    contentType = type,
                    songs = songs,
                    albums = albums,
                    artists = artists,
                    playlists = playlists,
                    isLoading = false,
                    hasAttemptedLoad = true,
                    canLoadMore = type == HomeSectionContentType.SONGS || type == HomeSectionContentType.ALBUMS,
                    currentPage = 1,
                    isPersonalized = rec.isPersonalized
                )
                if (section.hasContent) section else null
            }
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
        private const val KEY_HOME_SNAPSHOT = "persisted_home_sections_snapshot_v1"
        private const val MAX_FAVORITES = 200
        private const val MAX_RECENT = 25
        private const val MAX_PLAY_STATS = 80
        private const val MAX_CACHED_HOME_SECTIONS = 14
        private const val MAX_CACHED_ITEMS_PER_SECTION = 15
        private const val MAX_CACHED_PLAYLISTS_PER_SECTION = 8
        private const val HOME_SNAPSHOT_MAX_AGE_MS = 48L * 60L * 60L * 1000L

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
