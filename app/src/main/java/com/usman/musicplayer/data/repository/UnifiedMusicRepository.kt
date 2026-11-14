package com.usman.musicplayer.data.repository

import android.content.Context
import android.util.Log
import com.usman.musicplayer.data.repository.providers.JioSaavnMusicProvider
import com.usman.musicplayer.extension.ExtensionManager
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.sdk.AlbumDetails
import com.usman.musicplayer.sdk.AlbumSearchResult
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderRegistry
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

data class SearchSongsOutcome(
    val songs: List<Song>,
    val fromCache: Boolean = false,
    val hadNetworkError: Boolean = false
)

class UnifiedMusicRepository(
    context: Context?,
    private val providersOverride: List<MusicProvider>? = null,
    private val internalVerifierOverride: MusicProvider? = null
) {

    constructor(context: Context) : this(context, null, null)

    private data class CachedResult(val songs: List<Song>, val timestamp: Long)
    private data class CachedAlbums(val albums: List<AlbumSearchResult>, val timestamp: Long)
    private data class CachedArtists(val artists: List<OnlineArtistResult>, val timestamp: Long)
    private data class CachedAlbumDetails(val details: AlbumDetails, val timestamp: Long)

    private val appContext: Context? = context?.applicationContext
    @Volatile
    private var providersReady: Boolean = (providersOverride != null || appContext == null)
    private val initLock = Any()

    private val searchCache = ConcurrentHashMap<String, CachedResult>()
    private val albumSearchCache = ConcurrentHashMap<String, CachedAlbums>()
    private val soundtrackAlbumCache = ConcurrentHashMap<String, CachedAlbums>()
    private val artistSearchCache = ConcurrentHashMap<String, CachedArtists>()
    private val albumDetailsCache = ConcurrentHashMap<String, CachedAlbumDetails>()
    private val inFlightSongMutexes = ConcurrentHashMap<String, Mutex>()
    private val inFlightAlbumMutexes = ConcurrentHashMap<String, Mutex>()
    private val inFlightSoundtrackMutexes = ConcurrentHashMap<String, Mutex>()
    private val inFlightArtistMutexes = ConcurrentHashMap<String, Mutex>()

    private val CACHE_TTL_MS = 180_000L // 3 minutes TTL for fast repeat searches
    private val ALBUM_DETAILS_TTL_MS = 300_000L // 5 minutes TTL for album tracklists

    fun warmUp() {
        ensureProvidersInitialized()
    }

    private fun ensureProvidersInitialized() {
        if (providersReady) return
        val ctx = appContext ?: run {
            providersReady = true
            return
        }
        synchronized(initLock) {
            if (!providersReady) {
                ExtensionManager.init(ctx)
                ProviderRegistry.init(ctx)
                providersReady = true
            }
        }
    }

    private fun isItunesProvider(provider: MusicProvider): Boolean {
        return provider.metadata.id == "itunes_catalog_provider" ||
            provider.metadata.name.equals("iTunes", ignoreCase = true)
    }

    private fun userFacingOnlineProviders(): List<MusicProvider> {
        ensureProvidersInitialized()
        val base = (providersOverride ?: ProviderRegistry.activeProviders)
            .filter { it.metadata.id != "local_music_provider" && !isItunesProvider(it) }
        return if (providersOverride == null) {
            base.filter {
                it.metadata.id == "jiosaavn_native_provider" ||
                    it.metadata.name.equals("JioSaavn", ignoreCase = true)
            }
        } else {
            base
        }
    }

    private fun internalSoundtrackVerifier(): MusicProvider? {
        ensureProvidersInitialized()
        return internalVerifierOverride
            ?: providersOverride?.firstOrNull { isItunesProvider(it) }
            ?: ProviderRegistry.getInternalSoundtrackVerifier()
    }

    fun isAllowedUserFacingSong(song: Song): Boolean {
        if (!song.isOnline) return true
        val provider = song.sourceProvider.orEmpty().trim()
        val isItunes = provider.equals("iTunes", ignoreCase = true) ||
            song.folderName.equals("iTunes Discovery", ignoreCase = true) ||
            song.folderName.equals("iTunes", ignoreCase = true) ||
            song.contentUriString?.contains("itunes.apple.com", ignoreCase = true) == true ||
            song.contentUriString?.contains("mzstatic.com", ignoreCase = true) == true
        if (isItunes) return false
        if (song.playbackType != PlaybackType.FULL) return false
        if (!song.isStreamable && song.contentUriString.isNullOrBlank()) return false
        if (providersOverride == null && !provider.equals("JioSaavn", ignoreCase = true)) {
            return false
        }
        return true
    }

    private fun isAllowedUserFacingAlbum(album: AlbumSearchResult): Boolean {
        val provider = album.sourceProvider.trim()
        if (provider.equals("iTunes", ignoreCase = true)) return false
        if (providersOverride == null && !provider.equals("JioSaavn", ignoreCase = true)) return false
        return true
    }

    fun peekCachedSongs(query: String, limit: Int = 35, offset: Int = 0): List<Song>? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val cacheKey = "${trimmed.lowercase()}_${limit}_$offset"
        val cached = searchCache[cacheKey] ?: return null
        if ((System.currentTimeMillis() - cached.timestamp) >= CACHE_TTL_MS || cached.songs.isEmpty()) {
            return null
        }
        return cached.songs
    }

    fun peekCachedAlbums(query: String, limit: Int = 20): List<AlbumSearchResult>? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val prefix = "albums_${trimmed.lowercase()}_${limit}_"
        val match = albumSearchCache.entries.firstOrNull { (k, v) ->
            k.startsWith(prefix) && (now - v.timestamp) < CACHE_TTL_MS && v.albums.isNotEmpty()
        }?.value
        return match?.albums
    }

    fun peekCachedSoundtrackAlbums(query: String, limit: Int = 20): List<AlbumSearchResult>? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val cacheKey = "soundtrack_${trimmed.lowercase()}_$limit"
        val cached = soundtrackAlbumCache[cacheKey] ?: return null
        if ((System.currentTimeMillis() - cached.timestamp) >= CACHE_TTL_MS) {
            return null
        }
        return cached.albums
    }

    fun peekCachedArtists(query: String, limit: Int = 15): List<OnlineArtistResult>? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val cacheKey = "artists_${trimmed.lowercase()}_$limit"
        val cached = artistSearchCache[cacheKey] ?: return null
        if ((System.currentTimeMillis() - cached.timestamp) >= CACHE_TTL_MS) {
            return null
        }
        return cached.artists
    }

    suspend fun searchAll(query: String, limit: Int = 35, offset: Int = 0): List<Song> {
        return searchAllWithStatus(query, limit, offset).songs
    }

    suspend fun searchAllWithStatus(
        query: String,
        limit: Int = 35,
        offset: Int = 0
    ): SearchSongsOutcome {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return SearchSongsOutcome(emptyList(), fromCache = true)

        val cacheKey = "${trimmed.lowercase()}_${limit}_$offset"
        peekCachedSongs(trimmed, limit, offset)?.let { hit ->
            Log.d("SearchTiming", "Cache hit for query '$trimmed' (offset $offset)")
            return SearchSongsOutcome(hit, fromCache = true)
        }

        val mutex = inFlightSongMutexes.computeIfAbsent(cacheKey) { Mutex() }
        return mutex.withLock {
            peekCachedSongs(trimmed, limit, offset)?.let { hit ->
                return@withLock SearchSongsOutcome(hit, fromCache = true)
            }

            coroutineScope {
                val active = userFacingOnlineProviders()
                var networkFailureDetected = false
                val deferredList = active.map { provider ->
                    async {
                        val startTime = System.currentTimeMillis()
                        var page1TimedOut = false
                        val page1Result = runCatching {
                            val res = withTimeoutOrNull(4500L) {
                                provider.searchPaginated(trimmed, limit, page = 1)
                            }
                            if (res == null) {
                                page1TimedOut = true
                                null
                            } else {
                                res
                            }
                        }
                        val page1Exception = page1Result.exceptionOrNull() ?: page1Result.getOrNull()?.exceptionOrNull()
                        if (page1TimedOut || page1Exception is IOException) {
                            networkFailureDetected = true
                        }
                        val page1 = page1Result.getOrNull()?.getOrNull() ?: emptyList()

                        // Only hit page 2 sequentially when page 1 returned an abnormally small result set
                        val page2Threshold = minOf(8, limit)
                        val page2 = if (page1.isNotEmpty() && page1.size < page2Threshold) {
                            runCatching {
                                withTimeoutOrNull(3000L) {
                                    provider.searchPaginated(trimmed, limit, page = 2)
                                }?.getOrNull() ?: emptyList()
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                        val elapsed = System.currentTimeMillis() - startTime
                        val merged = page1 + page2
                        Log.d("SearchTiming", "Provider ${provider.metadata.name} took ${elapsed}ms for query '$trimmed' (found ${merged.size} songs)")
                        merged
                    }
                }

                val allResults = deferredList.awaitAll().flatten().filter(::isAllowedUserFacingSong)

                val queryTokens = tokenize(trimmed)
                val compactQuery = queryTokens.joinToString("")

                val dedupedByTrack = deduplicateSongsPreferringFullStreams(allResults)

                val sortedResults = dedupedByTrack
                    .map { song ->
                        val score = calculateRelevanceScore(song, queryTokens, compactQuery)
                        song to score
                    }
                    .filter { (song, score) ->
                        filterRelevantSong(song, score, queryTokens)
                    }
                    .sortedWith(
                        compareByDescending<Pair<Song, Int>> { it.second }
                            .thenByDescending { it.first.isFullTrackVerified }
                            .thenByDescending { !it.first.albumArtUri.isNullOrBlank() }
                    )
                    .map { it.first }
                    .let { list ->
                        if (offset > 0 && offset < list.size) list.drop(offset) else list
                    }
                    .take(limit)

                if (sortedResults.isNotEmpty()) {
                    if (searchCache.size >= MAX_SEARCH_CACHE_ENTRIES) {
                        trimOldestEntries(searchCache)
                    }
                    searchCache[cacheKey] = CachedResult(sortedResults, System.currentTimeMillis())
                } else {
                    searchCache.remove(cacheKey)
                }

                SearchSongsOutcome(
                    songs = sortedResults,
                    fromCache = false,
                    hadNetworkError = networkFailureDetected && sortedResults.isEmpty()
                )
            }
        }
    }

    suspend fun searchAlbums(
        query: String,
        limit: Int = 20,
        seedSongs: List<Song> = emptyList()
    ): List<AlbumSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val filteredSeedSongs = seedSongs.filter(::isAllowedUserFacingSong)
        val cacheKey = "albums_${trimmed.lowercase()}_${limit}_${filteredSeedSongs.size}"
        val cached = albumSearchCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS && cached.albums.isNotEmpty()) {
            return cached.albums
        }

        val mutex = inFlightAlbumMutexes.computeIfAbsent(cacheKey) { Mutex() }
        return mutex.withLock {
            val recheck = albumSearchCache[cacheKey]
            if (recheck != null && (System.currentTimeMillis() - recheck.timestamp) < CACHE_TTL_MS && recheck.albums.isNotEmpty()) {
                return@withLock recheck.albums
            }
            coroutineScope {
                val coreQuery = stripMovieNoiseWords(trimmed)
                val onlineProviders = userFacingOnlineProviders()

                val baseVariants = linkedSetOf(trimmed).apply {
                    if (coreQuery.isNotBlank() && !coreQuery.equals(trimmed, ignoreCase = true)) {
                        add(coreQuery)
                    }
                }

                val primaryAlbums = baseVariants.flatMap { q ->
                    onlineProviders.map { provider ->
                        async {
                            runCatching {
                                withTimeoutOrNull(4500L) {
                                    provider.searchAlbumsPaginated(q, limit, page = 1)
                                }?.getOrNull() ?: emptyList()
                            }.getOrDefault(emptyList())
                        }
                    }
                }.awaitAll().flatten()
                    .filter(::isAllowedUserFacingAlbum)
                    .toMutableList()

                // Only issue fallback soundtrack / page-2 calls when initial album batch is sparse
                val coreTokens = tokenize(coreQuery.ifBlank { trimmed })
                if (primaryAlbums.size < 3 && coreTokens.size in 1..3) {
                    val secondaryDeferred = buildList {
                        onlineProviders.forEach { provider ->
                            add(async {
                                runCatching {
                                    withTimeoutOrNull(3500L) {
                                        provider.searchAlbumsPaginated(trimmed, limit, page = 2)
                                    }?.getOrNull() ?: emptyList()
                                }.getOrDefault(emptyList())
                            })
                            if (!trimmed.contains("soundtrack", ignoreCase = true)) {
                                add(async {
                                    runCatching {
                                        withTimeoutOrNull(3500L) {
                                            provider.searchAlbumsPaginated("$coreQuery soundtrack", limit, page = 1)
                                        }?.getOrNull() ?: emptyList()
                                    }.getOrDefault(emptyList())
                                })
                            }
                        }
                    }
                    primaryAlbums.addAll(
                        secondaryDeferred.awaitAll().flatten().filter(::isAllowedUserFacingAlbum)
                    )
                }

        val coreQueryForScore = stripMovieNoiseWords(trimmed)
        val queryTokens = tokenize(coreQueryForScore.ifBlank { trimmed })
        val compactQuery = queryTokens.joinToString("")

        // Merge fallback album entries from verified JioSaavn songs with verified albumId
        val ranked = mergeAlbumsWithSeedSongs(primaryAlbums, filteredSeedSongs, queryTokens, compactQuery, limit)

        if (ranked.isNotEmpty()) {
            if (albumSearchCache.size >= MAX_SEARCH_CACHE_ENTRIES) {
                trimOldestAlbumEntries()
            }
            albumSearchCache[cacheKey] = CachedAlbums(ranked, System.currentTimeMillis())
        }
        ranked
            }
        }
    }

    private fun mergeAlbumsWithSeedSongs(
        baseAlbums: List<AlbumSearchResult>,
        seedSongs: List<Song>,
        queryTokens: List<String>,
        compactQuery: String,
        limit: Int
    ): List<AlbumSearchResult> {
        val knownAlbumKeys = baseAlbums.map { "${it.sourceProvider.lowercase()}|${it.id}" }.toMutableSet()
        val inferredFromSongs = seedSongs
            .filter {
                !it.albumId.isNullOrBlank() &&
                    it.album.isNotBlank() &&
                    !it.album.equals("Unknown Album", ignoreCase = true) &&
                    !it.album.equals("Single", ignoreCase = true) &&
                    !it.sourceProvider.equals("Internet Archive", ignoreCase = true)
            }
            .groupBy { "${it.sourceProvider.orEmpty().lowercase()}|${it.albumId}" }
            .values
            .mapNotNull { tracksInAlbum ->
                val first = tracksInAlbum.first()
                val provider = first.sourceProvider ?: "JioSaavn"
                val albumId = first.albumId ?: return@mapNotNull null
                val key = "${provider.lowercase()}|$albumId"
                if (knownAlbumKeys.contains(key)) return@mapNotNull null
                knownAlbumKeys.add(key)
                AlbumSearchResult(
                    id = albumId,
                    title = first.album,
                    movieName = extractMovieFromTitleOrAlbum(first.album)
                        ?: extractMovieFromTitleOrAlbum(first.title),
                    artist = first.artist,
                    year = first.releaseYear,
                    songCount = tracksInAlbum.size,
                    artworkUri = first.albumArtUri,
                    sourceProvider = provider,
                    externalUrl = first.albumUrl,
                    isCompleteAlbum = false
                )
            }
            .filter(::isAllowedUserFacingAlbum)

        return (baseAlbums + inferredFromSongs)
            .distinctBy { "${it.sourceProvider.lowercase()}|${it.id}" }
            .map { album -> album to calculateAlbumRelevanceScore(album, queryTokens, compactQuery) }
            .filter { (_, score) -> queryTokens.isEmpty() || score >= 45 }
            .sortedWith(
                compareByDescending<Pair<AlbumSearchResult, Int>> { it.second }
                    .thenByDescending { it.first.isCompleteAlbum }
                    .thenByDescending { it.first.songCount ?: 0 }
                    .thenByDescending { !it.first.artworkUri.isNullOrBlank() }
            )
            .map { it.first }
            .take(limit)
    }

    /**
     * Discovers JioSaavn albums and movie soundtrack collections.
     * Optionally uses hidden internal soundtrack verification solely to find missing
     * official song titles/ordering, then resolves and exposes ONLY playable full-length
     * JioSaavn tracks and JioSaavn collections in user-facing results.
     */
    suspend fun searchAlbumsAndSoundtracks(
        query: String,
        limit: Int = 20,
        seedSongs: List<Song> = emptyList()
    ): List<AlbumSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        peekCachedSoundtrackAlbums(trimmed, limit)?.let { return it }

        val cacheKey = "soundtrack_${trimmed.lowercase()}_$limit"
        val mutex = inFlightSoundtrackMutexes.computeIfAbsent(cacheKey) { Mutex() }
        return mutex.withLock {
            peekCachedSoundtrackAlbums(trimmed, limit)?.let { return@withLock it }

            coroutineScope {
                val cleanedSeedSongs = seedSongs.filter(::isAllowedUserFacingSong)
                val coreQuery = stripMovieNoiseWords(trimmed)
                val queryTokens = tokenize(coreQuery.ifBlank { trimmed })
                val compactQuery = queryTokens.joinToString("")

        // 1. Gather individual JioSaavn albums + hidden internal metadata verification hint in parallel
        val albumsDeferred = async { searchAlbums(trimmed, limit = limit, seedSongs = cleanedSeedSongs) }

        val movieSongsDeferred = async {
            if (coreQuery.isBlank()) emptyList()
            else {
                val onlineProviders = userFacingOnlineProviders()
                val queries = listOf("$coreQuery songs", "From $coreQuery").distinct()
                queries.flatMap { q ->
                    onlineProviders.map { p ->
                        async {
                            runCatching {
                                withTimeoutOrNull(4000L) {
                                    p.searchPaginated(q, 20, page = 1)
                                }?.getOrNull() ?: emptyList()
                            }.getOrDefault(emptyList())
                        }
                    }
                }.awaitAll().flatten().filter(::isAllowedUserFacingSong)
            }
        }

        val hiddenOstHintDeferred = async {
            if (coreQuery.isBlank() || queryTokens.isEmpty()) null
            else fetchHiddenOfficialSoundtrackReference(coreQuery, queryTokens, compactQuery)
        }

        val allSeedSongs = (cleanedSeedSongs + movieSongsDeferred.await())
            .filter(::isAllowedUserFacingSong)

        val albums = albumsDeferred.await()
        // Merge newly discovered soundtrack seed songs in-memory without re-running duplicate HTTP album queries
        val enrichedAlbums = if (allSeedSongs.size > cleanedSeedSongs.size) {
            mergeAlbumsWithSeedSongs(albums, allSeedSongs, queryTokens, compactQuery, limit)
        } else {
            albums
        }.filter(::isAllowedUserFacingAlbum)

        val matchingAlbums = enrichedAlbums.filter { album ->
            doesAlbumMatchMovie(album, queryTokens, compactQuery)
        }
        val matchingSongs = allSeedSongs.filter { song ->
            doesSongBelongToMovie(song, queryTokens, compactQuery)
        }
        val hiddenOstRef = hiddenOstHintDeferred.await()

        val shouldBuildSoundtrackCollection = (matchingAlbums.size >= 2) ||
            (matchingAlbums.size == 1 && (matchingAlbums.first().movieName != null || matchingSongs.size >= 2 || hiddenOstRef != null)) ||
            matchingSongs.size >= 2 ||
            (hiddenOstRef != null && matchingSongs.isNotEmpty())

        if (!shouldBuildSoundtrackCollection || queryTokens.isEmpty()) {
            if (enrichedAlbums.isNotEmpty()) {
                soundtrackAlbumCache[cacheKey] = CachedAlbums(enrichedAlbums, System.currentTimeMillis())
            }
            return@coroutineScope enrichedAlbums
        }

        val collectionDetails = buildMovieSoundtrackCollectionDetails(
            movieDisplayTitle = resolveDisplayMovieTitle(coreQuery, matchingAlbums, matchingSongs, hiddenOstRef),
            movieKey = compactQuery.ifBlank { coreQuery.lowercase() },
            constituentAlbums = matchingAlbums,
            movieSongs = matchingSongs,
            queryTokens = queryTokens,
            compactQuery = compactQuery,
            preFetchedHiddenRef = hiddenOstRef
        )

        if (collectionDetails == null || collectionDetails.tracks.isEmpty()) {
            if (enrichedAlbums.isNotEmpty()) {
                soundtrackAlbumCache[cacheKey] = CachedAlbums(enrichedAlbums, System.currentTimeMillis())
            }
            return@coroutineScope enrichedAlbums
        }

        val collectionKey = "${collectionDetails.sourceProvider.lowercase()}|${collectionDetails.id}"
        albumDetailsCache[collectionKey] = CachedAlbumDetails(collectionDetails, System.currentTimeMillis())

        val collectionSummary = AlbumSearchResult(
            id = collectionDetails.id,
            title = collectionDetails.title,
            movieName = collectionDetails.movieName,
            artist = collectionDetails.artist,
            year = collectionDetails.year,
            songCount = collectionDetails.discoveredCount,
            artworkUri = collectionDetails.artworkUri,
            sourceProvider = collectionDetails.sourceProvider,
            externalUrl = collectionDetails.externalUrl,
            language = collectionDetails.language,
            isCompleteAlbum = collectionDetails.isComplete,
            isSoundtrackCollection = true,
            constituentAlbums = matchingAlbums
        )

        val finalAlbums = (listOf(collectionSummary) + enrichedAlbums)
            .filter(::isAllowedUserFacingAlbum)
            .distinctBy { "${it.sourceProvider.lowercase()}|${it.id}" }
            .take(limit)
        if (finalAlbums.isNotEmpty()) {
            soundtrackAlbumCache[cacheKey] = CachedAlbums(finalAlbums, System.currentTimeMillis())
        }
        finalAlbums
            }
        }
    }

    suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> {
        if (album.sourceProvider.equals("iTunes", ignoreCase = true) &&
            !album.isSoundtrackCollection &&
            !album.id.startsWith("soundtrack_")
        ) {
            return Result.failure(IllegalArgumentException("This album is currently unavailable."))
        }

        val cacheKey = "${album.sourceProvider.lowercase()}|${album.id}"
        val cached = albumDetailsCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < ALBUM_DETAILS_TTL_MS) {
            return Result.success(cached.details)
        }

        if (album.isSoundtrackCollection || album.id.startsWith("soundtrack_")) {
            val movieTitle = album.movieName ?: album.title.substringBefore(" — ").trim()
            val queryTokens = tokenize(movieTitle)
            val compactQuery = queryTokens.joinToString("")
            val constituent = album.constituentAlbums
                .filter(::isAllowedUserFacingAlbum)
                .ifEmpty { searchAlbums(movieTitle, limit = 15) }
            val matchingSongs = searchAll(movieTitle, limit = 35).filter {
                doesSongBelongToMovie(it, queryTokens, compactQuery)
            }
            val details = buildMovieSoundtrackCollectionDetails(
                movieDisplayTitle = movieTitle,
                movieKey = album.id.removePrefix("soundtrack_").ifBlank { compactQuery },
                constituentAlbums = constituent,
                movieSongs = matchingSongs,
                queryTokens = queryTokens,
                compactQuery = compactQuery
            )
            if (details != null) {
                albumDetailsCache[cacheKey] = CachedAlbumDetails(details, System.currentTimeMillis())
            }
            return Result.success(details)
        }

        val matchingProvider = userFacingOnlineProviders().find {
            it.metadata.name.equals(album.sourceProvider, ignoreCase = true) ||
                it.metadata.id.equals(album.sourceProvider, ignoreCase = true)
        }

        val result = if (matchingProvider != null) {
            matchingProvider.getAlbumDetails(album)
        } else {
            ProviderRegistry.getAlbumDetails(album)
        }

        val details = result.getOrNull()
        if (details != null) {
            val deduped = LinkedHashMap<String, Song>()
            for (track in details.tracks.filter(::isAllowedUserFacingSong)) {
                val trackAlbumId = track.albumId
                if (!trackAlbumId.isNullOrBlank() && details.id.isNotBlank() && trackAlbumId != details.id) {
                    continue
                }
                val dedupKey = track.filePath.takeIf { it.isNotBlank() && it != "0" }
                    ?: "${track.title.lowercase().trim()}|${track.artist.lowercase().trim()}|${track.durationMs}"
                if (!deduped.containsKey(dedupKey)) {
                    deduped[dedupKey] = track
                }
            }
            val cleanedTracks = deduped.values.toList()
            val declaredOfficial = details.officialExpectedCount ?: details.songCount.takeIf { it > 0 }
            val cleaned = details.copy(
                sourceProvider = if (details.sourceProvider.equals("iTunes", true)) "JioSaavn" else details.sourceProvider,
                tracks = cleanedTracks,
                songCount = maxOf(details.songCount, cleanedTracks.size),
                discoveredCount = cleanedTracks.size,
                officialExpectedCount = declaredOfficial,
                contributingProviders = listOf(
                    if (details.sourceProvider.equals("iTunes", true)) "JioSaavn" else details.sourceProvider
                ),
                isComplete = cleanedTracks.isNotEmpty() && (declaredOfficial == null || cleanedTracks.size >= declaredOfficial)
            )
            albumDetailsCache[cacheKey] = CachedAlbumDetails(cleaned, System.currentTimeMillis())
            return Result.success(cleaned)
        }
        return result
    }

    private suspend fun fetchHiddenOfficialSoundtrackReference(
        coreQuery: String,
        queryTokens: List<String>,
        compactQuery: String
    ): AlbumDetails? {
        val verifier = internalSoundtrackVerifier() ?: return null
        return runCatching {
            val albums = withTimeoutOrNull(4500L) {
                verifier.searchAlbumsPaginated(coreQuery, 6, page = 1)
            }?.getOrNull().orEmpty()
                .filter { doesAlbumMatchMovie(it, queryTokens, compactQuery) }
                .sortedByDescending { (it.songCount ?: 0) }
            val best = albums.firstOrNull() ?: return@runCatching null
            withTimeoutOrNull(4500L) {
                verifier.getAlbumDetails(best)
            }?.getOrNull()
        }.getOrNull()
    }

    private suspend fun buildMovieSoundtrackCollectionDetails(
        movieDisplayTitle: String,
        movieKey: String,
        constituentAlbums: List<AlbumSearchResult>,
        movieSongs: List<Song>,
        queryTokens: List<String>,
        compactQuery: String,
        preFetchedHiddenRef: AlbumDetails? = null
    ): AlbumDetails? = coroutineScope {
        val onlineProviders = userFacingOnlineProviders()
        val allowedConstituents = constituentAlbums.filter(::isAllowedUserFacingAlbum)

        val hiddenRefDeferred = async {
            preFetchedHiddenRef ?: fetchHiddenOfficialSoundtrackReference(movieDisplayTitle, queryTokens, compactQuery)
        }

        // Fetch full album details for ALL matching JioSaavn albums
        val candidateAlbums = allowedConstituents
            .distinctBy { "${it.sourceProvider.lowercase()}|${it.id}" }
            .take(8)

        val fetchedDetailsList = candidateAlbums.map { album ->
            async {
                val provider = onlineProviders.find {
                    it.metadata.name.equals(album.sourceProvider, ignoreCase = true) ||
                        it.metadata.id.equals(album.sourceProvider, ignoreCase = true)
                }
                runCatching {
                    withTimeoutOrNull(5000L) {
                        provider?.getAlbumDetails(album)?.getOrNull()
                    }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()

        val verifiedAlbumTracks = fetchedDetailsList.flatMap { details ->
            val albumMatches = doesAlbumMatchMovie(
                AlbumSearchResult(
                    id = details.id,
                    title = details.title,
                    movieName = details.movieName,
                    artist = details.artist,
                    sourceProvider = details.sourceProvider
                ),
                queryTokens,
                compactQuery
            )
            val candidateTracks = if (albumMatches) {
                details.tracks.map { track ->
                    if (track.album.isBlank() || track.album == "Unknown Album") {
                        track.copy(album = details.title, albumId = track.albumId ?: details.id)
                    } else {
                        track
                    }
                }
            } else {
                details.tracks.filter { doesSongBelongToMovie(it, queryTokens, compactQuery) }
            }
            candidateTracks.filter(::isAllowedUserFacingSong)
        }

        val cleanedMovieSongs = movieSongs.filter(::isAllowedUserFacingSong)
        var consolidatedTracks = deduplicateSongsPreferringFullStreams(verifiedAlbumTracks + cleanedMovieSongs)
            .filter(::isAllowedUserFacingSong)

        // Use hidden internal OST tracklist ONLY to discover missing official song titles on JioSaavn
        // and to assign official soundtrack track ordering (never adding non-JioSaavn or preview items!)
        val hiddenOstDetails = hiddenRefDeferred.await()
        val officialOrderByNormalizedTitle = LinkedHashMap<String, Int>()
        hiddenOstDetails?.tracks?.forEachIndexed { idx, refTrack ->
            val key = normalizeTitleForDedup(refTrack.title)
            if (key.isNotBlank() && !officialOrderByNormalizedTitle.containsKey(key)) {
                officialOrderByNormalizedTitle[key] = refTrack.trackNumber ?: (idx + 1)
            }
        }

        if (hiddenOstDetails != null && hiddenOstDetails.tracks.isNotEmpty() && onlineProviders.isNotEmpty()) {
            val presentKeys = consolidatedTracks.map { normalizeTitleForDedup(it.title) }.toHashSet()
            val missingRefTracks = hiddenOstDetails.tracks.filter { refTrack ->
                val norm = normalizeTitleForDedup(refTrack.title)
                norm.isNotBlank() && !presentKeys.contains(norm)
            }

            if (missingRefTracks.isNotEmpty()) {
                val jioResolvedMatches = missingRefTracks.take(8).flatMap { missingRef ->
                    val cleanTitle = cleanTrackTitleForComparison(missingRef.title)
                    val targetNorm = normalizeTitleForDedup(missingRef.title)
                    onlineProviders.map { provider ->
                        async {
                            val primaryHits = runCatching {
                                withTimeoutOrNull(4000L) {
                                    provider.search("$cleanTitle $movieDisplayTitle", limit = 6)
                                }?.getOrNull() ?: emptyList()
                            }.getOrDefault(emptyList())

                            val fallbackHits = if (primaryHits.none { normalizeTitleForDedup(it.title) == targetNorm }) {
                                runCatching {
                                    withTimeoutOrNull(3500L) {
                                        provider.search(cleanTitle, limit = 6)
                                    }?.getOrNull() ?: emptyList()
                                }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }

                            (primaryHits + fallbackHits)
                                .filter(::isAllowedUserFacingSong)
                                .filter { candidate ->
                                    normalizeTitleForDedup(candidate.title) == targetNorm &&
                                        (doesSongBelongToMovie(candidate, queryTokens, compactQuery) ||
                                            artistsOverlap(candidate.artist, missingRef.artist))
                                }
                        }
                    }
                }.awaitAll().flatten()

                if (jioResolvedMatches.isNotEmpty()) {
                    consolidatedTracks = deduplicateSongsPreferringFullStreams(consolidatedTracks + jioResolvedMatches)
                        .filter(::isAllowedUserFacingSong)
                }
            }
        }

        if (consolidatedTracks.isEmpty()) return@coroutineScope null

        // Preserve verified official soundtrack order where available
        val orderedTracks = consolidatedTracks.map { song ->
            val ostNumber = officialOrderByNormalizedTitle[normalizeTitleForDedup(song.title)]
            if (ostNumber != null && (song.trackNumber == null || song.trackNumber <= 0)) {
                song.copy(trackNumber = ostNumber)
            } else {
                song
            }
        }

        val sortedTracks = orderedTracks.sortedWith(
            compareBy<Song> { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )

        val officialMultiTrackCounts = (
            fetchedDetailsList.mapNotNull { it.officialExpectedCount ?: it.songCount.takeIf { c -> c > 1 } } +
                allowedConstituents.mapNotNull { it.songCount?.takeIf { c -> c > 1 } } +
                listOfNotNull(hiddenOstDetails?.officialExpectedCount ?: hiddenOstDetails?.songCount?.takeIf { c -> c > 1 })
            )

        val officialExpectedCount = officialMultiTrackCounts.maxOrNull()
        val hasSingleCompleteJioAlbum = officialExpectedCount == null &&
            fetchedDetailsList.any { it.isComplete && it.tracks.size >= 2 && it.tracks.size >= sortedTracks.size }
        val isComplete = sortedTracks.isNotEmpty() && (
            (officialExpectedCount != null && sortedTracks.size >= officialExpectedCount) ||
                hasSingleCompleteJioAlbum
            )

        val contributingAlbumCount = maxOf(
            allowedConstituents.size,
            sortedTracks.mapNotNull { it.albumId ?: it.album.takeIf(String::isNotBlank) }.distinct().size
        ).coerceAtLeast(1)

        val bestArtwork = fetchedDetailsList
            .sortedByDescending { (it.officialExpectedCount ?: it.tracks.size) }
            .firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
            ?: allowedConstituents.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
            ?: sortedTracks.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri

        val combinedArtists = (fetchedDetailsList.map { it.artist } +
            allowedConstituents.map { it.artist } +
            sortedTracks.map { it.artist })
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", true) && !it.equals("Various Artists", true) }
            .distinct()
            .take(3)
            .joinToString(", ")
            .ifBlank { "Various Artists" }

        val releaseYear = fetchedDetailsList.firstOrNull { !it.year.isNullOrBlank() }?.year
            ?: allowedConstituents.firstOrNull { !it.year.isNullOrBlank() }?.year
            ?: sortedTracks.firstOrNull { !it.releaseYear.isNullOrBlank() }?.releaseYear
            ?: hiddenOstDetails?.year

        val titleSuffix = if (isComplete) "Complete Movie Soundtrack" else "Movie Soundtrack"

        AlbumDetails(
            id = "soundtrack_$movieKey",
            title = "$movieDisplayTitle — $titleSuffix",
            movieName = movieDisplayTitle,
            artist = combinedArtists,
            year = releaseYear,
            songCount = maxOf(sortedTracks.size, officialExpectedCount ?: sortedTracks.size),
            artworkUri = bestArtwork,
            sourceProvider = "JioSaavn",
            externalUrl = allowedConstituents.firstOrNull()?.externalUrl,
            language = allowedConstituents.firstOrNull { !it.language.isNullOrBlank() }?.language,
            isComplete = isComplete,
            tracks = sortedTracks,
            discoveredCount = sortedTracks.size,
            officialExpectedCount = officialExpectedCount,
            contributingAlbumsCount = contributingAlbumCount,
            contributingProviders = listOf("JioSaavn"),
            isSoundtrackCollection = true
        )
    }

    private fun artistsOverlap(a: String, b: String): Boolean {
        val tokensA = tokenize(a).toSet()
        val tokensB = tokenize(b).toSet()
        return tokensA.isNotEmpty() && tokensB.isNotEmpty() && tokensA.any { it in tokensB }
    }

    suspend fun searchArtists(
        query: String,
        limit: Int = 15,
        seedSongs: List<Song> = emptyList(),
        fallbackToSongSearch: Boolean = true
    ): List<OnlineArtistResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        peekCachedArtists(trimmed, limit)?.let { return it }

        val cacheKey = "artists_${trimmed.lowercase()}_$limit"
        val mutex = inFlightArtistMutexes.computeIfAbsent(cacheKey) { Mutex() }
        return mutex.withLock {
            peekCachedArtists(trimmed, limit)?.let { return@withLock it }

            val jioSaavn = userFacingOnlineProviders().filterIsInstance<JioSaavnMusicProvider>().firstOrNull()
            val directArtists = if (jioSaavn != null) {
                runCatching {
                    withTimeoutOrNull(4000L) { jioSaavn.searchArtists(trimmed, limit) }?.getOrNull() ?: emptyList()
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val resolved = if (directArtists.isNotEmpty()) {
                directArtists.take(limit)
            } else if (seedSongs.isNotEmpty() || fallbackToSongSearch) {
                val sourceSongs = seedSongs.ifEmpty {
                    peekCachedSongs(trimmed, limit = 35) ?: searchAll(trimmed, limit = 25)
                }
                deriveArtistsFromSongs(trimmed, sourceSongs, limit)
            } else {
                emptyList()
            }

            artistSearchCache[cacheKey] = CachedArtists(resolved, System.currentTimeMillis())
            resolved
        }
    }

    fun deriveArtistsFromSongs(
        query: String,
        songs: List<Song>,
        limit: Int = 15
    ): List<OnlineArtistResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank() || songs.isEmpty()) return emptyList()
        val tokens = tokenize(trimmed)
        val derived = songs
            .filter { it.sourceProvider.equals("JioSaavn", ignoreCase = true) || providersOverride != null }
            .flatMap { song ->
                song.artist.split(",").map { name -> name.trim() to song.albumArtUri }
            }
            .filter { (name, _) ->
                name.isNotBlank() &&
                    !name.equals("Unknown Artist", ignoreCase = true) &&
                    tokens.any { token -> name.lowercase().contains(token) }
            }
            .distinctBy { it.first.lowercase() }
            .map { (name, art) ->
                OnlineArtistResult(
                    id = name.lowercase(),
                    name = name,
                    artworkUri = art,
                    sourceProvider = "JioSaavn"
                )
            }
            .take(limit)
        if (derived.isNotEmpty()) {
            artistSearchCache["artists_${trimmed.lowercase()}_$limit"] = CachedArtists(derived, System.currentTimeMillis())
        }
        return derived
    }

    suspend fun getStreamUrl(song: Song): Result<String> {
        ensureProvidersInitialized()
        return ProviderRegistry.getStreamUrl(song)
    }

    private fun trimOldestEntries(map: ConcurrentHashMap<String, CachedResult>) {
        val toRemove = map.entries.sortedBy { it.value.timestamp }.take(15).map { it.key }
        toRemove.forEach { map.remove(it) }
    }

    private fun trimOldestAlbumEntries() {
        val toRemove = albumSearchCache.entries.sortedBy { it.value.timestamp }.take(15).map { it.key }
        toRemove.forEach { albumSearchCache.remove(it) }
    }

    private fun deduplicateSongsPreferringFullStreams(songs: List<Song>): List<Song> {
        // First deduplicate exact same provider track IDs
        val uniqueProviderTracks = songs.distinctBy {
            val pid = it.filePath.takeIf { fp -> fp.isNotBlank() && fp != "0" } ?: it.id.toString()
            "${it.sourceProvider.orEmpty().lowercase()}|$pid"
        }

        val byNormalizedTitle = LinkedHashMap<String, Song>()
        for (song in uniqueProviderTracks) {
            val normTitle = normalizeTitleForDedup(song.title)
            val normDurationBucket = (song.durationMs / 15_000L).coerceAtLeast(0L)
            // Group cross-provider matches cleanly; preview clips (30s) merge with full track if title matches!
            val key = if (normTitle.isNotBlank()) {
                normTitle
            } else {
                "${song.sourceProvider.orEmpty().lowercase()}|${song.id}|$normDurationBucket"
            }

            val existing = byNormalizedTitle[key]
            if (existing == null) {
                byNormalizedTitle[key] = song
            } else {
                byNormalizedTitle[key] = pickHigherQualityTrack(existing, song)
            }
        }
        return byNormalizedTitle.values.toList()
    }

    private fun pickHigherQualityTrack(a: Song, b: Song): Song {
        fun rank(s: Song): Int {
            var r = 0
            if (s.playbackType == PlaybackType.FULL) r += 100
            if (s.playbackType == PlaybackType.PREVIEW) r -= 500
            if (!s.contentUriString.isNullOrBlank()) r += 25
            if (s.isStreamable) r += 15
            if (s.isFullTrackVerified) r += 15
            if (s.sourceProvider.equals("JioSaavn", ignoreCase = true)) r += 10
            if (!s.albumArtUri.isNullOrBlank()) r += 5
            if (s.durationMs > 45_000L) r += 5
            return r
        }
        val winner = if (rank(b) > rank(a)) b else a
        val loser = if (winner === a) b else a
        return winner.copy(
            albumArtUri = winner.albumArtUri ?: loser.albumArtUri,
            trackNumber = winner.trackNumber ?: loser.trackNumber,
            releaseYear = winner.releaseYear ?: loser.releaseYear
        )
    }

    private fun doesAlbumMatchMovie(
        album: AlbumSearchResult,
        queryTokens: List<String>,
        compactQuery: String
    ): Boolean {
        if (queryTokens.isEmpty()) return false
        val fullQuery = queryTokens.joinToString(" ")
        val title = album.title.lowercase()
        val movie = album.movieName?.lowercase().orEmpty()
        val compactTitle = title.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val compactMovie = movie.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val titleWords = tokenize(title)
        val movieWords = tokenize(movie)

        if (movie == fullQuery || title == fullQuery) return true
        if (movie.isNotBlank() && (movie.contains(fullQuery) || (compactQuery.length >= 4 && compactMovie.contains(compactQuery)))) return true
        if (title.startsWith(fullQuery) || title.contains(fullQuery)) return true
        if (compactQuery.length >= 4 && compactTitle.contains(compactQuery)) return true

        val allWords = titleWords + movieWords
        return queryTokens.all { q ->
            allWords.any { w -> w == q || (q.length >= 5 && levenshteinDistance(w, q) <= 2) }
        }
    }

    private fun doesSongBelongToMovie(
        song: Song,
        queryTokens: List<String>,
        compactQuery: String
    ): Boolean {
        if (queryTokens.isEmpty()) return false
        if (song.sourceProvider.equals("Internet Archive", ignoreCase = true) && !isQueryArchiveIntended(queryTokens)) {
            val combined = "${song.title} ${song.artist}".lowercase()
            if (ARCHIVE_NOISE_KEYWORDS.any { combined.contains(it) }) return false
        }

        val fullQuery = queryTokens.joinToString(" ")
        val albumLower = song.album.lowercase()
        val titleLower = song.title.lowercase()
        val compactAlbum = albumLower.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val compactTitle = titleLower.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val albumWords = tokenize(albumLower)
        val titleWords = tokenize(titleLower)

        // Album name directly matches the movie
        if (albumLower == fullQuery || albumLower.startsWith(fullQuery) || albumLower.contains(fullQuery)) return true
        if (compactQuery.length >= 4 && compactAlbum.contains(compactQuery)) return true
        if (queryTokens.all { q -> albumWords.any { w -> w == q || (q.length >= 5 && levenshteinDistance(w, q) <= 1) } }) return true

        // Song title explicitly says "(From '<Movie>')" or "- From <Movie>" or starts with / contains full movie query
        val extractedMovieFromTitle = extractMovieFromTitleOrAlbum(song.title)?.lowercase().orEmpty()
        if (extractedMovieFromTitle.isNotBlank() &&
            (extractedMovieFromTitle.contains(fullQuery) ||
                (compactQuery.length >= 4 && extractedMovieFromTitle.replace(Regex("""[^\p{L}\p{Nd}]"""), "").contains(compactQuery)))
        ) {
            return true
        }

        if (titleLower.contains(fullQuery) || (compactQuery.length >= 5 && compactTitle.contains(compactQuery))) {
            return true
        }

        return queryTokens.size >= 2 && queryTokens.all { q -> titleWords.any { it == q } }
    }

    private fun resolveDisplayMovieTitle(
        coreQuery: String,
        matchingAlbums: List<AlbumSearchResult>,
        matchingSongs: List<Song>,
        hiddenOstRef: AlbumDetails? = null
    ): String {
        val candidate = matchingAlbums.firstOrNull { !it.movieName.isNullOrBlank() }?.movieName
            ?: matchingAlbums.firstOrNull { !it.isSoundtrackCollection }?.title
                ?.let { extractMovieFromTitleOrAlbum(it) ?: it }
            ?: hiddenOstRef?.movieName?.takeIf { it.isNotBlank() }
            ?: hiddenOstRef?.title?.let { extractMovieFromTitleOrAlbum(it) }
            ?: matchingSongs.firstNotNullOfOrNull { extractMovieFromTitleOrAlbum(it.album) ?: extractMovieFromTitleOrAlbum(it.title) }
        if (!candidate.isNullOrBlank()) return candidate.trim()
        return coreQuery.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun extractMovieFromTitleOrAlbum(text: String): String? {
        val fromParenMatch = Regex("""\((?:From|from)\s+["']?([^"')]+)["']?\)""").find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!fromParenMatch.isNullOrBlank()) return fromParenMatch

        val fromDashMatch = Regex("""[-–]\s*(?:From|from)\s+["']?([^"'(]+)["']?\s*$""").find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!fromDashMatch.isNullOrBlank()) return fromDashMatch

        val soundtrackRegex = Regex(
            """\s*[-–]?\s*\(?(?:Original Motion Picture Soundtrack|Motion Picture Soundtrack|Original Soundtrack|Soundtrack|OST)\)?\s*$""",
            RegexOption.IGNORE_CASE
        )
        if (soundtrackRegex.containsMatchIn(text)) {
            return text.replace(soundtrackRegex, "").trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun cleanTrackTitleForComparison(title: String): String {
        return title
            .replace(Regex("""\((?:From|from)[^)]+\)"""), "")
            .replace(Regex("""[-–]\s*(?:From|from)\s+.*$"""), "")
            .replace(Regex("""\[[^\]]+\]"""), "")
            .replace(Regex("""\((?:Original|Official|Lyrical|Full Song|Audio|Video)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { title.trim() }
    }

    private fun normalizeTitleForDedup(title: String): String {
        return cleanTrackTitleForComparison(title)
            .lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}]"""), "")
    }

    private fun isQueryArchiveIntended(queryTokens: List<String>): Boolean {
        val archiveIntentWords = setOf("radio", "ceylon", "broadcast", "archive", "speech", "interview", "podcast", "vividh")
        return queryTokens.any { it in archiveIntentWords }
    }

    private fun filterRelevantSong(song: Song, score: Int, queryTokens: List<String>): Boolean {
        if (queryTokens.isEmpty()) return true
        if (!song.sourceProvider.equals("Internet Archive", ignoreCase = true)) {
            return true
        }

        // Exclude unrelated historical broadcasts, talk shows, or generic archival docs unless explicitly queried
        val hasArchiveIntent = isQueryArchiveIntended(queryTokens)

        val titleLower = song.title.lowercase()
        val artistLower = song.artist.lowercase()
        val combinedText = "$titleLower $artistLower"

        if (!hasArchiveIntent && ARCHIVE_NOISE_KEYWORDS.any { combinedText.contains(it) }) {
            return false
        }

        val titleWords = tokenize(song.title).toSet()
        val artistWords = tokenize(song.artist).toSet()
        val allWords = titleWords + artistWords

        val matchedTokens = queryTokens.count { qToken ->
            allWords.any { word ->
                word == qToken || (qToken.length >= 4 && word.startsWith(qToken))
            }
        }

        return if (queryTokens.size == 1) {
            val single = queryTokens.first()
            score >= 90 && (titleWords.contains(single) || artistWords.contains(single))
        } else {
            matchedTokens == queryTokens.size && score >= 110
        }
    }

    private fun calculateRelevanceScore(
        song: Song,
        queryTokens: List<String>,
        compactQuery: String
    ): Int {
        if (queryTokens.isEmpty()) return 0
        val title = song.title.lowercase()
        val artist = song.artist.lowercase()
        val album = song.album.lowercase()
        val compactTitle = title.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val compactAlbum = album.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val titleWords = tokenize(title)

        var score = 0
        val fullQuery = queryTokens.joinToString(" ")

        if (title == fullQuery) {
            score += 320
        } else if (title.contains(fullQuery) || (compactQuery.length >= 4 && compactTitle.contains(compactQuery))) {
            score += 170
        }

        if (album == fullQuery) {
            score += 200
        } else if (album.contains(fullQuery) || (compactQuery.length >= 4 && compactAlbum.contains(compactQuery))) {
            score += 120
        }

        if (artist.contains(fullQuery)) {
            score += 140
        }

        for (token in queryTokens) {
            when {
                titleWords.any { it == token } -> score += 75
                title.contains(token) -> score += 50
                token.length >= 5 && titleWords.any { levenshteinDistance(it, token) <= 2 } -> score += 35
            }
            if (album.contains(token)) score += 45
            if (artist.contains(token)) score += 40
        }

        return score
    }

    private fun calculateAlbumRelevanceScore(
        album: AlbumSearchResult,
        queryTokens: List<String>,
        compactQuery: String
    ): Int {
        if (queryTokens.isEmpty()) return 0
        val title = album.title.lowercase()
        val movie = album.movieName?.lowercase().orEmpty()
        val artist = album.artist.lowercase()
        val titleWords = tokenize(title)
        val movieWords = tokenize(movie)
        val compactTitle = title.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val compactMovie = movie.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
        val fullQuery = queryTokens.joinToString(" ")

        var score = 0

        when {
            movie == fullQuery || title == fullQuery -> score += 420
            movie.startsWith(fullQuery) || title.startsWith(fullQuery) -> score += 300
            movie.contains(fullQuery) || title.contains(fullQuery) -> score += 230
            compactQuery.length >= 4 && (compactMovie.contains(compactQuery) || compactTitle.contains(compactQuery)) -> score += 200
        }

        if (artist.contains(fullQuery)) {
            score += 120
        }

        for (token in queryTokens) {
            when {
                movieWords.any { it == token } || titleWords.any { it == token } -> score += 80
                movie.contains(token) || title.contains(token) -> score += 55
                token.length >= 5 && (titleWords + movieWords).any { levenshteinDistance(it, token) <= 2 } -> score += 45
            }
            if (artist.contains(token)) score += 40
        }

        if (!album.movieName.isNullOrBlank()) score += 40
        if (album.isCompleteAlbum) score += 30
        if ((album.songCount ?: 0) >= 3) score += 20
        if (!album.artworkUri.isNullOrBlank()) score += 15

        return score
    }

    private fun stripMovieNoiseWords(query: String): String {
        val noise = setOf("movie", "film", "soundtrack", "ost", "album", "full", "songs", "song")
        val cleaned = tokenize(query).filterNot { it in noise }.joinToString(" ")
        return cleaned.ifBlank { query.trim() }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + min(prev, min(dp[j], dp[j - 1]))
                prev = temp
            }
        }
        return dp[b.length]
    }

    companion object {
        private const val MAX_SEARCH_CACHE_ENTRIES = 60
        private val ARCHIVE_NOISE_KEYWORDS = listOf(
            "radio ceylon",
            "vividh bharati",
            "all india radio",
            "radio broadcast",
            "news bulletin",
            "interview with"
        )
    }
}
