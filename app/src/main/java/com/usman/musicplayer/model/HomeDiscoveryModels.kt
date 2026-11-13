package com.usman.musicplayer.model

import java.util.Calendar

enum class HomeLanguageFilter(
    val chipLabel: String,
    val displayName: String,
    val jioSaavnLanguage: String?
) {
    ALL("ALL", "All Languages", null),
    TELUGU("TELUGU", "Telugu", "telugu"),
    TAMIL("TAMIL", "Tamil", "tamil"),
    ENGLISH("ENGLISH", "English", "english"),
    HINDI("HINDI", "Hindi", "hindi")
}

enum class HomeSectionContentType {
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}

enum class HomeQuickAccessType {
    TELUGU_HITS,
    TAMIL_HITS,
    ENGLISH_HITS,
    HINDI_HITS,
    RECENTLY_PLAYED,
    FAVORITE_SONGS
}

data class HomeQuickAccessCard(
    val id: String,
    val type: HomeQuickAccessType,
    val title: String,
    val subtitle: String,
    val artworkUri: String? = null,
    val trackCount: Int = 0,
    val languageFilter: HomeLanguageFilter = HomeLanguageFilter.ALL
)

data class HomeHeroFeature(
    val id: String,
    val badge: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String? = null,
    val language: HomeLanguageFilter = HomeLanguageFilter.ALL,
    val primarySong: Song? = null,
    val queueSongs: List<Song> = emptyList(),
    val album: AlbumSearchResult? = null,
    val playlist: CuratedPlaylist? = null
)

data class HomeSectionDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val language: HomeLanguageFilter,
    val contentType: HomeSectionContentType,
    val query: String,
    val fallbackQueries: List<String> = emptyList(),
    val isMovieSoundtrack: Boolean = false,
    val isPersonalized: Boolean = false,
    val isPrimarySpotlight: Boolean = false
)

data class HomeDiscoverySection(
    val id: String,
    val title: String,
    val subtitle: String,
    val language: HomeLanguageFilter,
    val contentType: HomeSectionContentType,
    val songs: List<Song> = emptyList(),
    val albums: List<AlbumSearchResult> = emptyList(),
    val artists: List<OnlineArtistResult> = emptyList(),
    val playlists: List<CuratedPlaylist> = emptyList(),
    val isLoading: Boolean = false,
    val hasAttemptedLoad: Boolean = false,
    val canLoadMore: Boolean = true,
    val currentPage: Int = 1,
    val isPersonalized: Boolean = false
) {
    val hasContent: Boolean
        get() = songs.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()

    val itemCount: Int
        get() = when (contentType) {
            HomeSectionContentType.SONGS -> songs.size
            HomeSectionContentType.ALBUMS -> albums.size
            HomeSectionContentType.ARTISTS -> artists.size
            HomeSectionContentType.PLAYLISTS -> playlists.size
        }
}

data class HomeNotificationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val song: Song? = null,
    val album: AlbumSearchResult? = null
)

object HomeLanguagePolicy {
    val ALLOWED_LANGUAGES = setOf("telugu", "tamil", "english", "hindi")

    val EXCLUDED_LANGUAGES = setOf(
        "malayalam",
        "kannada",
        "punjabi",
        "bengali",
        "bangla",
        "marathi",
        "bhojpuri",
        "gujarati",
        "haryanvi",
        "odia",
        "oriya",
        "assamese",
        "rajasthani",
        "konkani",
        "tulu",
        "nepali",
        "sinhala",
        "sanskrit",
        "kashmiri",
        "dogri",
        "maithili",
        "santali"
    )

    private val EXCLUDED_KEYWORDS_REGEX = Regex(
        "\\b(malayalam|kannada|punjabi|bengali|marathi|bhojpuri|gujarati|haryanvi|odia|assamese|rajasthani)\\b",
        RegexOption.IGNORE_CASE
    )

    fun normalizeLanguage(raw: String?): String? {
        val clean = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        return when {
            clean == "te" || clean.contains("telugu") || clean.contains("tollywood") -> "telugu"
            clean == "ta" || clean.contains("tamil") || clean.contains("kollywood") -> "tamil"
            clean == "hi" || clean.contains("hindi") || clean.contains("bollywood") -> "hindi"
            clean == "en" || clean.contains("english") -> "english"
            else -> clean
        }
    }

    fun isAllowedHomeLanguage(rawLanguage: String?, titleOrText: String = ""): Boolean {
        val norm = normalizeLanguage(rawLanguage)
        if (norm != null) {
            if (norm in EXCLUDED_LANGUAGES) return false
            if (norm in ALLOWED_LANGUAGES) return true
            // Any explicit non-allowed regional language
            return false
        }
        return !EXCLUDED_KEYWORDS_REGEX.containsMatchIn(titleOrText)
    }

    fun matchesTargetLanguage(
        itemLanguage: String?,
        targetFilter: HomeLanguageFilter,
        titleOrText: String = ""
    ): Boolean {
        if (!isAllowedHomeLanguage(itemLanguage, titleOrText)) return false
        val required = targetFilter.jioSaavnLanguage ?: return true
        val norm = normalizeLanguage(itemLanguage)
        // Strict if metadata specifies a different language; allow if metadata didn't include language
        return norm == null || norm == required
    }

    fun isAllowedJioSaavnSong(song: Song): Boolean {
        if (song.isOnline) {
            val provider = song.sourceProvider.orEmpty().trim()
            if (!provider.equals("JioSaavn", ignoreCase = true)) return false
            if (song.playbackType != PlaybackType.FULL) return false
            if (!song.isStreamable && song.contentUriString.isNullOrBlank()) return false
        }
        return isAllowedHomeLanguage(song.language, "${song.title} ${song.album}")
    }

    fun isAllowedJioSaavnAlbum(album: AlbumSearchResult): Boolean {
        if (!album.sourceProvider.equals("JioSaavn", ignoreCase = true)) return false
        return isAllowedHomeLanguage(album.language, "${album.title} ${album.movieName.orEmpty()}")
    }

    fun isAllowedHomePlaylist(playlist: CuratedPlaylist): Boolean {
        if (EXCLUDED_KEYWORDS_REGEX.containsMatchIn("${playlist.title} ${playlist.description}")) {
            return false
        }
        return true
    }

    fun timeOfDayGreeting(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        val normalizedHour = ((hourOfDay % 24) + 24) % 24
        return when (normalizedHour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
