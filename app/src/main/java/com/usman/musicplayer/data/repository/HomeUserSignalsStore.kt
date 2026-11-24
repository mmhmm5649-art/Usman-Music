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
import com.usman.musicplayer.model.RecommendationPreference
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
    val trackNumber: Int? = null,
    val genre: String? = null
)

data class PersistedMoodChoice(
    val moodId: String,
    val weight: Float,
    val updatedAtMs: Long
)

data class PersistedSkippedSignal(
    val songKey: String,
    val moodId: String? = null,
    val timestampMs: Long
)

data class HomeSessionState(
    val sessionNumber: Long,
    val sessionSeed: Long,
    val previousShownSongKeys: Set<String> = emptySet(),
    val previousSectionOrder: List<String> = emptyList()
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

    private val moodChoiceListType = Types.newParameterizedType(List::class.java, PersistedMoodChoice::class.java)
    private val moodChoiceAdapter = moshi.adapter<List<PersistedMoodChoice>>(moodChoiceListType)

    private val skippedSignalListType = Types.newParameterizedType(List::class.java, PersistedSkippedSignal::class.java)
    private val skippedSignalAdapter = moshi.adapter<List<PersistedSkippedSignal>>(skippedSignalListType)

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

    fun loadMoodAutoplayEnabled(): Boolean {
        return prefs.getBoolean(KEY_MOOD_AUTOPLAY_ENABLED, true)
    }

    fun saveMoodAutoplayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOOD_AUTOPLAY_ENABLED, enabled).apply()
    }

    fun loadDynamicHomeFeedEnabled(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_HOME_FEED_ENABLED, true)
    }

    fun saveDynamicHomeFeedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_HOME_FEED_ENABLED, enabled).apply()
    }

    fun loadRecommendationPreference(): RecommendationPreference {
        return RecommendationPreference.fromId(prefs.getString(KEY_RECOMMENDATION_PREFERENCE, null))
    }

    fun saveRecommendationPreference(preference: RecommendationPreference) {
        prefs.edit().putString(KEY_RECOMMENDATION_PREFERENCE, preference.id).apply()
    }

    fun loadExcludeRecentlyPlayed(): Boolean {
        return prefs.getBoolean(KEY_EXCLUDE_RECENTLY_PLAYED, true)
    }

    fun saveExcludeRecentlyPlayed(exclude: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_RECENTLY_PLAYED, exclude).apply()
    }

    fun recordMoodChoice(moodId: String, boost: Float = 1.0f) {
        if (moodId.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = loadRawMoodChoices().toMutableList()
        val idx = existing.indexOfFirst { it.moodId.equals(moodId, ignoreCase = true) }
        if (idx >= 0) {
            val prev = existing[idx]
            existing[idx] = prev.copy(
                weight = (prev.weight * 0.85f + boost).coerceAtMost(8.0f),
                updatedAtMs = now
            )
        } else {
            existing.add(PersistedMoodChoice(moodId = moodId, weight = boost.coerceIn(0.2f, 8.0f), updatedAtMs = now))
        }
        val sorted = existing.sortedByDescending { it.weight }.take(12)
        runCatching {
            prefs.edit().putString(KEY_MOOD_CHOICES, moodChoiceAdapter.toJson(sorted)).apply()
        }
    }

    fun loadMoodChoices(): Map<String, Float> {
        return loadRawMoodChoices().associate { it.moodId to it.weight }
    }

    private fun loadRawMoodChoices(): List<PersistedMoodChoice> {
        val raw = prefs.getString(KEY_MOOD_CHOICES, null) ?: return emptyList()
        return runCatching { moodChoiceAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    fun recordSkippedSong(song: Song, moodId: String?) {
        val key = songIdentityKey(song)
        val now = System.currentTimeMillis()
        val existing = loadRawSkippedSignals()
            .filterNot { it.songKey == key }
            .toMutableList()
        existing.add(0, PersistedSkippedSignal(songKey = key, moodId = moodId, timestampMs = now))
        val trimmed = existing.take(MAX_SKIPPED_SIGNALS)
        runCatching {
            prefs.edit().putString(KEY_SKIPPED_SIGNALS, skippedSignalAdapter.toJson(trimmed)).apply()
        }
    }

    fun loadSkippedSignals(): List<PersistedSkippedSignal> {
        val cutoff = System.currentTimeMillis() - SKIP_RETENTION_MS
        return loadRawSkippedSignals().filter { it.timestampMs >= cutoff }
    }

    private fun loadRawSkippedSignals(): List<PersistedSkippedSignal> {
        val raw = prefs.getString(KEY_SKIPPED_SIGNALS, null) ?: return emptyList()
        return runCatching { skippedSignalAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    fun startNewHomeSession(): HomeSessionState {
        val prevCounter = prefs.getLong(KEY_HOME_SESSION_COUNTER, 0L)
        val nextCounter = prevCounter + 1L
        val sessionSeed = mixSessionSeed(nextCounter, System.currentTimeMillis())

        val prevShown = prefs.getStringSet(KEY_PREVIOUS_SHOWN_SONG_KEYS, emptySet()).orEmpty().toSet()
        val prevOrderRaw = prefs.getString(KEY_PREVIOUS_SECTION_ORDER, null).orEmpty()
        val prevOrder = prevOrderRaw.split(',').map { it.trim() }.filter { it.isNotBlank() }

        prefs.edit()
            .putLong(KEY_HOME_SESSION_COUNTER, nextCounter)
            .apply()

        return HomeSessionState(
            sessionNumber = nextCounter,
            sessionSeed = sessionSeed,
            previousShownSongKeys = prevShown,
            previousSectionOrder = prevOrder
        )
    }

    fun recordShownHomeState(shownSongKeys: Set<String>, sectionOrder: List<String>) {
        val cappedKeys = shownSongKeys.take(MAX_SHOWN_HISTORY_KEYS).toSet()
        val orderCsv = sectionOrder.take(40).joinToString(",")
        prefs.edit()
            .putStringSet(KEY_PREVIOUS_SHOWN_SONG_KEYS, cappedKeys)
            .putString(KEY_PREVIOUS_SECTION_ORDER, orderCsv)
            .apply()
    }

    private fun mixSessionSeed(counter: Long, timestampMs: Long): Long {
        var x = counter * 0x9E3779B97F4A7C15UL.toLong() + (timestampMs ushr 10)
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        x = x xor (x ushr 31)
        return if (x == 0L) counter + 101L else x
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
        trackNumber = trackNumber,
        genre = genre
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
        genre = genre,
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
        private const val KEY_MOOD_CHOICES = "persisted_mood_choices_v1"
        private const val KEY_SKIPPED_SIGNALS = "persisted_skipped_signals_v1"
        private const val KEY_MOOD_AUTOPLAY_ENABLED = "mood_autoplay_enabled_v1"
        private const val KEY_DYNAMIC_HOME_FEED_ENABLED = "dynamic_home_feed_enabled_v1"
        private const val KEY_RECOMMENDATION_PREFERENCE = "recommendation_preference_v1"
        private const val KEY_EXCLUDE_RECENTLY_PLAYED = "exclude_recently_played_v1"
        private const val KEY_HOME_SESSION_COUNTER = "home_session_counter_v1"
        private const val KEY_PREVIOUS_SHOWN_SONG_KEYS = "home_prev_shown_song_keys_v1"
        private const val KEY_PREVIOUS_SECTION_ORDER = "home_prev_section_order_v1"
        private const val MAX_FAVORITES = 200
        private const val MAX_RECENT = 25
        private const val MAX_PLAY_STATS = 80
        private const val MAX_CACHED_HOME_SECTIONS = 16
        private const val MAX_CACHED_ITEMS_PER_SECTION = 15
        private const val MAX_CACHED_PLAYLISTS_PER_SECTION = 8
        private const val MAX_SKIPPED_SIGNALS = 60
        private const val MAX_SHOWN_HISTORY_KEYS = 80
        private const val SKIP_RETENTION_MS = 14L * 24L * 60L * 60L * 1000L
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
