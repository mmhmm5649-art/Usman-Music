package com.usman.musicplayer.data.repository

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.data.remote.JioSaavnApiService
import com.usman.musicplayer.data.repository.providers.JioSaavnMusicProvider
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.ArtistDetailsData
import com.usman.musicplayer.model.ArtistMatchingPolicy
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeHeroFeature
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.HomeNotificationItem
import com.usman.musicplayer.model.HomeQuickAccessCard
import com.usman.musicplayer.model.HomeQuickAccessType
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.HomeSectionDefinition
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.sdk.ProviderRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface HomeMusicGateway {
    suspend fun searchSongs(query: String, limit: Int = 20, page: Int = 1): List<Song>
    suspend fun searchAlbums(query: String, limit: Int = 16, page: Int = 1): List<AlbumSearchResult>
    suspend fun searchArtists(query: String, limit: Int = 10): List<OnlineArtistResult>
    suspend fun getArtistDetails(artist: OnlineArtistResult): ArtistDetailsData? = null
    suspend fun searchPlaylists(query: String, limit: Int = 10): List<CuratedPlaylist>
    suspend fun getPlaylistDetails(playlist: CuratedPlaylist): CuratedPlaylist?
}

class JioSaavnHomeGateway(
    private val providerOverride: JioSaavnMusicProvider? = null
) : HomeMusicGateway {

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val apiService: JioSaavnApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.jiosaavn.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JioSaavnApiService::class.java)
    }

    private val fallbackProvider: JioSaavnMusicProvider by lazy {
        JioSaavnMusicProvider(apiService, moshi)
    }

    private fun provider(): JioSaavnMusicProvider {
        return providerOverride
            ?: ProviderRegistry.activeProviders.filterIsInstance<JioSaavnMusicProvider>().firstOrNull()
            ?: fallbackProvider
    }

    override suspend fun searchSongs(query: String, limit: Int, page: Int): List<Song> {
        return runCatching {
            withTimeoutOrNull(7000L) {
                provider().searchPaginated(query, limit, page).getOrDefault(emptyList())
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override suspend fun searchAlbums(query: String, limit: Int, page: Int): List<AlbumSearchResult> {
        return runCatching {
            withTimeoutOrNull(7000L) {
                provider().searchAlbumsPaginated(query, limit, page).getOrDefault(emptyList())
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override suspend fun searchArtists(query: String, limit: Int): List<OnlineArtistResult> {
        return runCatching {
            withTimeoutOrNull(6000L) {
                provider().searchArtists(query, limit).getOrDefault(emptyList())
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override suspend fun getArtistDetails(artist: OnlineArtistResult): ArtistDetailsData? {
        return runCatching {
            withTimeoutOrNull(7500L) {
                provider().getArtistDetails(artist).getOrNull()
            }
        }.getOrNull()
    }

    override suspend fun searchPlaylists(query: String, limit: Int): List<CuratedPlaylist> {
        return runCatching {
            withTimeoutOrNull(6500L) {
                provider().searchPlaylists(query, limit).getOrDefault(emptyList())
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override suspend fun getPlaylistDetails(playlist: CuratedPlaylist): CuratedPlaylist? {
        return runCatching {
            withTimeoutOrNull(6500L) {
                provider().getPlaylistDetails(
                    playlistId = playlist.id,
                    fallbackTitle = playlist.title,
                    fallbackDescription = playlist.description,
                    fallbackArtwork = playlist.coverArtUri
                ).getOrNull()
            }
        }.getOrNull()
    }
}

class HomeDiscoveryRepository(
    private val gateway: HomeMusicGateway = JioSaavnHomeGateway()
) {

    private data class CachedSection(
        val section: HomeDiscoverySection,
        val timestampMs: Long
    )

    private data class CachedArtistDetails(
        val details: ArtistDetailsData,
        val timestampMs: Long
    )

    private val sectionCache = ConcurrentHashMap<String, CachedSection>()
    private val artistDetailsCache = ConcurrentHashMap<String, CachedArtistDetails>()
    private val requestSemaphore = Semaphore(3)

    suspend fun loadSection(
        definition: HomeSectionDefinition,
        page: Int = 1,
        recentlyPlayed: List<Song> = emptyList(),
        favoriteSongs: List<Song> = emptyList(),
        playStats: List<PersistedPlayStat> = emptyList(),
        forceRefresh: Boolean = false
    ): HomeDiscoverySection = withContext(Dispatchers.IO) {
        if (page == 1 && !forceRefresh && !definition.isPersonalized) {
            val cached = sectionCache[definition.id]
            if (cached != null &&
                cached.section.hasContent &&
                (System.currentTimeMillis() - cached.timestampMs) < CACHE_TTL_MS
            ) {
                return@withContext cached.section
            }
        }

        val resolved = try {
            requestSemaphore.withPermit {
                fetchSectionInternal(
                    definition = definition,
                    page = page,
                    recentlyPlayed = recentlyPlayed,
                    favoriteSongs = favoriteSongs,
                    playStats = playStats
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading home section ${definition.id}: ${e.message}")
            HomeDiscoverySection(
                id = definition.id,
                title = definition.title,
                subtitle = definition.subtitle,
                language = definition.language,
                contentType = definition.contentType,
                isLoading = false,
                hasAttemptedLoad = true,
                canLoadMore = false,
                currentPage = page,
                isPersonalized = definition.isPersonalized
            )
        }

        if (page == 1 && resolved.hasContent && !definition.isPersonalized) {
            sectionCache[definition.id] = CachedSection(resolved, System.currentTimeMillis())
        }
        resolved
    }

    private suspend fun fetchSectionInternal(
        definition: HomeSectionDefinition,
        page: Int,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        playStats: List<PersistedPlayStat>
    ): HomeDiscoverySection {
        return when (definition.contentType) {
            HomeSectionContentType.SONGS -> fetchSongsSection(definition, page, recentlyPlayed, favoriteSongs, playStats)
            HomeSectionContentType.ALBUMS -> fetchAlbumsSection(definition, page)
            HomeSectionContentType.ARTISTS -> fetchArtistsSection(definition)
            HomeSectionContentType.PLAYLISTS -> fetchPlaylistsSection(definition, recentlyPlayed, favoriteSongs)
        }
    }

    private suspend fun fetchSongsSection(
        definition: HomeSectionDefinition,
        page: Int,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        playStats: List<PersistedPlayStat>
    ): HomeDiscoverySection {
        val rawSongs = mutableListOf<Song>()

        if (definition.id.startsWith("discovery_heavy_rotation") || definition.id.startsWith("discovery_heavy_")) {
            val heavyFromStats = playStats
                .sortedWith(compareByDescending<PersistedPlayStat> { it.count }.thenByDescending { it.lastPlayedAtMs })
                .map { stat ->
                    val s = stat.song
                    Song(
                        id = s.id,
                        title = s.title,
                        artist = s.artist,
                        album = s.album,
                        durationMs = s.durationMs,
                        filePath = s.filePath,
                        folderName = s.folderName,
                        albumArtUri = s.albumArtUri,
                        contentUriString = s.contentUriString,
                        isOnline = s.isOnline,
                        externalUrl = s.externalUrl,
                        language = s.language,
                        sourceProvider = s.sourceProvider,
                        isStreamable = true,
                        isFullTrackVerified = true,
                        isRightsVerified = true,
                        albumId = s.albumId,
                        releaseYear = s.releaseYear,
                        albumUrl = s.albumUrl
                    )
                }
                .filter { HomeLanguagePolicy.matchesTargetLanguage(it.language, definition.language, "${it.title} ${it.album}") }

            val combinedPersonal = (heavyFromStats + recentlyPlayed + favoriteSongs)
                .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                .filter { HomeLanguagePolicy.matchesTargetLanguage(it.language, definition.language, "${it.title} ${it.album}") }
                .distinctBy { songDedupKey(it) }
            rawSongs.addAll(combinedPersonal)
        }

        val queryToUse = rotateQuery(definition)
        val personalizedQuery = when {
            definition.id.startsWith("discovery_recommended_") -> buildPersonalizedRecommendationQuery(
                targetLanguage = definition.language,
                recentlyPlayed = recentlyPlayed,
                favoriteSongs = favoriteSongs,
                playStats = playStats,
                fallbackQuery = queryToUse
            )
            definition.id == "discovery_discover_weekly" -> buildDiscoverWeeklyQuery(
                recentlyPlayed = recentlyPlayed,
                favoriteSongs = favoriteSongs,
                fallbackQuery = queryToUse
            )
            else -> queryToUse
        }

        if (rawSongs.size < 12) {
            val fetchedPrimary = gateway.searchSongs(personalizedQuery, limit = 22, page = page)
            rawSongs.addAll(fetchedPrimary)
            if (fetchedPrimary.isEmpty() && page == 1) {
                for (alt in definition.fallbackQueries) {
                    val altResults = gateway.searchSongs(alt, limit = 20, page = 1)
                    if (altResults.isNotEmpty()) {
                        rawSongs.addAll(altResults)
                        break
                    }
                }
            }
        }

        // For ALL Trending / New Releases, blend across the 4 allowed languages cleanly
        if (definition.language == HomeLanguageFilter.ALL &&
            (definition.id == "discovery_trending_now" || definition.id == "discovery_new_releases") &&
            page == 1 &&
            rawSongs.size < 16
        ) {
            val langSeed = if (definition.id == "discovery_trending_now") {
                listOf("telugu top hits", "tamil top hits", "hindi top hits", "english pop hits")
            } else {
                listOf("latest telugu songs", "latest tamil songs", "latest hindi songs", "latest english pop")
            }
            val extra = coroutineScope {
                langSeed.map { q -> async { gateway.searchSongs(q, limit = 6, page = 1) } }.awaitAll().flatten()
            }
            rawSongs.addAll(extra)
        }

        val verified = rawSongs
            .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
            .filter { HomeLanguagePolicy.matchesTargetLanguage(it.language, definition.language, "${it.title} ${it.album}") }
            .sortedByDescending {
                val targetLang = definition.language.jioSaavnLanguage
                val matchesExplicit = targetLang != null && HomeLanguagePolicy.normalizeLanguage(it.language) == targetLang
                if (matchesExplicit) 1 else 0
            }
            .distinctBy { songDedupKey(it) }

        return HomeDiscoverySection(
            id = definition.id,
            title = definition.title,
            subtitle = definition.subtitle,
            language = definition.language,
            contentType = definition.contentType,
            songs = verified,
            isLoading = false,
            hasAttemptedLoad = true,
            canLoadMore = verified.size >= 10,
            currentPage = page,
            isPersonalized = definition.isPersonalized
        )
    }

    private suspend fun fetchAlbumsSection(
        definition: HomeSectionDefinition,
        page: Int
    ): HomeDiscoverySection {
        val collected = mutableListOf<AlbumSearchResult>()
        val primary = gateway.searchAlbums(definition.query, limit = 16, page = page)
        collected.addAll(primary)

        if (collected.size < 4 && page == 1) {
            for (alt in definition.fallbackQueries) {
                val extra = gateway.searchAlbums(alt, limit = 12, page = 1)
                collected.addAll(extra)
                if (collected.size >= 10) break
            }
        }

        // Fallback: derive verified albums from JioSaavn soundtrack/album songs when needed
        if (collected.size < 4 && page == 1) {
            val songSeed = gateway.searchSongs(definition.query, limit = 20, page = 1)
                .filter {
                    HomeLanguagePolicy.isAllowedJioSaavnSong(it) &&
                        !it.albumId.isNullOrBlank() &&
                        !it.album.equals("Unknown Album", ignoreCase = true)
                }
                .filter { HomeLanguagePolicy.matchesTargetLanguage(it.language, definition.language, "${it.title} ${it.album}") }

            songSeed.groupBy { it.albumId!! }.values.forEach { tracks ->
                val lead = tracks.first()
                collected.add(
                    AlbumSearchResult(
                        id = lead.albumId!!,
                        title = lead.album,
                        movieName = if (definition.isMovieSoundtrack) lead.album else null,
                        artist = lead.artist,
                        year = lead.releaseYear,
                        songCount = tracks.size,
                        artworkUri = lead.albumArtUri,
                        sourceProvider = "JioSaavn",
                        externalUrl = lead.albumUrl,
                        language = lead.language,
                        isCompleteAlbum = false
                    )
                )
            }
        }

        val cleaned = collected
            .filter { HomeLanguagePolicy.isAllowedJioSaavnAlbum(it) }
            .filter { HomeLanguagePolicy.matchesTargetLanguage(it.language, definition.language, "${it.title} ${it.movieName.orEmpty()}") }
            .distinctBy { it.id.ifBlank { "${it.title.lowercase()}_${it.artist.lowercase()}" } }

        return HomeDiscoverySection(
            id = definition.id,
            title = definition.title,
            subtitle = definition.subtitle,
            language = definition.language,
            contentType = HomeSectionContentType.ALBUMS,
            albums = cleaned,
            isLoading = false,
            hasAttemptedLoad = true,
            canLoadMore = cleaned.size >= 8,
            currentPage = page,
            isPersonalized = definition.isPersonalized
        )
    }

    private suspend fun fetchArtistsSection(definition: HomeSectionDefinition): HomeDiscoverySection {
        val seeds = HomeDiscoveryCatalog.POPULAR_ARTIST_SEEDS[definition.language]
            ?: HomeDiscoveryCatalog.POPULAR_ARTIST_SEEDS.getValue(HomeLanguageFilter.ALL)

        val resolvedArtists = coroutineScope {
            seeds.map { artistName ->
                async {
                    resolveVerifiedArtist(artistName, definition.language)
                }
            }.awaitAll().filterNotNull()
        }

        val dedupedArtists = ArtistMatchingPolicy.deduplicateArtists(resolvedArtists)

        return HomeDiscoverySection(
            id = definition.id,
            title = definition.title,
            subtitle = definition.subtitle,
            language = definition.language,
            contentType = HomeSectionContentType.ARTISTS,
            artists = dedupedArtists,
            isLoading = false,
            hasAttemptedLoad = true,
            canLoadMore = false,
            currentPage = 1,
            isPersonalized = definition.isPersonalized
        )
    }

    private suspend fun resolveVerifiedArtist(
        artistName: String,
        targetLanguage: HomeLanguageFilter
    ): OnlineArtistResult? {
        val knownLanguages = HomeDiscoveryCatalog.languagesForArtist(artistName).ifEmpty {
            if (targetLanguage != HomeLanguageFilter.ALL) listOf(targetLanguage.displayName) else emptyList()
        }
        val candidates = gateway.searchArtists(artistName, limit = 6)
            .filter { it.sourceProvider.equals("JioSaavn", ignoreCase = true) }

        val bestMatch = candidates.firstOrNull { ArtistMatchingPolicy.artistsMatch(it.name, artistName) }
            ?: candidates.firstOrNull { !it.artworkUri.isNullOrBlank() && ArtistMatchingPolicy.artistsMatch(artistName, it.name) }

        if (bestMatch != null) {
            val resolvedArtwork = bestMatch.artworkUri?.takeIf { it.isNotBlank() }
                ?: gateway.searchSongs(artistName, limit = 4, page = 1)
                    .firstOrNull { HomeLanguagePolicy.isAllowedJioSaavnSong(it) && ArtistMatchingPolicy.songMatchesArtist(it, artistName) && !it.albumArtUri.isNullOrBlank() }
                    ?.albumArtUri
            val subtitle = knownLanguages.takeIf { it.isNotEmpty() }?.joinToString(" • ")
                ?: bestMatch.subtitle
                    ?.replace(Regex("(?i)\\bjiosaavn(\\s*artist)?\\b"), "")
                    ?.trim(' ', '•', '-')
                    ?.takeIf { it.isNotBlank() }
                ?: "Featured Artist"
            return bestMatch.copy(
                name = artistName,
                artworkUri = resolvedArtwork,
                subtitle = subtitle,
                languages = (knownLanguages + bestMatch.languages).distinct()
            )
        }

        // Only surface artist via song availability if JioSaavn has verified matching songs
        val verifiedSongs = gateway.searchSongs(artistName, limit = 6, page = 1)
            .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) && ArtistMatchingPolicy.songMatchesArtist(it, artistName) }
        val leadSong = verifiedSongs.firstOrNull() ?: return null
        val subtitle = knownLanguages.takeIf { it.isNotEmpty() }?.joinToString(" • ") ?: "Featured Artist"
        return OnlineArtistResult(
            id = "seed_${ArtistMatchingPolicy.normalizeArtistName(artistName).replace(' ', '_')}",
            name = artistName,
            artworkUri = leadSong.albumArtUri,
            subtitle = subtitle,
            sourceProvider = "JioSaavn",
            languages = knownLanguages
        )
    }

    suspend fun loadArtistDetails(
        artist: OnlineArtistResult,
        preferredLanguage: HomeLanguageFilter = HomeLanguageFilter.ALL,
        forceRefresh: Boolean = false
    ): ArtistDetailsData = withContext(Dispatchers.IO) {
        val cacheKey = "${ArtistMatchingPolicy.normalizeArtistName(artist.name)}|${preferredLanguage.name}"
        if (!forceRefresh) {
            val cached = artistDetailsCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.timestampMs) < CACHE_TTL_MS) {
                return@withContext cached.details
            }
        }

        // Resolve provider artist record if current ID isn't a genuine provider ID
        val resolvedArtist = if (ArtistMatchingPolicy.isProviderArtistId(artist.id)) {
            artist
        } else {
            gateway.searchArtists(artist.name, limit = 5)
                .firstOrNull {
                    it.sourceProvider.equals("JioSaavn", ignoreCase = true) &&
                        ArtistMatchingPolicy.artistsMatch(it.name, artist.name)
                }
                ?.let { found ->
                    found.copy(
                        name = artist.name,
                        artworkUri = found.artworkUri?.takeIf { it.isNotBlank() } ?: artist.artworkUri,
                        languages = (artist.languages + found.languages).distinct()
                    )
                }
                ?: artist
        }

        val knownLangs = (resolvedArtist.languages + HomeDiscoveryCatalog.languagesForArtist(artist.name))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val langQueryToken = if (preferredLanguage != HomeLanguageFilter.ALL &&
            knownLangs.any { it.equals(preferredLanguage.displayName, ignoreCase = true) }
        ) {
            preferredLanguage.displayName
        } else {
            ""
        }

        val details = coroutineScope {
            val officialDeferred = async {
                if (ArtistMatchingPolicy.isProviderArtistId(resolvedArtist.id)) {
                    gateway.getArtistDetails(resolvedArtist)
                } else {
                    null
                }
            }
            val topSongsDeferred = async {
                val query = listOf(artist.name, langQueryToken).filter { it.isNotBlank() }.joinToString(" ")
                gateway.searchSongs(query, limit = 25, page = 1)
            }
            val generalSongsDeferred = async {
                if (langQueryToken.isNotBlank()) {
                    gateway.searchSongs(artist.name, limit = 20, page = 1)
                } else {
                    emptyList()
                }
            }
            val latestSongsDeferred = async {
                gateway.searchSongs("latest ${artist.name} songs", limit = 15, page = 1)
            }
            val albumsDeferred = async {
                gateway.searchAlbums(artist.name, limit = 16, page = 1)
            }
            val playlistsDeferred = async {
                gateway.searchPlaylists(artist.name, limit = 10)
            }

            val official = officialDeferred.await()
            val officialPopular = official?.popularSongs.orEmpty()
                .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                .filter { ArtistMatchingPolicy.songMatchesArtist(it, artist.name) || official?.id == resolvedArtist.id }
            val searchedPopular = (topSongsDeferred.await() + generalSongsDeferred.await())
                .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                .filter { ArtistMatchingPolicy.songMatchesArtist(it, artist.name) }

            val popularSongs = ArtistMatchingPolicy.deduplicateSongs(officialPopular + searchedPopular)

            val officialLatest = official?.latestReleases.orEmpty()
                .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                .filter { ArtistMatchingPolicy.songMatchesArtist(it, artist.name) || official?.id == resolvedArtist.id }
            val searchedLatest = latestSongsDeferred.await()
                .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                .filter { ArtistMatchingPolicy.songMatchesArtist(it, artist.name) }

            val yearSortedCandidates = ArtistMatchingPolicy.deduplicateSongs(officialLatest + searchedLatest + popularSongs)
                .sortedWith(
                    compareByDescending<Song> { it.releaseYear?.toIntOrNull() ?: 0 }
                        .thenByDescending { song -> officialLatest.any { it.filePath == song.filePath } }
                )
                .filter { song ->
                    val y = song.releaseYear?.toIntOrNull()
                    (y != null && y >= 2022) || officialLatest.any { it.filePath == song.filePath } || searchedLatest.any { it.filePath == song.filePath }
                }
                .take(12)

            val officialAlbums = official?.albums.orEmpty()
                .filter { HomeLanguagePolicy.isAllowedJioSaavnAlbum(it) }
            val searchedAlbums = albumsDeferred.await()
                .filter { HomeLanguagePolicy.isAllowedJioSaavnAlbum(it) }
                .filter { ArtistMatchingPolicy.albumMatchesArtist(it, artist.name) }
            val derivedAlbumsFromSongs = deriveAlbumsFromArtistSongs(popularSongs + yearSortedCandidates, artist.name)

            val albums = ArtistMatchingPolicy.deduplicateAlbums(officialAlbums + searchedAlbums + derivedAlbumsFromSongs)
                .sortedByDescending { it.year?.toIntOrNull() ?: 0 }

            val officialPlaylists = official?.playlists.orEmpty()
                .filter { HomeLanguagePolicy.isAllowedHomePlaylist(it) }
            val searchedPlaylists = playlistsDeferred.await()
                .filter { HomeLanguagePolicy.isAllowedHomePlaylist(it) }
            val playlists = (officialPlaylists + searchedPlaylists)
                .distinctBy { it.id.ifBlank { it.title.lowercase() } }

            val finalArtwork = official?.artworkUri?.takeIf { it.isNotBlank() }
                ?: resolvedArtist.artworkUri?.takeIf { it.isNotBlank() }
                ?: popularSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
                ?: albums.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri

            val mergedLanguages = (knownLangs + official?.languages.orEmpty()).distinct()

            ArtistDetailsData(
                id = official?.id ?: resolvedArtist.id,
                name = artist.name,
                artworkUri = finalArtwork,
                subtitle = official?.subtitle ?: resolvedArtist.subtitle ?: mergedLanguages.takeIf { it.isNotEmpty() }?.joinToString(" • "),
                languages = mergedLanguages,
                sourceProvider = "JioSaavn",
                popularSongs = popularSongs,
                latestReleases = yearSortedCandidates,
                albums = albums,
                playlists = playlists
            )
        }

        if (details.popularSongs.isNotEmpty() || details.albums.isNotEmpty() || details.playlists.isNotEmpty()) {
            artistDetailsCache[cacheKey] = CachedArtistDetails(details, System.currentTimeMillis())
        }
        details
    }

    private fun deriveAlbumsFromArtistSongs(songs: List<Song>, artistName: String): List<AlbumSearchResult> {
        return songs
            .filter { !it.albumId.isNullOrBlank() && !it.album.equals("Unknown Album", ignoreCase = true) }
            .groupBy { it.albumId!! }
            .values
            .mapNotNull { tracks ->
                val lead = tracks.firstOrNull() ?: return@mapNotNull null
                AlbumSearchResult(
                    id = lead.albumId!!,
                    title = lead.album,
                    artist = artistName,
                    year = lead.releaseYear,
                    songCount = tracks.size,
                    artworkUri = lead.albumArtUri,
                    sourceProvider = "JioSaavn",
                    externalUrl = lead.albumUrl,
                    language = lead.language,
                    isCompleteAlbum = false
                )
            }
    }

    private suspend fun fetchPlaylistsSection(
        definition: HomeSectionDefinition,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>
    ): HomeDiscoverySection {
        val playlists = mutableListOf<CuratedPlaylist>()

        val languageSeeds = if (definition.language == HomeLanguageFilter.ALL) {
            listOf(
                "telugu hits" to "Telugu",
                "tamil hits" to "Tamil",
                "hindi top hits" to "Hindi",
                "english pop hits" to "English"
            )
        } else {
            val langName = definition.language.displayName
            listOf(
                "${langName.lowercase()} top hits" to langName,
                "${langName.lowercase()} love romantic" to "$langName Love"
            )
        }

        // Fetch authentic JioSaavn playlists first
        val fetchedPlaylists = coroutineScope {
            languageSeeds.map { (q, _) ->
                async { gateway.searchPlaylists(q, limit = 3) }
            }.awaitAll().flatten()
        }
            .filter { HomeLanguagePolicy.isAllowedHomePlaylist(it) }
            .distinctBy { it.id.ifBlank { it.title.lowercase() } }
            .take(8)

        playlists.addAll(fetchedPlaylists)

        // Hydrate song Mix playlists from real JioSaavn songs when provider returns thin list
        if (playlists.size < 4) {
            val songMixes = coroutineScope {
                languageSeeds.map { (query, label) ->
                    async {
                        val songs = gateway.searchSongs(query, limit = 15, page = 1)
                            .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
                            .filter { HomeLanguagePolicy.matchesTargetLanguage(it.language, definition.language, "${it.title} ${it.album}") }
                        if (songs.isNotEmpty()) {
                            CuratedPlaylist(
                                id = "saavn_mix_${label.lowercase().replace(" ", "_")}",
                                title = "$label Daily Mix",
                                description = "Full-length tracks curated for $label listeners",
                                coverArtUri = songs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                                songs = songs
                            )
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            playlists.addAll(songMixes)
        }

        // Personalized Favourite Mix if user has saved favourites
        if (favoriteSongs.isNotEmpty() && definition.language == HomeLanguageFilter.ALL) {
            val favs = favoriteSongs.filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
            if (favs.isNotEmpty()) {
                playlists.add(
                    0,
                    CuratedPlaylist(
                        id = "personalized_favorites_mix",
                        title = "Your Favourite Mix",
                        description = "${favs.size} liked tracks ready to stream",
                        coverArtUri = favs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                        songs = favs
                    )
                )
            }
        }

        val deduped = playlists
            .filter { HomeLanguagePolicy.isAllowedHomePlaylist(it) }
            .distinctBy { it.id.ifBlank { it.title.lowercase() } }

        return HomeDiscoverySection(
            id = definition.id,
            title = definition.title,
            subtitle = definition.subtitle,
            language = definition.language,
            contentType = HomeSectionContentType.PLAYLISTS,
            playlists = deduped,
            isLoading = false,
            hasAttemptedLoad = true,
            canLoadMore = false,
            currentPage = 1,
            isPersonalized = definition.isPersonalized
        )
    }

    suspend fun hydratePlaylistIfNeeded(playlist: CuratedPlaylist): CuratedPlaylist {
        if (playlist.songs.isNotEmpty()) return playlist
        val details = gateway.getPlaylistDetails(playlist)
        if (details != null && details.songs.isNotEmpty()) {
            val filtered = details.songs.filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
            if (filtered.isNotEmpty()) {
                return details.copy(songs = filtered)
            }
        }
        val fallbackSongs = gateway.searchSongs(playlist.title, limit = 20, page = 1)
            .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
        return playlist.copy(
            coverArtUri = playlist.coverArtUri ?: fallbackSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
            songs = fallbackSongs
        )
    }

    fun buildQuickAccessCards(
        sectionsById: Map<String, HomeDiscoverySection>,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>
    ): List<HomeQuickAccessCard> {
        val teluguSongs = sectionsById["telugu_top_hits"]?.songs.orEmpty()
        val tamilSongs = sectionsById["tamil_top_hits"]?.songs.orEmpty()
        val englishSongs = sectionsById["english_global_top_hits"]?.songs.orEmpty()
        val hindiSongs = sectionsById["hindi_top_hits"]?.songs.orEmpty()
        val cleanRecent = recentlyPlayed.filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
        val cleanFavs = favoriteSongs.filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }

        return listOf(
            HomeQuickAccessCard(
                id = "qa_telugu_hits",
                type = HomeQuickAccessType.TELUGU_HITS,
                title = "Telugu Hits",
                subtitle = if (teluguSongs.isNotEmpty()) "${teluguSongs.size} Tollywood tracks" else "Tollywood top hits",
                artworkUri = teluguSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                trackCount = teluguSongs.size,
                languageFilter = HomeLanguageFilter.TELUGU
            ),
            HomeQuickAccessCard(
                id = "qa_tamil_hits",
                type = HomeQuickAccessType.TAMIL_HITS,
                title = "Tamil Hits",
                subtitle = if (tamilSongs.isNotEmpty()) "${tamilSongs.size} Kollywood tracks" else "Kollywood top hits",
                artworkUri = tamilSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                trackCount = tamilSongs.size,
                languageFilter = HomeLanguageFilter.TAMIL
            ),
            HomeQuickAccessCard(
                id = "qa_english_hits",
                type = HomeQuickAccessType.ENGLISH_HITS,
                title = "English Hits",
                subtitle = if (englishSongs.isNotEmpty()) "${englishSongs.size} global tracks" else "Global pop hits",
                artworkUri = englishSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                trackCount = englishSongs.size,
                languageFilter = HomeLanguageFilter.ENGLISH
            ),
            HomeQuickAccessCard(
                id = "qa_hindi_hits",
                type = HomeQuickAccessType.HINDI_HITS,
                title = "Hindi Hits",
                subtitle = if (hindiSongs.isNotEmpty()) "${hindiSongs.size} Bollywood tracks" else "Bollywood chartbusters",
                artworkUri = hindiSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                trackCount = hindiSongs.size,
                languageFilter = HomeLanguageFilter.HINDI
            ),
            HomeQuickAccessCard(
                id = "qa_recently_played",
                type = HomeQuickAccessType.RECENTLY_PLAYED,
                title = "Recently Played",
                subtitle = if (cleanRecent.isNotEmpty()) "${cleanRecent.size} tracks played" else "Your listening history",
                artworkUri = cleanRecent.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
                    ?: sectionsById["discovery_trending_now"]?.songs?.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                trackCount = cleanRecent.size,
                languageFilter = HomeLanguageFilter.ALL
            ),
            HomeQuickAccessCard(
                id = "qa_favorite_songs",
                type = HomeQuickAccessType.FAVORITE_SONGS,
                title = "Your Favourite Songs",
                subtitle = if (cleanFavs.isNotEmpty()) "${cleanFavs.size} liked tracks" else "Tap ♥ on songs you love",
                artworkUri = cleanFavs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
                    ?: hindiSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                trackCount = cleanFavs.size,
                languageFilter = HomeLanguageFilter.ALL
            )
        )
    }

    fun buildHeroFeature(
        activeLanguage: HomeLanguageFilter,
        sectionsById: Map<String, HomeDiscoverySection>,
        recentlyPlayed: List<Song>
    ): HomeHeroFeature? {
        val preferredSongs: List<Song> = when (activeLanguage) {
            HomeLanguageFilter.TELUGU -> sectionsById["telugu_top_hits"]?.songs.orEmpty()
                .ifEmpty { sectionsById["telugu_latest_songs"]?.songs.orEmpty() }
            HomeLanguageFilter.TAMIL -> sectionsById["tamil_top_hits"]?.songs.orEmpty()
                .ifEmpty { sectionsById["tamil_latest_songs"]?.songs.orEmpty() }
            HomeLanguageFilter.ENGLISH -> sectionsById["english_global_top_hits"]?.songs.orEmpty()
                .ifEmpty { sectionsById["english_latest_songs"]?.songs.orEmpty() }
            HomeLanguageFilter.HINDI -> sectionsById["hindi_top_hits"]?.songs.orEmpty()
                .ifEmpty { sectionsById["hindi_latest_songs"]?.songs.orEmpty() }
            HomeLanguageFilter.ALL -> sectionsById["discovery_trending_now"]?.songs.orEmpty()
                .ifEmpty { sectionsById["discovery_new_releases"]?.songs.orEmpty() }
                .ifEmpty { recentlyPlayed }
        }.filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }

        val lead = preferredSongs.firstOrNull()
        val albumCandidate = when (activeLanguage) {
            HomeLanguageFilter.TELUGU -> sectionsById["telugu_movie_soundtracks"]?.albums?.firstOrNull()
            HomeLanguageFilter.TAMIL -> sectionsById["tamil_movie_soundtracks"]?.albums?.firstOrNull()
            HomeLanguageFilter.ENGLISH -> sectionsById["english_popular_albums"]?.albums?.firstOrNull()
            HomeLanguageFilter.HINDI -> sectionsById["hindi_movie_soundtracks"]?.albums?.firstOrNull()
            HomeLanguageFilter.ALL -> sectionsById["discovery_featured_albums"]?.albums?.firstOrNull()
        }

        if (lead == null && albumCandidate == null) return null

        val badgeText = when (activeLanguage) {
            HomeLanguageFilter.ALL -> "FEATURED DISCOVERY • USMAN MUSIC"
            else -> "FEATURED ${activeLanguage.chipLabel} SPOTLIGHT"
        }

        return HomeHeroFeature(
            id = "hero_${activeLanguage.name.lowercase()}_${lead?.id ?: albumCandidate?.id}",
            badge = badgeText,
            title = lead?.title ?: albumCandidate?.title.orEmpty(),
            subtitle = if (lead != null) {
                "${lead.artist} • ${lead.album}"
            } else {
                "${albumCandidate?.artist.orEmpty()} • Official Album"
            },
            artworkUri = lead?.albumArtUri ?: albumCandidate?.artworkUri,
            language = activeLanguage,
            primarySong = lead,
            queueSongs = preferredSongs,
            album = albumCandidate
        )
    }

    fun buildNotificationsFeed(
        sectionsById: Map<String, HomeDiscoverySection>,
        activeDownloadCount: Int,
        completedDownloadCount: Int
    ): List<HomeNotificationItem> {
        val items = mutableListOf<HomeNotificationItem>()
        if (activeDownloadCount > 0) {
            items.add(
                HomeNotificationItem(
                    id = "notif_active_dl",
                    title = "Downloads in progress",
                    subtitle = "$activeDownloadCount track(s) currently downloading for offline listening",
                    badge = "DOWNLOADING"
                )
            )
        } else if (completedDownloadCount > 0) {
            items.add(
                HomeNotificationItem(
                    id = "notif_done_dl",
                    title = "Offline Library Ready",
                    subtitle = "$completedDownloadCount downloaded full-length tracks available offline",
                    badge = "OFFLINE"
                )
            )
        }

        val releaseSources = listOf(
            "Latest Telugu" to sectionsById["telugu_new_releases"]?.songs.orEmpty(),
            "Latest Tamil" to sectionsById["tamil_new_releases"]?.songs.orEmpty(),
            "Latest Hindi" to sectionsById["hindi_new_bollywood_releases"]?.songs.orEmpty(),
            "Latest English" to sectionsById["english_new_releases"]?.songs.orEmpty(),
            "New Release" to sectionsById["discovery_new_releases"]?.songs.orEmpty()
        )
        for ((badge, songs) in releaseSources) {
            songs.take(2).forEach { song ->
                items.add(
                    HomeNotificationItem(
                        id = "notif_song_${song.id}_${song.filePath}",
                        title = song.title,
                        subtitle = "${song.artist} • ${song.album}",
                        badge = badge.uppercase(),
                        song = song
                    )
                )
            }
        }
        return items.distinctBy { it.id }.take(12)
    }

    private fun buildPersonalizedRecommendationQuery(
        targetLanguage: HomeLanguageFilter,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        playStats: List<PersistedPlayStat>,
        fallbackQuery: String
    ): String {
        val signals = (favoriteSongs + recentlyPlayed)
            .filter { HomeLanguagePolicy.isAllowedJioSaavnSong(it) }
            .filter { HomeLanguagePolicy.matchesTargetLanguage(it.language, targetLanguage, it.title) }

        val topArtist = signals
            .flatMap { it.artist.split(",") }
            .map { it.trim() }
            .filter { it.length >= 3 && !it.equals("Unknown Artist", true) && !it.equals("Various Artists", true) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val topLang = if (targetLanguage != HomeLanguageFilter.ALL) {
            targetLanguage.displayName
        } else {
            signals.mapNotNull { HomeLanguagePolicy.normalizeLanguage(it.language) }
                .filter { it in HomeLanguagePolicy.ALLOWED_LANGUAGES }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: playStats.firstOrNull()?.song?.language?.let { HomeLanguagePolicy.normalizeLanguage(it) }
        }

        return when {
            !topArtist.isNullOrBlank() && !topLang.isNullOrBlank() -> "$topArtist $topLang hits"
            !topArtist.isNullOrBlank() -> "$topArtist top songs"
            !topLang.isNullOrBlank() -> "$topLang top melody hits"
            else -> fallbackQuery
        }
    }

    private fun buildDiscoverWeeklyQuery(
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        fallbackQuery: String
    ): String {
        val artists = (recentlyPlayed + favoriteSongs)
            .flatMap { it.artist.split(",") }
            .map { it.trim() }
            .filter { it.length >= 3 && !it.equals("Unknown Artist", true) }
            .distinct()
        val secondaryArtist = artists.drop(1).firstOrNull() ?: artists.firstOrNull()
        return if (!secondaryArtist.isNullOrBlank()) {
            "$secondaryArtist latest songs"
        } else {
            fallbackQuery
        }
    }

    private fun rotateQuery(definition: HomeSectionDefinition): String {
        val allQueries = listOf(definition.query) + definition.fallbackQueries
        if (allQueries.isEmpty()) return definition.query
        val sessionSeed = (System.currentTimeMillis() / (5 * 60 * 1000L)).toInt()
        val index = Math.abs((definition.id.hashCode() + sessionSeed)) % allQueries.size
        return allQueries[index]
    }

    private fun songDedupKey(song: Song): String {
        val fp = song.filePath.trim()
        return if (fp.isNotBlank() && fp != "0") {
            "id_$fp"
        } else {
            "${song.title.lowercase().trim()}|${song.artist.lowercase().trim()}"
        }
    }

    companion object {
        private const val TAG = "HomeDiscoveryRepo"
        private const val CACHE_TTL_MS = 20 * 60 * 1000L
    }
}
