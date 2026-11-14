package com.usman.musicplayer.data.repository

import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.HomeQuickAccessType
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDiscoveryTest {

    private class FakeHomeGateway : HomeMusicGateway {
        val songQueries = mutableListOf<Pair<String, Int>>()

        override suspend fun searchSongs(query: String, limit: Int, page: Int): List<Song> {
            songQueries.add(query to page)
            val lang = when {
                query.contains("telugu", ignoreCase = true) || query.contains("tollywood", ignoreCase = true) -> "telugu"
                query.contains("tamil", ignoreCase = true) || query.contains("kollywood", ignoreCase = true) -> "tamil"
                query.contains("hindi", ignoreCase = true) || query.contains("bollywood", ignoreCase = true) -> "hindi"
                query.contains("english", ignoreCase = true) || query.contains("pop", ignoreCase = true) -> "english"
                else -> "hindi"
            }

            return listOf(
                Song(
                    id = 100L + page * 10 + songQueries.size,
                    title = "Verified $lang Track $page",
                    artist = "Sid Sriram",
                    album = "$lang Hit Album",
                    durationMs = 210_000L,
                    filePath = "saavn_${lang}_${page}_${songQueries.size}",
                    albumArtUri = "https://c.saavncdn.com/test/$lang.jpg",
                    contentUriString = "https://aac.saavncdn.com/test/${lang}_$page.mp4",
                    isOnline = true,
                    playbackType = PlaybackType.FULL,
                    language = lang,
                    sourceProvider = "JioSaavn",
                    isStreamable = true,
                    albumId = "alb_${lang}_1"
                ),
                // Must be filtered out: Punjabi (unsupported home language)
                Song(
                    id = 901L,
                    title = "Punjabi Beat",
                    artist = "Some Artist",
                    album = "Punjabi Mix",
                    durationMs = 190_000L,
                    filePath = "saavn_pa_901",
                    contentUriString = "https://aac.saavncdn.com/pa.mp4",
                    isOnline = true,
                    playbackType = PlaybackType.FULL,
                    language = "punjabi",
                    sourceProvider = "JioSaavn",
                    isStreamable = true
                ),
                // Must be filtered out: Malayalam (unsupported home language)
                Song(
                    id = 902L,
                    title = "Malayalam Melody",
                    artist = "Some Singer",
                    album = "Mollywood",
                    durationMs = 200_000L,
                    filePath = "saavn_ml_902",
                    contentUriString = "https://aac.saavncdn.com/ml.mp4",
                    isOnline = true,
                    playbackType = PlaybackType.FULL,
                    language = "malayalam",
                    sourceProvider = "JioSaavn",
                    isStreamable = true
                ),
                // Must be filtered out: iTunes 30-second preview
                Song(
                    id = 903L,
                    title = "iTunes Preview Song",
                    artist = "Preview Artist",
                    album = "Preview",
                    durationMs = 30_000L,
                    filePath = "itunes_903",
                    contentUriString = "https://audio-ssl.itunes.apple.com/preview.m4a",
                    isOnline = true,
                    playbackType = PlaybackType.PREVIEW,
                    language = "english",
                    sourceProvider = "iTunes",
                    isStreamable = true
                )
            )
        }

        override suspend fun searchAlbums(query: String, limit: Int, page: Int): List<AlbumSearchResult> {
            val lang = when {
                query.contains("telugu", ignoreCase = true) -> "telugu"
                query.contains("tamil", ignoreCase = true) -> "tamil"
                query.contains("hindi", ignoreCase = true) || query.contains("bollywood", ignoreCase = true) -> "hindi"
                else -> "english"
            }
            return listOf(
                AlbumSearchResult(
                    id = "alb_${lang}_$page",
                    title = "Pushpa OST" + if (lang == "telugu") "" else " ($lang)",
                    artist = "Devi Sri Prasad",
                    year = "2025",
                    songCount = 6,
                    artworkUri = "https://c.saavncdn.com/alb/$lang.jpg",
                    sourceProvider = "JioSaavn",
                    language = lang,
                    isCompleteAlbum = true
                ),
                AlbumSearchResult(
                    id = "alb_kn_999",
                    title = "Kannada Hit Album",
                    artist = "Ravi Basrur",
                    sourceProvider = "JioSaavn",
                    language = "kannada"
                ),
                AlbumSearchResult(
                    id = "alb_itunes_888",
                    title = "iTunes Album",
                    artist = "Preview",
                    sourceProvider = "iTunes",
                    language = "english"
                )
            )
        }

        override suspend fun searchArtists(query: String, limit: Int): List<OnlineArtistResult> {
            return listOf(
                OnlineArtistResult(
                    id = "art_$query",
                    name = query,
                    artworkUri = "https://c.saavncdn.com/art/$query.jpg",
                    subtitle = "Singer",
                    sourceProvider = "JioSaavn"
                )
            )
        }

        override suspend fun searchPlaylists(query: String, limit: Int): List<CuratedPlaylist> {
            return listOf(
                CuratedPlaylist(
                    id = "pl_1",
                    title = "Top Chartbusters",
                    description = "JioSaavn Editorial",
                    coverArtUri = "https://c.saavncdn.com/pl/1.jpg"
                ),
                CuratedPlaylist(
                    id = "pl_excluded",
                    title = "Best Punjabi Bhangra Hits",
                    description = "Punjabi Mix"
                )
            )
        }

        override suspend fun getPlaylistDetails(playlist: CuratedPlaylist): CuratedPlaylist? {
            return playlist.copy(
                songs = searchSongs("telugu hits", 10, 1)
            )
        }
    }

    @Test
    fun homeCatalogHasOnlyFourLanguagesAndTenSectionsPerLanguage() {
        val supported = HomeLanguageFilter.entries.filter { it != HomeLanguageFilter.ALL }
        assertEquals(
            listOf(
                HomeLanguageFilter.TELUGU,
                HomeLanguageFilter.TAMIL,
                HomeLanguageFilter.ENGLISH,
                HomeLanguageFilter.HINDI
            ),
            supported
        )

        assertEquals(10, HomeDiscoveryCatalog.TELUGU_SECTIONS.size)
        assertEquals(10, HomeDiscoveryCatalog.TAMIL_SECTIONS.size)
        assertEquals(10, HomeDiscoveryCatalog.HINDI_SECTIONS.size)
        assertEquals(10, HomeDiscoveryCatalog.ENGLISH_SECTIONS.size)

        val teluguTitles = HomeDiscoveryCatalog.TELUGU_SECTIONS.map { it.title }
        assertTrue(teluguTitles.contains("Telugu Top Hits"))
        assertTrue(teluguTitles.contains("Telugu Movie Soundtracks"))
        assertTrue(teluguTitles.contains("Popular Telugu Albums"))

        val tamilTitles = HomeDiscoveryCatalog.TAMIL_SECTIONS.map { it.title }
        assertTrue(tamilTitles.contains("Tamil Top Hits"))
        assertTrue(tamilTitles.contains("Tamil Movie Soundtracks"))
        assertTrue(tamilTitles.contains("Popular Tamil Albums"))

        val hindiTitles = HomeDiscoveryCatalog.HINDI_SECTIONS.map { it.title }
        assertTrue(hindiTitles.contains("Hindi Top Hits"))
        assertTrue(hindiTitles.contains("New Bollywood Releases"))
        assertTrue(hindiTitles.contains("Hindi Movie Soundtracks"))

        val englishTitles = HomeDiscoveryCatalog.ENGLISH_SECTIONS.map { it.title }
        assertTrue(englishTitles.contains("Global Top Hits"))
        assertTrue(englishTitles.contains("EDM & Dance Hits"))
        assertTrue(englishTitles.contains("Popular English Albums"))
    }

    @Test
    fun repositoryExcludesNonAllowedLanguagesAndItunesPreviews() = runBlocking {
        val repo = HomeDiscoveryRepository(FakeHomeGateway())
        val teluguDef = HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("telugu_top_hits")
        val section = repo.loadSection(teluguDef)

        assertTrue(section.songs.isNotEmpty())
        assertTrue(section.songs.all { it.sourceProvider == "JioSaavn" })
        assertTrue(section.songs.all { it.playbackType == PlaybackType.FULL })
        assertTrue(section.songs.all { it.language == "telugu" })
        assertFalse(section.songs.any { it.language in HomeLanguagePolicy.EXCLUDED_LANGUAGES })
        assertFalse(section.songs.any { it.title.contains("iTunes", ignoreCase = true) })
    }

    @Test
    fun repositoryExcludesUnsupportedLanguageAlbumsAndPlaylists() = runBlocking {
        val repo = HomeDiscoveryRepository(FakeHomeGateway())
        val teluguAlbumDef = HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("telugu_movie_soundtracks")
        val albumSection = repo.loadSection(teluguAlbumDef)

        assertTrue(albumSection.albums.isNotEmpty())
        assertTrue(albumSection.albums.all { it.sourceProvider == "JioSaavn" })
        assertTrue(albumSection.albums.none { it.language == "kannada" })

        val playlistDef = HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("discovery_made_for_you")
        val playlistSection = repo.loadSection(playlistDef)
        assertTrue(playlistSection.playlists.isNotEmpty())
        assertTrue(playlistSection.playlists.none { it.title.contains("Punjabi", ignoreCase = true) })
    }

    @Test
    fun repositoryBuildsSixQuickAccessCardsAndPersonalizedRecommendations() = runBlocking {
        val gateway = FakeHomeGateway()
        val repo = HomeDiscoveryRepository(gateway)

        val teluguSec = repo.loadSection(HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("telugu_top_hits"))
        val tamilSec = repo.loadSection(HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("tamil_top_hits"))
        val englishSec = repo.loadSection(HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("english_global_top_hits"))
        val hindiSec = repo.loadSection(HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("hindi_top_hits"))

        val sectionsMap = listOf(teluguSec, tamilSec, englishSec, hindiSec).associateBy { it.id }

        val favSong = Song(
            id = 55L,
            title = "Inkem Inkem",
            artist = "Sid Sriram",
            album = "Geetha Govindam",
            durationMs = 240_000L,
            filePath = "saavn_55",
            albumArtUri = "https://c.saavncdn.com/55.jpg",
            contentUriString = "https://aac.saavncdn.com/55.mp4",
            isFavorite = true,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            language = "telugu",
            sourceProvider = "JioSaavn",
            isStreamable = true
        )

        val quickCards = repo.buildQuickAccessCards(
            sectionsById = sectionsMap,
            recentlyPlayed = listOf(favSong),
            favoriteSongs = listOf(favSong)
        )

        assertEquals(6, quickCards.size)
        assertEquals(
            listOf(
                HomeQuickAccessType.TELUGU_HITS,
                HomeQuickAccessType.TAMIL_HITS,
                HomeQuickAccessType.ENGLISH_HITS,
                HomeQuickAccessType.HINDI_HITS,
                HomeQuickAccessType.RECENTLY_PLAYED,
                HomeQuickAccessType.FAVORITE_SONGS
            ),
            quickCards.map { it.type }
        )
        assertNotNull(quickCards.first { it.type == HomeQuickAccessType.FAVORITE_SONGS }.artworkUri)

        val recDef = HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("discovery_recommended_for_you")
        repo.loadSection(recDef, favoriteSongs = listOf(favSong))
        assertTrue(gateway.songQueries.any { (q, _) -> q.contains("Sid Sriram", ignoreCase = true) })
    }

    @Test
    fun popularArtistsCatalogContainsExactRequestedFourLanguageArtistsWithoutDuplicates() {
        assertEquals(
            listOf("Anirudh Ravichander", "Sid Sriram", "Devi Sri Prasad", "Thaman S", "Armaan Malik", "Shreya Ghoshal"),
            HomeDiscoveryCatalog.TELUGU_ARTIST_SEEDS
        )
        assertEquals(
            listOf("A. R. Rahman", "Anirudh Ravichander", "Yuvan Shankar Raja", "Santhosh Narayanan", "Sid Sriram"),
            HomeDiscoveryCatalog.TAMIL_ARTIST_SEEDS
        )
        assertEquals(
            listOf("Arijit Singh", "Shreya Ghoshal", "Vishal-Shekhar", "Pritam", "Sonu Nigam", "Neha Kakkar"),
            HomeDiscoveryCatalog.HINDI_ARTIST_SEEDS
        )
        assertEquals(
            listOf("Taylor Swift", "Ed Sheeran", "The Weeknd", "Justin Bieber", "Bruno Mars", "Billie Eilish"),
            HomeDiscoveryCatalog.ENGLISH_ARTIST_SEEDS
        )

        val allSeeds = HomeDiscoveryCatalog.POPULAR_ARTIST_SEEDS[HomeLanguageFilter.ALL].orEmpty()
        assertEquals(20, allSeeds.size)
        assertEquals(allSeeds.distinct().size, allSeeds.size)

        assertEquals(listOf("Telugu", "Tamil"), HomeDiscoveryCatalog.languagesForArtist("Anirudh Ravichander"))
        assertEquals(listOf("Telugu", "Tamil"), HomeDiscoveryCatalog.languagesForArtist("Sid Sriram"))
        assertEquals(listOf("Telugu", "Hindi"), HomeDiscoveryCatalog.languagesForArtist("Shreya Ghoshal"))
    }

    @Test
    fun repositoryLoadsPopularArtistsWithoutDuplicatesAcrossLanguages() = runBlocking {
        val repo = HomeDiscoveryRepository(FakeHomeGateway())
        val popularDef = HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("discovery_popular_artists")
        val section = repo.loadSection(popularDef)

        assertEquals(20, section.artists.size)
        assertEquals(20, section.artists.map { it.name.lowercase() }.distinct().size)
        val anirudh = section.artists.first { it.name == "Anirudh Ravichander" }
        assertEquals(listOf("Telugu", "Tamil"), anirudh.languages)
        assertTrue(section.artists.all { it.sourceProvider == "JioSaavn" })
    }

    @Test
    fun loadArtistDetailsFiltersUnrelatedTracksAndItunesPreviewsAndDeduplicates() = runBlocking {
        val artistGateway = object : HomeMusicGateway {
            override suspend fun searchSongs(query: String, limit: Int, page: Int): List<Song> {
                return listOf(
                    Song(
                        id = 1L,
                        title = "Tum Hi Ho",
                        artist = "Arijit Singh",
                        album = "Aashiqui 2",
                        durationMs = 262_000L,
                        filePath = "saavn_arijit_1",
                        contentUriString = "https://aac.saavncdn.com/arijit1.mp4",
                        isOnline = true,
                        playbackType = PlaybackType.FULL,
                        language = "hindi",
                        sourceProvider = "JioSaavn",
                        releaseYear = "2025",
                        albumId = "alb_10",
                        isStreamable = true
                    ),
                    // Duplicate PID should be removed
                    Song(
                        id = 2L,
                        title = "Tum Hi Ho",
                        artist = "Arijit Singh",
                        album = "Aashiqui 2",
                        durationMs = 262_000L,
                        filePath = "saavn_arijit_1",
                        contentUriString = "https://aac.saavncdn.com/arijit1.mp4",
                        isOnline = true,
                        playbackType = PlaybackType.FULL,
                        language = "hindi",
                        sourceProvider = "JioSaavn",
                        albumId = "alb_10",
                        isStreamable = true
                    ),
                    // Unrelated artist track should be excluded
                    Song(
                        id = 3L,
                        title = "Unrelated Song",
                        artist = "Random DJ",
                        album = "Other Album",
                        durationMs = 200_000L,
                        filePath = "saavn_other_3",
                        contentUriString = "https://aac.saavncdn.com/other.mp4",
                        isOnline = true,
                        playbackType = PlaybackType.FULL,
                        language = "hindi",
                        sourceProvider = "JioSaavn",
                        isStreamable = true
                    ),
                    // iTunes 30s preview even with matching artist name must be excluded
                    Song(
                        id = 4L,
                        title = "Preview Song",
                        artist = "Arijit Singh",
                        album = "Preview Album",
                        durationMs = 30_000L,
                        filePath = "itunes_4",
                        contentUriString = "https://audio-ssl.itunes.apple.com/preview.m4a",
                        isOnline = true,
                        playbackType = PlaybackType.PREVIEW,
                        language = "hindi",
                        sourceProvider = "iTunes",
                        isStreamable = true
                    )
                )
            }

            override suspend fun searchAlbums(query: String, limit: Int, page: Int): List<AlbumSearchResult> {
                return listOf(
                    AlbumSearchResult(
                        id = "alb_10",
                        title = "Aashiqui 2",
                        artist = "Arijit Singh",
                        year = "2013",
                        songCount = 6,
                        sourceProvider = "JioSaavn",
                        language = "hindi"
                    ),
                    AlbumSearchResult(
                        id = "alb_unrelated",
                        title = "Unrelated Album",
                        artist = "Completely Different",
                        year = "2024",
                        sourceProvider = "JioSaavn",
                        language = "hindi"
                    )
                )
            }

            override suspend fun searchArtists(query: String, limit: Int): List<OnlineArtistResult> = emptyList()

            override suspend fun searchPlaylists(query: String, limit: Int): List<CuratedPlaylist> {
                return listOf(
                    CuratedPlaylist(id = "pl_a_1", title = "Best of Arijit Singh", description = "JioSaavn"),
                    CuratedPlaylist(id = "pl_punjabi", title = "Punjabi Hits", description = "Punjabi")
                )
            }

            override suspend fun getPlaylistDetails(playlist: CuratedPlaylist): CuratedPlaylist? = playlist
        }

        val repo = HomeDiscoveryRepository(artistGateway)
        val details = repo.loadArtistDetails(
            OnlineArtistResult(
                id = "459320",
                name = "Arijit Singh",
                subtitle = "Singer",
                sourceProvider = "JioSaavn",
                languages = listOf("Hindi")
            )
        )

        assertNotNull(details)
        assertEquals(1, details!!.popularSongs.size)
        assertEquals("Tum Hi Ho", details.popularSongs.first().title)
        assertTrue(details.popularSongs.none { it.title == "Unrelated Song" })
        assertTrue(details.popularSongs.none { it.sourceProvider == "iTunes" })
        assertEquals(1, details.albums.size)
        assertEquals("Aashiqui 2", details.albums.first().title)
        assertEquals(1, details.playlists.size)
        assertEquals("Best of Arijit Singh", details.playlists.first().title)
    }
}

