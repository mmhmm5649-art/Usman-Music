package com.usman.musicplayer.data.repository.providers

import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.usman.musicplayer.data.remote.JioSaavnApiService
import com.usman.musicplayer.data.remote.JioSaavnSong
import com.usman.musicplayer.extension.JioSaavnDecryptor
import com.usman.musicplayer.model.ArtistDetailsData
import com.usman.musicplayer.model.ArtistMatchingPolicy
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.playback.PlaybackErrorRepository
import com.usman.musicplayer.sdk.AlbumDetails
import com.usman.musicplayer.sdk.AlbumSearchResult
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.abs

/**
 * Native Kotlin Music Provider for JioSaavn.
 * Implements real searching and stream resolution using JioSaavn's internal web API.
 */
class JioSaavnMusicProvider(
    private val apiService: JioSaavnApiService,
    private val moshi: Moshi,
    private val publicApiBases: List<String> = PUBLIC_API_BASES
) : MusicProvider {

    companion object {
        private const val TAG = "USMAN_PLAYBACK"
        private const val MIRROR_COOLDOWN_MS = 45_000L
        val PUBLIC_API_BASES = listOf(
            "https://saavn-api-tau.vercel.app",
            "https://jiosaavn-api-sage.vercel.app",
            "https://saavn.dev"
        )
    }

    @Volatile
    private var preferredBaseUrl: String? = null
    private val mirrorCooldownUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun orderedPublicBases(): List<String> {
        if (publicApiBases.size <= 1) return publicApiBases
        val now = System.currentTimeMillis()
        val preferred = preferredBaseUrl?.takeIf { base ->
            base in publicApiBases && (mirrorCooldownUntil[base] ?: 0L) <= now
        }
        val healthy = publicApiBases.filter { it != preferred && (mirrorCooldownUntil[it] ?: 0L) <= now }
        val cooling = publicApiBases.filter { it != preferred && (mirrorCooldownUntil[it] ?: 0L) > now }
        return buildList {
            if (preferred != null) add(preferred)
            addAll(healthy)
            addAll(cooling)
        }
    }

    private fun markMirrorHealthy(baseUrl: String) {
        preferredBaseUrl = baseUrl
        mirrorCooldownUntil.remove(baseUrl)
    }

    private fun markMirrorFailed(baseUrl: String) {
        mirrorCooldownUntil[baseUrl] = System.currentTimeMillis() + MIRROR_COOLDOWN_MS
        if (preferredBaseUrl == baseUrl) {
            preferredBaseUrl = null
        }
    }

    override val metadata = ProviderMetadata(
        id = "jiosaavn_native_provider",
        name = "JioSaavn",
        version = "2.1.0",
        isFullPlaybackSupported = true,
        authRequired = false
    )

    override suspend fun search(query: String, limit: Int): Result<List<Song>> =
        searchPaginated(query, limit, page = 1)

    override suspend fun searchPaginated(query: String, limit: Int, page: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        val safePage = page.coerceAtLeast(1)
        Log.d(TAG, "JioSaavn search requested for query: '$trimmed' (page=$safePage)")

        // Strategy 1: Try public REST API (prioritizing fastest healthy mirror)
        for (baseUrl in orderedPublicBases()) {
            try {
                val encodedQuery = Uri.encode(trimmed)
                val url = "$baseUrl/api/search/songs?query=$encodedQuery&page=$safePage&limit=$limit"
                Log.d(TAG, "[Search Strategy 1] Trying public API: $url")
                val response = apiService.searchSongsCustom(url)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                        val adapter = moshi.adapter<Map<String, Any>>(type)
                        val rawBody = adapter.fromJson(bodyString)

                        val songMaps = extractSongListFromResponse(rawBody)
                        if (songMaps.isNotEmpty()) {
                            markMirrorHealthy(baseUrl)
                            val songs = songMaps.map { parseSongFromMap(it) }
                            Log.d(TAG, "[Search Strategy 1] Succeeded via $baseUrl, returned ${songs.size} tracks (page=$safePage)")
                            return@withContext Result.success(songs)
                        }
                    }
                } else if (response.code() >= 500 || response.code() == 429) {
                    markMirrorFailed(baseUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markMirrorFailed(baseUrl)
                Log.w(TAG, "[Search Strategy 1] Error from $baseUrl: ${e.message}")
            }
        }

        // Strategy 2: Official JioSaavn API fallback
        try {
            Log.d(TAG, "[Search Strategy 2] Falling back to official JioSaavn API (page=$safePage)")
            val response = apiService.searchSongs(query = trimmed, limit = limit, page = safePage)
            if (response.isSuccessful) {
                val results = response.body()?.results ?: emptyList()
                val songs = results.map { it.toSong() }
                Log.d(TAG, "[Search Strategy 2] Succeeded, returned ${songs.size} tracks")
                Result.success(songs)
            } else {
                Log.e(TAG, "JioSaavn search failed with HTTP code: ${response.code()}")
                Result.failure(IOException("Music search failed (${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "JioSaavn search exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Fast path 1: Check if contentUriString is already resolved and cached
            val cachedUrl = song.contentUriString
            if (!cachedUrl.isNullOrBlank()) {
                val sanitizedHost = runCatching { Uri.parse(cachedUrl).host }.getOrNull() ?: "unknown"
                Log.d(TAG, "[Stage 4] Using cached stream URL for '${song.title}', host: $sanitizedHost")
                return@withContext Result.success(cachedUrl)
            }

            val songId = song.filePath.takeIf { !it.isNullOrBlank() && !it.startsWith("http") }
                ?: song.id.toString().takeIf { it.isNotBlank() && it != "0" }
                ?: run {
                    Log.e(TAG, "[Stage 4] Missing valid song ID for '${song.title}'")
                    return@withContext Result.failure(Exception("Missing song ID for stream resolution"))
                }

            // Strategy 1: Resolve stream via public API details endpoint (/api/songs/{id})
            for (baseUrl in publicApiBases) {
                try {
                    val url = "$baseUrl/api/songs/$songId"
                    Log.d(TAG, "[Stage 4 Strategy 1] Requesting song details from $url")
                    val resp = apiService.getSongDetailsCustom(url)
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        val songData = resp.body()?.data.orEmpty()
                        val firstSong = songData.firstOrNull()
                        val resolvedUrl = firstSong?.let { extractHighestQualityUrl(it.downloadUrl) }
                        if (!resolvedUrl.isNullOrBlank()) {
                            val host = runCatching { Uri.parse(resolvedUrl).host }.getOrNull() ?: "unknown"
                            Log.d(TAG, "[Stage 4 Strategy 1] Stream resolution success for '${song.title}' via $baseUrl, host: $host")
                            return@withContext Result.success(resolvedUrl)
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w(TAG, "[Stage 4 Strategy 1] Error from $baseUrl: ${e.message}")
                }
            }

            // Strategy 2: Official JioSaavn details endpoint with pids and DES decryption
            Log.d(TAG, "[Stage 4 Strategy 2] Requesting details from official JioSaavn API for songId: $songId")
            val response = apiService.getSongDetailsWithReferer(pids = songId)
            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("Empty response body"))
                }

                val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val rawBody = adapter.fromJson(responseBody)
                
                Log.d(TAG, "[Stage 4: API Response] Top-level keys: ${rawBody?.keys}")
                
                val songDetailsMap = extractSongDetailsFromResponse(rawBody, songId)
                
                if (songDetailsMap == null) {
                    Log.e(TAG, "[Stage 4: API Response] Failed to extract details map for ID: $songId")
                } else {
                    Log.d(TAG, "[Stage 4: API Response] Song details map keys: ${songDetailsMap.keys}")
                }

                val encryptedUrl = songDetailsMap?.get("encrypted_media_url") as? String
                    ?: songDetailsMap?.get("encryptedMediaUrl") as? String
                    ?: (songDetailsMap?.get("more_info") as? Map<*, *>)?.get("encrypted_media_url") as? String
                val mediaUrl = songDetailsMap?.get("media_url") as? String
                    ?: songDetailsMap?.get("mediaUrl") as? String
                    ?: (songDetailsMap?.get("more_info") as? Map<*, *>)?.get("media_url") as? String

                if (encryptedUrl == null && mediaUrl == null) {
                    Log.e(TAG, "Extraction failed: No URL fields found in map or more_info. Map keys: ${songDetailsMap?.keys}, Full raw body: $rawBody")
                } else {
                    Log.d(TAG, "[Stage 4] ID: ${song.id}, Title: '${song.title}', EncryptedUrlFound: ${encryptedUrl != null}, MediaUrlFound: ${mediaUrl != null}")
                }

                val decryptedUrl = JioSaavnDecryptor.decryptUrl(encryptedUrl) ?: mediaUrl
                val finalUrl = if (decryptedUrl?.startsWith("http://") == true) {
                    decryptedUrl.replace("http://", "https://")
                } else {
                    decryptedUrl
                }

                if (!finalUrl.isNullOrBlank()) {
                    val sanitizedHost = runCatching { Uri.parse(finalUrl).host }.getOrNull() ?: "unknown"
                    Log.d(TAG, "[Stage 4] Stream resolution success for '${song.title}', host: $sanitizedHost, URL: $finalUrl")
                    Result.success(finalUrl)
                } else {
                    val errorMsg = "Failed to decrypt or resolve stream URL for '${song.title}'. Encrypted: ${encryptedUrl?.take(20)}, Media: ${mediaUrl?.take(20)}"
                    Log.e(TAG, errorMsg)
                    PlaybackErrorRepository.setError(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                Log.e(TAG, "JioSaavn API details failed for '${song.title}' with code: ${response.code()}, Body: ${response.errorBody()?.string()}")
                Result.failure(IOException("Track details unavailable (${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is java.net.UnknownHostException || e is java.io.IOException || e is java.net.SocketTimeoutException) {
                Log.w(TAG, "Network offline or unavailable during JioSaavn stream resolution for '${song.title}': ${e.message}")
            } else {
                Log.e(TAG, "Exception during JioSaavn stream resolution for '${song.title}': ${e.message}", e)
            }
            Result.failure(e)
        }
    }


    override suspend fun searchAlbums(query: String, limit: Int): Result<List<AlbumSearchResult>> =
        searchAlbumsPaginated(query, limit, page = 1)

    override suspend fun searchAlbumsPaginated(
        query: String,
        limit: Int,
        page: Int
    ): Result<List<AlbumSearchResult>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        val safePage = page.coerceAtLeast(1)
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

        // Strategy 1: Public REST API (/api/search/albums, with /api/search fallback only if needed)
        for (baseUrl in orderedPublicBases()) {
            try {
                val encodedQuery = Uri.encode(trimmed)
                val albumUrl = "$baseUrl/api/search/albums?query=$encodedQuery&page=$safePage&limit=$limit"
                val response = apiService.searchSongsCustom(albumUrl)
                val collected = mutableListOf<AlbumSearchResult>()
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val rawBody = mapAdapter.fromJson(bodyString)
                        val albumMaps = extractAlbumListFromResponse(rawBody)
                        collected.addAll(albumMaps.mapNotNull { parseAlbumResultFromMap(it) })
                    }
                } else if (response.code() >= 500 || response.code() == 429) {
                    markMirrorFailed(baseUrl)
                }

                if (collected.isEmpty() && safePage == 1) {
                    val globalUrl = "$baseUrl/api/search?query=$encodedQuery"
                    val globalResp = apiService.searchSongsCustom(globalUrl)
                    if (globalResp.isSuccessful) {
                        val bodyString = globalResp.body()?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val rawBody = mapAdapter.fromJson(bodyString)
                            val globalAlbumMaps = extractGlobalAlbumsFromResponse(rawBody)
                            collected.addAll(globalAlbumMaps.mapNotNull { parseAlbumResultFromMap(it) })
                        }
                    }
                }

                if (collected.isNotEmpty()) {
                    markMirrorHealthy(baseUrl)
                    val deduped = collected.distinctBy { it.id.ifBlank { "${it.title.lowercase()}_${it.artist.lowercase()}" } }
                        .take(limit)
                    Log.d(TAG, "[Album Search Strategy 1] Succeeded via $baseUrl, returned ${deduped.size} albums (page=$safePage)")
                    return@withContext Result.success(deduped)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markMirrorFailed(baseUrl)
                Log.w(TAG, "[Album Search Strategy 1] Error from $baseUrl: ${e.message}")
            }
        }

        // Strategy 2: Official JioSaavn search.getAlbumResults fallback
        try {
            val response = apiService.searchAlbums(query = trimmed, limit = limit, page = safePage)
            if (response.isSuccessful) {
                val bodyString = response.body()?.string()
                if (!bodyString.isNullOrBlank()) {
                    val rawBody = mapAdapter.fromJson(bodyString)
                    val albumMaps = extractAlbumListFromResponse(rawBody)
                    val albums = albumMaps.mapNotNull { parseAlbumResultFromMap(it) }
                        .distinctBy { it.id.ifBlank { "${it.title.lowercase()}_${it.artist.lowercase()}" } }
                        .take(limit)
                    Log.d(TAG, "[Album Search Strategy 2] Official API returned ${albums.size} albums (page=$safePage)")
                    return@withContext Result.success(albums)
                }
                return@withContext Result.success(emptyList())
            } else {
                return@withContext Result.failure(IOException("Album search failed (${response.code()})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[Album Search Strategy 2] Exception: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    override suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> = withContext(Dispatchers.IO) {
        val albumId = album.id.trim()
        if (albumId.isBlank() && album.externalUrl.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing album ID"))
        }

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)
        var bestPublicDetails: AlbumDetails? = null

        // Strategy 1: Public REST API (/api/albums?id=... or ?link=...)
        for (baseUrl in publicApiBases) {
            try {
                val queryParam = if (albumId.isNotBlank() && !albumId.startsWith("inferred_")) {
                    "id=${Uri.encode(albumId)}"
                } else if (!album.externalUrl.isNullOrBlank()) {
                    "link=${Uri.encode(album.externalUrl)}"
                } else {
                    continue
                }
                val url = "$baseUrl/api/albums?$queryParam"
                val response = apiService.searchSongsCustom(url)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val rawBody = mapAdapter.fromJson(bodyString)
                        val details = parseAlbumDetailsFromResponse(rawBody, fallback = album)
                        if (details != null && details.tracks.isNotEmpty()) {
                            if (bestPublicDetails == null || details.tracks.size > bestPublicDetails.tracks.size) {
                                bestPublicDetails = details
                            }
                            if (details.isComplete) {
                                Log.d(TAG, "[Album Details Strategy 1] Loaded complete '${details.title}' (${details.tracks.size} tracks) via $baseUrl")
                                return@withContext Result.success(details)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[Album Details Strategy 1] Error from $baseUrl: ${e.message}")
            }
        }

        // Strategy 2: Official JioSaavn content.getAlbumDetails
        if (albumId.isNotBlank() && !albumId.startsWith("inferred_")) {
            try {
                val response = apiService.getAlbumDetailsWithReferer(albumId = albumId)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val rawBody = mapAdapter.fromJson(bodyString)
                        val officialDetails = parseAlbumDetailsFromResponse(rawBody, fallback = album)
                        if (officialDetails != null &&
                            (bestPublicDetails == null || officialDetails.tracks.size >= bestPublicDetails.tracks.size)
                        ) {
                            Log.d(TAG, "[Album Details Strategy 2] Official API loaded '${officialDetails.title}' (${officialDetails.tracks.size} tracks, complete=${officialDetails.isComplete})")
                            return@withContext Result.success(officialDetails)
                        }
                    }
                } else if (bestPublicDetails == null) {
                    return@withContext Result.failure(IOException("Album details unavailable (${response.code()})"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[Album Details Strategy 2] Exception: ${e.message}")
                if (bestPublicDetails == null) {
                    return@withContext Result.failure(e)
                }
            }
        }

        Result.success(bestPublicDetails)
    }

    suspend fun searchArtists(query: String, limit: Int = 20): Result<List<OnlineArtistResult>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

        for (baseUrl in orderedPublicBases()) {
            try {
                val url = "$baseUrl/api/search/artists?query=${Uri.encode(trimmed)}&limit=$limit"
                val response = apiService.searchSongsCustom(url)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val rawBody = mapAdapter.fromJson(bodyString)
                        val artistMaps = extractAlbumListFromResponse(rawBody)
                        val artists = artistMaps.mapNotNull { parseArtistResultFromMap(it) }
                            .distinctBy { it.name.lowercase() }
                            .take(limit)
                        if (artists.isNotEmpty()) {
                            markMirrorHealthy(baseUrl)
                            return@withContext Result.success(artists)
                        }
                    }
                } else if (response.code() >= 500 || response.code() == 429) {
                    markMirrorFailed(baseUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markMirrorFailed(baseUrl)
                Log.w(TAG, "[Artist Search Strategy 1] Error from $baseUrl: ${e.message}")
            }
        }

        try {
            val response = apiService.searchArtists(query = trimmed, limit = limit)
            if (response.isSuccessful) {
                val bodyString = response.body()?.string()
                if (!bodyString.isNullOrBlank()) {
                    val rawBody = mapAdapter.fromJson(bodyString)
                    val artistMaps = extractAlbumListFromResponse(rawBody)
                    val artists = artistMaps.mapNotNull { parseArtistResultFromMap(it) }
                        .distinctBy { it.name.lowercase() }
                        .take(limit)
                    return@withContext Result.success(artists)
                }
            }
            Result.success(emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 10): Result<List<CuratedPlaylist>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

        for (baseUrl in orderedPublicBases()) {
            try {
                val url = "$baseUrl/api/search/playlists?query=${Uri.encode(trimmed)}&limit=$limit"
                val response = apiService.searchSongsCustom(url)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val rawBody = mapAdapter.fromJson(bodyString)
                        val playlistMaps = extractPlaylistListFromResponse(rawBody)
                        val playlists = playlistMaps.mapNotNull { parsePlaylistResultFromMap(it) }
                            .distinctBy { it.id.ifBlank { it.title.lowercase() } }
                            .take(limit)
                        if (playlists.isNotEmpty()) {
                            markMirrorHealthy(baseUrl)
                            return@withContext Result.success(playlists)
                        }
                    }
                } else if (response.code() >= 500 || response.code() == 429) {
                    markMirrorFailed(baseUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markMirrorFailed(baseUrl)
                Log.w(TAG, "[Playlist Search Strategy 1] Error from $baseUrl: ${e.message}")
            }
        }

        try {
            val response = apiService.searchPlaylists(query = trimmed, limit = limit)
            if (response.isSuccessful) {
                val bodyString = response.body()?.string()
                if (!bodyString.isNullOrBlank()) {
                    val rawBody = mapAdapter.fromJson(bodyString)
                    val playlistMaps = extractPlaylistListFromResponse(rawBody)
                    val playlists = playlistMaps.mapNotNull { parsePlaylistResultFromMap(it) }
                        .distinctBy { it.id.ifBlank { it.title.lowercase() } }
                        .take(limit)
                    return@withContext Result.success(playlists)
                }
            }
            Result.success(emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylistDetails(
        playlistId: String,
        fallbackTitle: String = "Playlist",
        fallbackDescription: String = "",
        fallbackArtwork: String? = null
    ): Result<CuratedPlaylist?> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.trim()
        if (cleanId.isBlank()) return@withContext Result.success(null)

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

        for (baseUrl in publicApiBases) {
            try {
                val url = "$baseUrl/api/playlists?id=${Uri.encode(cleanId)}&limit=50"
                val response = apiService.searchSongsCustom(url)
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val rawBody = mapAdapter.fromJson(bodyString)
                        val parsed = parsePlaylistDetailsFromResponse(rawBody, cleanId, fallbackTitle, fallbackDescription, fallbackArtwork)
                        if (parsed != null && parsed.songs.isNotEmpty()) {
                            return@withContext Result.success(parsed)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[Playlist Details Strategy 1] Error from $baseUrl: ${e.message}")
            }
        }

        try {
            val response = apiService.getPlaylistDetailsWithReferer(listId = cleanId)
            if (response.isSuccessful) {
                val bodyString = response.body()?.string()
                if (!bodyString.isNullOrBlank()) {
                    val rawBody = mapAdapter.fromJson(bodyString)
                    val parsed = parsePlaylistDetailsFromResponse(rawBody, cleanId, fallbackTitle, fallbackDescription, fallbackArtwork)
                    if (parsed != null) {
                        return@withContext Result.success(parsed)
                    }
                }
            }
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getArtistDetails(artist: OnlineArtistResult): Result<ArtistDetailsData?> = withContext(Dispatchers.IO) {
        val cleanId = artist.id.trim()
        if (!ArtistMatchingPolicy.isProviderArtistId(cleanId)) {
            return@withContext Result.success(null)
        }

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

        // Strategy 1: Public REST API (/api/artists/{id} or /api/artists?id=...)
        for (baseUrl in publicApiBases) {
            for (url in listOf(
                "$baseUrl/api/artists?id=${Uri.encode(cleanId)}&songCount=25&albumCount=15",
                "$baseUrl/api/artists/${Uri.encode(cleanId)}"
            )) {
                try {
                    val response = apiService.searchSongsCustom(url)
                    if (response.isSuccessful) {
                        val bodyString = response.body()?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val rawBody = mapAdapter.fromJson(bodyString)
                            val parsed = parseArtistDetailsFromResponse(rawBody, fallback = artist)
                            if (parsed != null && (parsed.popularSongs.isNotEmpty() || parsed.albums.isNotEmpty())) {
                                return@withContext Result.success(parsed)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[Artist Details Strategy 1] Error from $baseUrl: ${e.message}")
                }
            }
        }

        // Strategy 2: Official JioSaavn artist.getArtistPageDetails
        try {
            val response = apiService.getArtistPageDetailsWithReferer(artistId = cleanId)
            if (response.isSuccessful) {
                val bodyString = response.body()?.string()
                if (!bodyString.isNullOrBlank()) {
                    val rawBody = mapAdapter.fromJson(bodyString)
                    val parsed = parseArtistDetailsFromResponse(rawBody, fallback = artist)
                    if (parsed != null) {
                        return@withContext Result.success(parsed)
                    }
                }
            }
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(song: Song): Result<String?> = withContext(Dispatchers.IO) {
        val songId = song.filePath ?: run {
            Log.w("USMAN_LYRICS", "[JioSaavnMusicProvider] Missing songId (filePath) for '${song.title}'")
            return@withContext Result.success(null)
        }
        try {
            Log.d("USMAN_LYRICS", "[JioSaavnMusicProvider] Requesting lyrics for '${song.title}' with songId: $songId")
            val response = apiService.getLyrics(songId)
            if (response.isSuccessful) {
                val lyrics = response.body()?.lyrics
                val hasLyrics = !lyrics.isNullOrBlank()
                Log.d("USMAN_LYRICS", "[JioSaavnMusicProvider] Lyrics response for '$songId': code=${response.code()}, hasLyrics=$hasLyrics, length=${lyrics?.length ?: 0}")
                Result.success(lyrics)
            } else {
                Log.w("USMAN_LYRICS", "[JioSaavnMusicProvider] Failed HTTP response ${response.code()} for songId: $songId")
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is java.net.UnknownHostException || e is java.io.IOException || e is java.net.SocketTimeoutException) {
                Log.w("USMAN_LYRICS", "[JioSaavnMusicProvider] Network offline during lyrics fetch for songId $songId: ${e.message}")
            } else {
                Log.e("USMAN_LYRICS", "[JioSaavnMusicProvider] Exception fetching lyrics for songId $songId: ${e.message}", e)
            }
            Result.success(null)
        }
    }

    private fun extractSongDetailsFromResponse(body: Map<String, Any>?, songId: String): Map<String, Any?>? {
        if (body == null) return null
        
        // 1. Direct songId key: { "oM52yM5X": { ... } }
        val direct = body[songId] as? Map<String, Any?>
        if (direct != null) return direct

        // 2. "songs" list: { "songs": [ { "id": "oM52yM5X", ... } ] }
        val songsList = body["songs"] as? List<*>
        if (songsList != null) {
            for (item in songsList) {
                if (item is Map<*, *>) {
                    val innerId = (item["id"] ?: item["song_id"])?.toString()
                    if (innerId == songId || innerId != null) {
                        @Suppress("UNCHECKED_CAST")
                        return item as Map<String, Any?>
                    }
                }
            }
        }

        // 3. Search within all map and list values
        for ((k, v) in body) {
            if (v is Map<*, *>) {
                val innerId = (v["id"] ?: v["song_id"])?.toString()
                if (innerId == songId || k == songId) {
                    @Suppress("UNCHECKED_CAST")
                    return v as Map<String, Any?>
                }
            }
            if (v is List<*>) {
                for (item in v) {
                    if (item is Map<*, *>) {
                        val innerId = (item["id"] ?: item["song_id"])?.toString()
                        if (innerId == songId) {
                            @Suppress("UNCHECKED_CAST")
                            return item as Map<String, Any?>
                        }
                    }
                }
            }
        }

        // 4. Root itself is the song object
        if (body["id"] != null || body["encrypted_media_url"] != null || body["encryptedMediaUrl"] != null || body["more_info"] != null) {
            return body
        }

        val firstVal = body.values.firstOrNull()
        if (firstVal is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return firstVal as Map<String, Any?>
        }
        
        return null
    }

    private fun JioSaavnSong.toSong(): Song {
        val titleName = decodeHtmlEntities(parseTitle(this))
        val artistName = decodeHtmlEntities(parseArtists(this))
        val albumName = decodeHtmlEntities(parseAlbum(this))
        val albumId = parseAlbumId(this)
        val releaseYear = parseReleaseYear(this)
        val artwork = parseArtwork(this)
        val durationMs = parseDuration(this)

        val songIdStr = id ?: "0"
        val songIdLong = try {
            songIdStr.toLong()
        } catch (e: Exception) {
            abs(songIdStr.hashCode().toLong())
        }

        // Extract encrypted media URL if search result already returned it!
        val searchEncryptedUrl = (encryptedMediaUrl
            ?: moreInfo?.get("encrypted_media_url")?.toString()
            ?: moreInfo?.get("encryptedMediaUrl")?.toString())?.takeIf { it.isNotBlank() }

        val searchDecryptedUrl = if (!searchEncryptedUrl.isNullOrBlank()) {
            JioSaavnDecryptor.decryptUrl(searchEncryptedUrl)
        } else {
            mediaUrl ?: moreInfo?.get("media_url")?.toString()
        }

        val finalSearchUrl = if (searchDecryptedUrl?.startsWith("http://") == true) {
            searchDecryptedUrl.replace("http://", "https://")
        } else {
            searchDecryptedUrl
        }

        if (!finalSearchUrl.isNullOrBlank()) {
            val sanitizedHost = runCatching { Uri.parse(finalSearchUrl).host }.getOrNull() ?: "unknown"
            Log.d(TAG, "[Search Parse] Pre-resolved stream URL for '$titleName', host: $sanitizedHost")
        }

        val resolvedAlbumUrl = (albumUrl
            ?: (album as? Map<*, *>)?.get("url")?.toString()
            ?: moreInfo?.get("album_url")?.toString())?.takeIf { it.isNotBlank() }

        val resolvedLanguage = (language
            ?: moreInfo?.get("language")?.toString())
            ?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" }

        return Song(
            id = songIdLong,
            title = titleName,
            artist = artistName,
            album = albumName,
            durationMs = durationMs,
            filePath = songIdStr,
            folderName = "JioSaavn",
            albumArtUri = artwork,
            contentUriString = finalSearchUrl,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            externalUrl = permaUrl,
            language = resolvedLanguage,
            sourceProvider = "JioSaavn",
            attribution = "$artistName • JioSaavn",
            isStreamable = true,
            isFullTrackVerified = true,
            isRightsVerified = true,
            albumId = albumId,
            releaseYear = releaseYear,
            albumUrl = resolvedAlbumUrl
        )
    }

    private fun parseTitle(song: JioSaavnSong): String {
        return song.title?.takeIf { it.isNotBlank() }
            ?: song.song?.takeIf { it.isNotBlank() }
            ?: song.moreInfo?.get("song")?.toString()?.takeIf { it.isNotBlank() }
            ?: song.moreInfo?.get("title")?.toString()?.takeIf { it.isNotBlank() }
            ?: "Unknown Track"
    }

    private fun parseArtists(song: JioSaavnSong): String {
        val raw = song.primaryArtists ?: song.singers ?: song.artist
            ?: (song.moreInfo?.get("artistMap") as? Map<*, *>)?.let { extractArtistMapNames(it) }
            ?: song.moreInfo?.get("primary_artists")
            ?: song.moreInfo?.get("singers")
            ?: song.moreInfo?.get("artist")
            ?: song.moreInfo?.get("music")
        return extractArtistString(raw) ?: "Unknown Artist"
    }

    private fun extractArtistString(value: Any?): String? {
        when (value) {
            is String -> {
                if (value.isNotBlank()) return decodeHtmlEntities(value.trim())
            }
            is List<*> -> {
                val names = value.mapNotNull { item ->
                    when (item) {
                        is String -> item.takeIf { it.isNotBlank() }?.let(::decodeHtmlEntities)
                        is Map<*, *> -> (item["name"] ?: item["title"] ?: item["artist"])?.toString()?.takeIf { it.isNotBlank() }?.let(::decodeHtmlEntities)
                        else -> item?.toString()?.takeIf { it.isNotBlank() }?.let(::decodeHtmlEntities)
                    }
                }.distinct()
                if (names.isNotEmpty()) return names.joinToString(", ")
            }
            is Map<*, *> -> {
                val mapNames = extractArtistMapNames(value)
                if (!mapNames.isNullOrBlank()) return mapNames
                val name = (value["name"] ?: value["title"] ?: value["artist"])?.toString()
                if (!name.isNullOrBlank()) return decodeHtmlEntities(name.trim())
            }
        }
        return null
    }

    private fun parseAlbum(song: JioSaavnSong): String {
        val raw = song.album ?: song.moreInfo?.get("album")
        return when (raw) {
            is String -> raw.takeIf { it.isNotBlank() } ?: "Unknown Album"
            is Map<*, *> -> (raw["album"] ?: raw["name"] ?: raw["title"])?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Album"
            else -> raw?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        }
    }

    private fun parseAlbumId(song: JioSaavnSong): String? {
        val rawAlbum = song.album as? Map<*, *>
        return (rawAlbum?.get("id")
            ?: song.moreInfo?.get("album_id")
            ?: song.moreInfo?.get("albumid"))?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun parseReleaseYear(song: JioSaavnSong): String? {
        val raw = song.year?.takeIf { it.isNotBlank() }
            ?: song.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
            ?: song.moreInfo?.get("year")?.toString()?.takeIf { it.isNotBlank() }
            ?: song.moreInfo?.get("release_date")?.toString()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        return raw?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun parseArtwork(song: JioSaavnSong): String? {
        val img = song.image ?: song.moreInfo?.get("image")?.toString()
        if (img.isNullOrBlank()) return null
        return img.replace("150x150", "500x500")
            .replace("50x50", "500x500")
            .replace("100x100", "500x500")
            .replace("http://", "https://")
    }

    private fun parseDuration(song: JioSaavnSong): Long {
        val raw = song.duration ?: song.moreInfo?.get("duration")
        val seconds = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 240L
            else -> 240L
        }
        return seconds * 1000L
    }

    private fun com.usman.musicplayer.data.remote.SaavnDevSong.toSong(): Song {
        val songIdStr = id ?: "0"
        val songIdLong = try {
            songIdStr.toLong()
        } catch (e: Exception) {
            abs(songIdStr.hashCode().toLong())
        }

        val titleName = name?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: "Unknown Track"

        val artistName = parseSaavnDevArtists(artists)
        val albumName = parseSaavnDevAlbum(album)
        val artwork = parseSaavnDevArtwork(image)
        val streamUrl = extractHighestQualityUrl(downloadUrl)
        val durationMs = when (duration) {
            is Number -> duration.toLong() * 1000L
            is String -> (duration.toLongOrNull() ?: 240L) * 1000L
            else -> 240000L
        }

        if (!streamUrl.isNullOrBlank()) {
            val host = runCatching { Uri.parse(streamUrl).host }.getOrNull() ?: "unknown"
            Log.d(TAG, "[SaavnDev Parse] Pre-resolved stream URL for '$titleName', host: $host")
        }

        val resolvedLanguage = language?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" }

        return Song(
            id = songIdLong,
            title = titleName,
            artist = artistName,
            album = albumName,
            durationMs = durationMs,
            filePath = songIdStr,
            folderName = "JioSaavn",
            albumArtUri = artwork,
            contentUriString = streamUrl,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            externalUrl = url,
            language = resolvedLanguage,
            sourceProvider = "JioSaavn",
            attribution = "$artistName • JioSaavn",
            isStreamable = true,
            isFullTrackVerified = true,
            isRightsVerified = true
        )
    }

    private fun parseSaavnDevArtists(artists: Any?): String {
        if (artists == null) return "Unknown Artist"
        if (artists is String) return artists
        if (artists is Map<*, *>) {
            val primary = artists["primary"] as? List<*>
            if (!primary.isNullOrEmpty()) {
                val names = primary.mapNotNull {
                    when (it) {
                        is Map<*, *> -> it["name"]?.toString()
                        else -> it?.toString()
                    }
                }
                if (names.isNotEmpty()) return names.joinToString(", ")
            }
            val all = artists["all"] as? List<*>
            if (!all.isNullOrEmpty()) {
                val names = all.mapNotNull {
                    when (it) {
                        is Map<*, *> -> it["name"]?.toString()
                        else -> it?.toString()
                    }
                }
                if (names.isNotEmpty()) return names.joinToString(", ")
            }
        }
        return "Unknown Artist"
    }

    private fun parseSaavnDevAlbum(album: Any?): String {
        return when (album) {
            is String -> album.takeIf { it.isNotBlank() } ?: "Unknown Album"
            is Map<*, *> -> album["name"]?.toString() ?: "Unknown Album"
            else -> "Unknown Album"
        }
    }

    private fun parseSaavnDevArtwork(image: Any?): String? {
        if (image == null) return null
        if (image is String && image.isNotBlank()) return image
        if (image is List<*>) {
            // Find highest quality e.g. 500x500
            for (quality in listOf("500x500", "150x150", "50x50")) {
                for (item in image) {
                    if (item is Map<*, *>) {
                        if (item["quality"] == quality) {
                            val url = item["url"]?.toString()
                            if (!url.isNullOrBlank()) return url
                        }
                    }
                }
            }
            val first = image.lastOrNull()
            if (first is Map<*, *>) return first["url"]?.toString()
        }
        return null
    }

    private fun extractHighestQualityUrl(downloadUrl: Any?): String? {
        if (downloadUrl == null) return null
        if (downloadUrl is String && downloadUrl.isNotBlank()) {
            return if (downloadUrl.startsWith("http://")) downloadUrl.replace("http://", "https://") else downloadUrl
        }
        if (downloadUrl is List<*>) {
            // Check qualities in preference order: 320kbps -> 160kbps -> 96kbps -> 48kbps -> 12kbps
            val preferredQualities = listOf("320kbps", "160kbps", "96kbps", "48kbps", "12kbps")
            for (q in preferredQualities) {
                for (item in downloadUrl) {
                    if (item is Map<*, *>) {
                        if (item["quality"] == q) {
                            val url = item["url"]?.toString()
                            if (!url.isNullOrBlank()) {
                                return if (url.startsWith("http://")) url.replace("http://", "https://") else url
                            }
                        }
                    }
                }
            }
            // Fallback: pick the last item in the list which is typically the highest quality
            val lastItem = downloadUrl.lastOrNull()
            if (lastItem is Map<*, *>) {
                val url = lastItem["url"]?.toString()
                if (!url.isNullOrBlank()) {
                    return if (url.startsWith("http://")) url.replace("http://", "https://") else url
                }
            }
        }
        return null
    }

    private fun extractSongListFromResponse(body: Map<String, Any?>?): List<Map<String, Any?>> {
        if (body == null) return emptyList()
        
        val data = body["data"]
        when (data) {
            is Map<*, *> -> {
                val results = (data["results"] ?: data["songs"]) as? List<*>
                if (!results.isNullOrEmpty()) {
                    return results.filterIsInstance<Map<String, Any?>>()
                }
            }
            is List<*> -> {
                return data.filterIsInstance<Map<String, Any?>>()
            }
        }

        val rootResults = (body["results"] ?: body["songs"]) as? List<*>
        if (!rootResults.isNullOrEmpty()) {
            return rootResults.filterIsInstance<Map<String, Any?>>()
        }

        for ((_, v) in body) {
            if (v is List<*>) {
                val maps = v.filterIsInstance<Map<String, Any?>>()
                if (maps.isNotEmpty() && (maps[0].containsKey("title") || maps[0].containsKey("name") || maps[0].containsKey("id"))) {
                    return maps
                }
            }
            if (v is Map<*, *>) {
                val nestedResults = (v["results"] ?: v["songs"]) as? List<*>
                if (!nestedResults.isNullOrEmpty()) {
                    return nestedResults.filterIsInstance<Map<String, Any?>>()
                }
            }
        }

        return emptyList()
    }

    private fun parseSongFromMap(
        item: Map<String, Any?>,
        fallbackAlbumId: String? = null,
        fallbackAlbumTitle: String? = null,
        fallbackYear: String? = null,
        fallbackArtwork: String? = null
    ): Song {
        val moreInfo = item["more_info"] as? Map<*, *>
        val idStr = (item["id"] ?: item["song_id"] ?: item["pids"])?.toString() ?: "0"
        val songIdLong = try {
            idStr.toLong()
        } catch (e: Exception) {
            abs(idStr.hashCode().toLong())
        }

        val title = decodeHtmlEntities(
            (item["name"] ?: item["title"] ?: item["song"] ?: moreInfo?.get("song") ?: moreInfo?.get("title"))
                ?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Track"
        )

        val artistsObj = item["artists"]
            ?: item["primary_artists"]
            ?: item["singers"]
            ?: item["artist"]
            ?: moreInfo?.get("artistMap")
            ?: moreInfo?.get("primary_artists")
            ?: moreInfo?.get("singers")
            ?: moreInfo?.get("artist")
            ?: moreInfo?.get("music")
        val artist = decodeHtmlEntities(extractArtistString(artistsObj) ?: parseSaavnDevArtists(artistsObj))

        val albumObj = item["album"] ?: moreInfo?.get("album")
        val parsedAlbum = parseSaavnDevAlbum(albumObj)
        val album = decodeHtmlEntities(
            if (parsedAlbum == "Unknown Album" && !fallbackAlbumTitle.isNullOrBlank()) fallbackAlbumTitle else parsedAlbum
        )
        val albumId = ((albumObj as? Map<*, *>)?.get("id")
            ?: item["album_id"]
            ?: moreInfo?.get("album_id")
            ?: moreInfo?.get("albumid"))?.toString()?.takeIf { it.isNotBlank() } ?: fallbackAlbumId

        val releaseYear = (item["year"]
            ?: moreInfo?.get("year")
            ?: moreInfo?.get("release_date")?.toString()?.take(4))
            ?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: fallbackYear

        val imageObj = item["image"] ?: item["image_url"] ?: moreInfo?.get("image")
        val artwork = parseSaavnDevArtwork(imageObj)?.let(::upgradeArtworkQuality) ?: fallbackArtwork

        val downloadUrlObj = item["downloadUrl"] ?: item["download_url"]
        val directDownloadUrl = extractHighestQualityUrl(downloadUrlObj)
        val encryptedUrl = (item["encrypted_media_url"]
            ?: item["encryptedMediaUrl"]
            ?: moreInfo?.get("encrypted_media_url")
            ?: moreInfo?.get("encryptedMediaUrl"))?.toString()?.takeIf { it.isNotBlank() }
        val directMediaUrl = (item["media_url"] ?: moreInfo?.get("media_url"))?.toString()?.takeIf { it.isNotBlank() }

        val resolvedRawUrl = directDownloadUrl
            ?: encryptedUrl?.let { JioSaavnDecryptor.decryptUrl(it) }
            ?: directMediaUrl
        val streamUrl = if (resolvedRawUrl?.startsWith("http://") == true) {
            resolvedRawUrl.replace("http://", "https://")
        } else {
            resolvedRawUrl
        }

        val durationObj = item["duration"] ?: moreInfo?.get("duration")
        val durationMs = when (durationObj) {
            is Number -> durationObj.toLong() * 1000L
            is String -> (durationObj.toLongOrNull() ?: 240L) * 1000L
            else -> 240000L
        }

        val externalUrl = (item["url"] ?: item["perma_url"])?.toString()
        val resolvedAlbumUrl = ((albumObj as? Map<*, *>)?.get("url")
            ?: item["album_url"]
            ?: moreInfo?.get("album_url"))?.toString()?.takeIf { it.isNotBlank() }

        val resolvedLanguage = (item["language"] ?: moreInfo?.get("language"))
            ?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" }

        return Song(
            id = songIdLong,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filePath = idStr,
            folderName = "JioSaavn",
            albumArtUri = artwork,
            contentUriString = streamUrl,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            externalUrl = externalUrl,
            language = resolvedLanguage,
            sourceProvider = "JioSaavn",
            attribution = "$artist • JioSaavn",
            isStreamable = true,
            isFullTrackVerified = true,
            isRightsVerified = true,
            albumId = albumId,
            releaseYear = releaseYear,
            albumUrl = resolvedAlbumUrl
        )
    }

    private fun extractAlbumListFromResponse(body: Map<String, Any?>?): List<Map<String, Any?>> {
        if (body == null) return emptyList()
        val data = body["data"]
        when (data) {
            is Map<*, *> -> {
                val results = (data["results"] ?: data["albums"]) as? List<*>
                if (!results.isNullOrEmpty()) return results.filterIsInstance<Map<String, Any?>>()
            }
            is List<*> -> {
                val maps = data.filterIsInstance<Map<String, Any?>>()
                if (maps.isNotEmpty()) return maps
            }
        }

        val rootResults = (body["results"] ?: body["albums"]) as? List<*>
        if (!rootResults.isNullOrEmpty()) return rootResults.filterIsInstance<Map<String, Any?>>()

        for ((_, v) in body) {
            if (v is Map<*, *>) {
                val nested = (v["results"] ?: v["albums"]) as? List<*>
                if (!nested.isNullOrEmpty()) return nested.filterIsInstance<Map<String, Any?>>()
            }
        }
        return emptyList()
    }

    private fun extractGlobalAlbumsFromResponse(body: Map<String, Any?>?): List<Map<String, Any?>> {
        if (body == null) return emptyList()
        val data = body["data"] as? Map<*, *> ?: body
        val albumsSection = data["albums"] as? Map<*, *>
        val albumResults = (albumsSection?.get("results") as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()
        val topQuerySection = data["topQuery"] as? Map<*, *>
        val topAlbums = (topQuerySection?.get("results") as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            ?.filter { it["type"]?.toString()?.equals("album", ignoreCase = true) == true }
            .orEmpty()
        return topAlbums + albumResults
    }

    private fun parseAlbumResultFromMap(item: Map<String, Any?>): AlbumSearchResult? {
        val id = (item["id"] ?: item["albumid"] ?: item["album_id"])?.toString()?.trim().orEmpty()
        val rawTitle = (item["name"] ?: item["title"] ?: item["album"])?.toString()?.trim().orEmpty()
        if (rawTitle.isBlank()) return null

        val title = decodeHtmlEntities(rawTitle)
        val moreInfo = item["more_info"] as? Map<*, *>
        val rawArtists = item["artists"]
            ?: item["primary_artists"]
            ?: item["music"]
            ?: moreInfo?.get("artistMap")
            ?: moreInfo?.get("music")
            ?: moreInfo?.get("primary_artists")
            ?: item["description"]
            ?: item["subtitle"]
        val artist = decodeHtmlEntities(
            extractArtistString(rawArtists)
                ?: parseSaavnDevArtists(rawArtists).takeIf { it != "Unknown Artist" }
                ?: "Various Artists"
        )

        val year = (item["year"]
            ?: moreInfo?.get("year")
            ?: moreInfo?.get("release_date")?.toString()?.take(4))
            ?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "0" }

        val countRaw = item["songCount"] ?: item["song_count"] ?: item["list_count"]
            ?: moreInfo?.get("song_count") ?: moreInfo?.get("songCount")
        val songCount = when (countRaw) {
            is Number -> countRaw.toInt().takeIf { it > 0 }
            is String -> countRaw.toIntOrNull()?.takeIf { it > 0 }
            else -> (item["songs"] as? List<*>)?.size?.takeIf { it > 0 }
        }

        val artwork = parseSaavnDevArtwork(item["image"] ?: moreInfo?.get("image"))?.let(::upgradeArtworkQuality)
        val externalUrl = (item["url"] ?: item["perma_url"])?.toString()?.takeIf { it.isNotBlank() }
        val language = (item["language"] ?: moreInfo?.get("language"))?.toString()?.takeIf { it.isNotBlank() }

        val movieName = extractMovieNameIfSoundtrack(item, moreInfo, title)

        return AlbumSearchResult(
            id = id.ifBlank { externalUrl ?: title },
            title = title,
            movieName = movieName,
            artist = artist,
            year = year,
            songCount = songCount,
            artworkUri = artwork,
            sourceProvider = "JioSaavn",
            externalUrl = externalUrl,
            language = language,
            isCompleteAlbum = songCount != null && songCount > 0
        )
    }

    private fun parseAlbumDetailsFromResponse(
        body: Map<String, Any?>?,
        fallback: AlbumSearchResult
    ): AlbumDetails? {
        if (body == null) return null
        val root = (body["data"] as? Map<*, *>)?.let {
            @Suppress("UNCHECKED_CAST")
            it as Map<String, Any?>
        } ?: body

        val moreInfo = root["more_info"] as? Map<*, *>
        val id = (root["id"] ?: root["albumid"])?.toString()?.takeIf { it.isNotBlank() } ?: fallback.id
        val title = decodeHtmlEntities(
            (root["name"] ?: root["title"] ?: root["album"])?.toString()?.takeIf { it.isNotBlank() } ?: fallback.title
        )

        val rawArtists = root["artists"]
            ?: root["primary_artists"]
            ?: root["music"]
            ?: moreInfo?.get("artistMap")
            ?: moreInfo?.get("music")
            ?: root["description"]
        val artist = decodeHtmlEntities(
            extractArtistString(rawArtists)
                ?: parseSaavnDevArtists(rawArtists).takeIf { it != "Unknown Artist" }
                ?: fallback.artist
        )

        val year = (root["year"]
            ?: moreInfo?.get("year")
            ?: moreInfo?.get("release_date")?.toString()?.take(4))
            ?.toString()?.takeIf { it.isNotBlank() && it != "null" && it != "0" }
            ?: fallback.year

        val artwork = parseSaavnDevArtwork(root["image"] ?: moreInfo?.get("image"))?.let(::upgradeArtworkQuality)
            ?: fallback.artworkUri
        val externalUrl = (root["url"] ?: root["perma_url"])?.toString()?.takeIf { it.isNotBlank() } ?: fallback.externalUrl
        val language = (root["language"] ?: moreInfo?.get("language"))?.toString()?.takeIf { it.isNotBlank() } ?: fallback.language
        val movieName = extractMovieNameIfSoundtrack(root, moreInfo, title) ?: fallback.movieName

        val rawSongList = ((root["songs"] ?: root["list"] ?: root["tracks"]) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            .orEmpty()

        // Preserve album track order and deduplicate repeated tracks strictly
        val dedupedTracks = LinkedHashMap<String, Song>()
        for ((index, songMap) in rawSongList.withIndex()) {
            val parsed = parseSongFromMap(
                item = songMap,
                fallbackAlbumId = id,
                fallbackAlbumTitle = title,
                fallbackYear = year,
                fallbackArtwork = artwork
            ).copy(trackNumber = index + 1)
            val dedupKey = parsed.filePath.takeIf { it.isNotBlank() && it != "0" }
                ?: "${parsed.title.lowercase().trim()}|${parsed.artist.lowercase().trim()}|${parsed.durationMs}"
            if (!dedupedTracks.containsKey(dedupKey)) {
                dedupedTracks[dedupKey] = parsed
            }
        }
        val tracks = dedupedTracks.values.toList()

        val declaredCountRaw = root["songCount"] ?: root["song_count"] ?: root["list_count"] ?: moreInfo?.get("song_count")
        val declaredCount = when (declaredCountRaw) {
            is Number -> declaredCountRaw.toInt().takeIf { it > 0 }
            is String -> declaredCountRaw.toIntOrNull()?.takeIf { it > 0 }
            else -> fallback.songCount
        }

        val totalCount = maxOf(tracks.size, declaredCount ?: tracks.size)
        val isComplete = tracks.isNotEmpty() && (declaredCount == null || tracks.size >= declaredCount)

        return AlbumDetails(
            id = id,
            title = title,
            movieName = movieName,
            artist = artist,
            year = year,
            songCount = totalCount,
            artworkUri = artwork,
            sourceProvider = "JioSaavn",
            externalUrl = externalUrl,
            language = language,
            isComplete = isComplete,
            tracks = tracks,
            discoveredCount = tracks.size,
            officialExpectedCount = declaredCount,
            contributingAlbumsCount = 1,
            contributingProviders = listOf("JioSaavn"),
            isSoundtrackCollection = false
        )
    }

    private fun parseArtistResultFromMap(item: Map<String, Any?>): OnlineArtistResult? {
        val id = (item["id"] ?: item["artistId"])?.toString()?.trim().orEmpty()
        val rawName = (item["name"] ?: item["title"])?.toString()?.trim().orEmpty()
        if (rawName.isBlank()) return null
        val name = decodeHtmlEntities(rawName)
        val artwork = parseSaavnDevArtwork(item["image"])?.let(::upgradeArtworkQuality)
        val subtitle = (item["role"] ?: item["description"] ?: item["type"])?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let(::decodeHtmlEntities)
        val dominantLang = (item["dominantLanguage"] ?: item["language"])?.toString()?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.replaceFirstChar { it.uppercase() }
        return OnlineArtistResult(
            id = id.ifBlank { name },
            name = name,
            artworkUri = artwork,
            subtitle = subtitle,
            sourceProvider = "JioSaavn",
            languages = listOfNotNull(dominantLang)
        )
    }

    private fun parseArtistDetailsFromResponse(
        body: Map<String, Any?>?,
        fallback: OnlineArtistResult
    ): ArtistDetailsData? {
        if (body == null) return null
        val root = (body["data"] as? Map<*, *>)?.let {
            @Suppress("UNCHECKED_CAST")
            it as Map<String, Any?>
        } ?: body

        val id = (root["artistId"] ?: root["id"])?.toString()?.takeIf { it.isNotBlank() } ?: fallback.id
        val name = decodeHtmlEntities(
            (root["name"] ?: root["title"])?.toString()?.takeIf { it.isNotBlank() } ?: fallback.name
        )
        val artwork = parseSaavnDevArtwork(root["image"])?.let(::upgradeArtworkQuality) ?: fallback.artworkUri
        val subtitle = (root["dominantType"] ?: root["subtitle"] ?: root["role"])?.toString()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let(::decodeHtmlEntities)
            ?: fallback.subtitle

        val langFromRoot = (root["dominantLanguage"] ?: root["language"])?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.replaceFirstChar { it.uppercase() }
        val mergedLanguages = (fallback.languages + listOfNotNull(langFromRoot)).distinct()

        val rawTopSongs = ((root["topSongs"] ?: root["songs"] ?: (root["top_songs"] as? Map<*, *>)?.get("songs")) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            .orEmpty()
        val topSongs = ArtistMatchingPolicy.deduplicateSongs(
            rawTopSongs.map { parseSongFromMap(it, fallbackArtwork = artwork) }
        )

        val rawLatest = ((root["latest_release"] ?: root["latestRelease"] ?: root["singles"]) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            .orEmpty()
        val latestFromList = rawLatest.mapNotNull { entry ->
            val type = entry["type"]?.toString()?.lowercase()
            if (type == "album" && entry["songs"] == null && entry["encrypted_media_url"] == null && entry["more_info"] == null) {
                null
            } else {
                parseSongFromMap(entry, fallbackArtwork = artwork)
            }
        }

        val rawTopAlbums = ((root["topAlbums"] ?: root["albums"] ?: (root["top_albums"] as? Map<*, *>)?.get("albums")) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            .orEmpty()
        val rawSingleAlbums = rawLatest.filter {
            it["type"]?.toString()?.equals("album", ignoreCase = true) == true
        }
        val albums = ArtistMatchingPolicy.deduplicateAlbums(
            (rawTopAlbums + rawSingleAlbums).mapNotNull { parseAlbumResultFromMap(it) }
        )

        val rawDedicatedPlaylists = ((root["dedicated_artist_playlist"] ?: root["dedicatedArtistPlaylist"]) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            .orEmpty()
        val rawFeaturedPlaylists = ((root["featured_artist_playlist"] ?: root["featuredArtistPlaylist"]) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            .orEmpty()
        val playlists = (rawDedicatedPlaylists + rawFeaturedPlaylists)
            .mapNotNull { parsePlaylistResultFromMap(it) }
            .distinctBy { it.id.ifBlank { it.title.lowercase() } }

        return ArtistDetailsData(
            id = id,
            name = name,
            artworkUri = artwork,
            subtitle = subtitle,
            languages = mergedLanguages,
            sourceProvider = "JioSaavn",
            popularSongs = topSongs,
            latestReleases = ArtistMatchingPolicy.deduplicateSongs(latestFromList),
            albums = albums,
            playlists = playlists
        )
    }

    private fun extractPlaylistListFromResponse(body: Map<String, Any?>?): List<Map<String, Any?>> {
        if (body == null) return emptyList()
        val data = body["data"]
        when (data) {
            is Map<*, *> -> {
                val results = (data["results"] ?: data["playlists"]) as? List<*>
                if (!results.isNullOrEmpty()) return results.filterIsInstance<Map<String, Any?>>()
            }
            is List<*> -> {
                val maps = data.filterIsInstance<Map<String, Any?>>()
                if (maps.isNotEmpty()) return maps
            }
        }
        val rootResults = (body["results"] ?: body["playlists"]) as? List<*>
        if (!rootResults.isNullOrEmpty()) return rootResults.filterIsInstance<Map<String, Any?>>()
        return emptyList()
    }

    private fun parsePlaylistResultFromMap(item: Map<String, Any?>): CuratedPlaylist? {
        val id = (item["id"] ?: item["listid"])?.toString()?.trim().orEmpty()
        val rawTitle = (item["name"] ?: item["title"] ?: item["listname"])?.toString()?.trim().orEmpty()
        if (id.isBlank() || rawTitle.isBlank()) return null
        val moreInfo = item["more_info"] as? Map<*, *>
        val countRaw = item["songCount"] ?: item["song_count"] ?: item["list_count"] ?: moreInfo?.get("song_count")
        val countText = countRaw?.toString()?.toIntOrNull()?.let { "$it tracks" }
        val descRaw = (item["subtitle"] ?: item["description"] ?: countText ?: "Curated Playlist")?.toString().orEmpty()
        val cleanDesc = decodeHtmlEntities(descRaw)
            .replace(Regex("(?i)\\b(jio)?saavn\\b"), "")
            .trim(' ', '•', '-')
            .ifBlank { countText ?: "Curated Playlist" }
        val artwork = parseSaavnDevArtwork(item["image"] ?: moreInfo?.get("image"))?.let(::upgradeArtworkQuality)
        val rawSongs = (item["songs"] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()
            .map { parseSongFromMap(it) }
        return CuratedPlaylist(
            id = id,
            title = decodeHtmlEntities(rawTitle),
            description = cleanDesc,
            coverArtUri = artwork,
            songs = rawSongs
        )
    }

    private fun parsePlaylistDetailsFromResponse(
        body: Map<String, Any?>?,
        fallbackId: String,
        fallbackTitle: String,
        fallbackDescription: String,
        fallbackArtwork: String?
    ): CuratedPlaylist? {
        if (body == null) return null
        val root = (body["data"] as? Map<*, *>)?.let {
            @Suppress("UNCHECKED_CAST")
            it as Map<String, Any?>
        } ?: body
        val id = (root["id"] ?: root["listid"])?.toString()?.takeIf { it.isNotBlank() } ?: fallbackId
        val title = decodeHtmlEntities(
            (root["name"] ?: root["title"] ?: root["listname"])?.toString()?.takeIf { it.isNotBlank() } ?: fallbackTitle
        )
        val desc = decodeHtmlEntities(
            (root["description"] ?: root["subtitle"])?.toString()?.takeIf { it.isNotBlank() } ?: fallbackDescription
        ).replace(Regex("(?i)\\b(jio)?saavn\\b"), "").trim(' ', '•', '-').ifBlank { fallbackDescription }
        val artwork = parseSaavnDevArtwork(root["image"])?.let(::upgradeArtworkQuality) ?: fallbackArtwork
        val rawSongs = ((root["songs"] ?: root["list"]) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            .orEmpty()
        val songs = rawSongs.map { parseSongFromMap(it, fallbackArtwork = artwork) }
            .distinctBy { it.filePath.takeIf { fp -> fp.isNotBlank() && fp != "0" } ?: "${it.title.lowercase()}|${it.artist.lowercase()}" }
        return CuratedPlaylist(
            id = id,
            title = title,
            description = desc,
            coverArtUri = artwork ?: songs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
            songs = songs
        )
    }

    private fun extractArtistMapNames(artistMap: Map<*, *>): String? {
        val buckets = listOf("primary_artists", "music", "artists", "singers", "featured_artists")
        val names = linkedSetOf<String>()
        for (bucket in buckets) {
            val list = artistMap[bucket] as? List<*> ?: continue
            for (entry in list) {
                if (entry is Map<*, *>) {
                    val name = entry["name"]?.toString()?.trim()
                    if (!name.isNullOrBlank()) names.add(decodeHtmlEntities(name))
                }
            }
            if (names.isNotEmpty() && bucket == "primary_artists") break
        }
        return if (names.isNotEmpty()) names.joinToString(", ") else null
    }

    private fun extractMovieNameIfSoundtrack(
        root: Map<String, Any?>,
        moreInfo: Map<*, *>?,
        albumTitle: String
    ): String? {
        val explicitMovie = (root["movie"] ?: moreInfo?.get("movie"))?.toString()?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
        if (explicitMovie != null) return decodeHtmlEntities(explicitMovie)

        val isMovieFlag = (moreInfo?.get("is_movie") ?: root["is_movie"])?.toString()?.trim()
        val subtitle = (root["subtitle"] ?: root["description"] ?: moreInfo?.get("album_type"))?.toString().orEmpty()
        val fromParenMatch = Regex("""\((?:From|from)\s+["']?([^"')]+)["']?\)""").find(albumTitle)?.groupValues?.getOrNull(1)?.trim()
        if (!fromParenMatch.isNullOrBlank()) return fromParenMatch

        val fromDashMatch = Regex("""[-–]\s*(?:From|from)\s+["']?([^"'(]+)["']?\s*$""").find(albumTitle)?.groupValues?.getOrNull(1)?.trim()
        if (!fromDashMatch.isNullOrBlank()) return fromDashMatch

        val soundtrackRegex = Regex(
            """\s*[-–]?\s*\(?(?:Original Motion Picture Soundtrack|Motion Picture Soundtrack|Original Soundtrack|Soundtrack|OST)\)?\s*$""",
            RegexOption.IGNORE_CASE
        )
        if (soundtrackRegex.containsMatchIn(albumTitle)) {
            return albumTitle.replace(soundtrackRegex, "").trim().takeIf { it.isNotBlank() }
        }

        if (isMovieFlag == "1" || isMovieFlag.equals("true", ignoreCase = true) || subtitle.contains("movie", ignoreCase = true)) {
            return albumTitle.trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun upgradeArtworkQuality(url: String): String {
        return url.replace("150x150", "500x500")
            .replace("50x50", "500x500")
            .replace("100x100", "500x500")
            .replace("http://", "https://")
    }

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    fun getFallbackQualityUrl(currentUrl: String): String? {
        return when {
            currentUrl.contains("_320.mp4") -> currentUrl.replace("_320.mp4", "_160.mp4")
            currentUrl.contains("_160.mp4") -> currentUrl.replace("_160.mp4", "_96.mp4")
            currentUrl.contains("_96.mp4") -> currentUrl.replace("_96.mp4", "_48.mp4")
            currentUrl.startsWith("http://") -> currentUrl.replace("http://", "https://")
            else -> null
        }
    }
}

