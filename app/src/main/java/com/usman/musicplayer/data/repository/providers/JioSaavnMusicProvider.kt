package com.usman.musicplayer.data.repository.providers

import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.usman.musicplayer.data.remote.JioSaavnApiService
import com.usman.musicplayer.data.remote.JioSaavnSong
import com.usman.musicplayer.extension.JioSaavnDecryptor
import com.usman.musicplayer.playback.PlaybackErrorRepository
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
        val PUBLIC_API_BASES = listOf(
            "https://saavn-api-tau.vercel.app",
            "https://jiosaavn-api-sage.vercel.app",
            "https://saavn.dev"
        )
    }

    override val metadata = ProviderMetadata(
        id = "jiosaavn_native_provider",
        name = "JioSaavn",
        version = "2.1.0",
        isFullPlaybackSupported = true,
        authRequired = false
    )

    override suspend fun search(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        Log.d(TAG, "JioSaavn search requested for query: '$trimmed'")

        // Strategy 1: Try public REST API (saavn.dev / sumitkolhe API instances)
        for (baseUrl in publicApiBases) {
            try {
                val encodedQuery = Uri.encode(trimmed)
                val url = "$baseUrl/api/search/songs?query=$encodedQuery&limit=$limit"
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
                            val songs = songMaps.map { parseSongFromMap(it) }
                            Log.d(TAG, "[Search Strategy 1] Succeeded via $baseUrl, returned ${songs.size} tracks")
                            return@withContext Result.success(songs)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[Search Strategy 1] Error from $baseUrl: ${e.message}")
            }
        }

        // Strategy 2: Official JioSaavn API fallback
        try {
            Log.d(TAG, "[Search Strategy 2] Falling back to official JioSaavn API")
            val response = apiService.searchSongs(query = trimmed, limit = limit)
            if (response.isSuccessful) {
                val results = response.body()?.results ?: emptyList()
                val songs = results.map { it.toSong() }
                Log.d(TAG, "[Search Strategy 2] Succeeded, returned ${songs.size} tracks")
                Result.success(songs)
            } else {
                Log.e(TAG, "JioSaavn search failed with HTTP code: ${response.code()}")
                Result.failure(IOException("JioSaavn API search failed: ${response.code()}"))
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
                Result.failure(IOException("JioSaavn API details failed: ${response.code()}"))
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
        val titleName = parseTitle(this)
        val artistName = parseArtists(this)
        val albumName = parseAlbum(this)
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
            sourceProvider = "JioSaavn",
            attribution = "$artistName • JioSaavn",
            isStreamable = true,
            isFullTrackVerified = true,
            isRightsVerified = true
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
            ?: song.moreInfo?.get("primary_artists") 
            ?: song.moreInfo?.get("singers") 
            ?: song.moreInfo?.get("artist")
            ?: song.moreInfo?.get("music")
        return extractArtistString(raw) ?: "Unknown Artist"
    }

    private fun extractArtistString(value: Any?): String? {
        when (value) {
            is String -> {
                if (value.isNotBlank()) return value.trim()
            }
            is List<*> -> {
                val names = value.mapNotNull { item ->
                    when (item) {
                        is String -> item.takeIf { it.isNotBlank() }
                        is Map<*, *> -> (item["name"] ?: item["title"] ?: item["artist"])?.toString()?.takeIf { it.isNotBlank() }
                        else -> item?.toString()?.takeIf { it.isNotBlank() }
                    }
                }
                if (names.isNotEmpty()) return names.joinToString(", ")
            }
            is Map<*, *> -> {
                val name = (value["name"] ?: value["title"] ?: value["artist"])?.toString()
                if (!name.isNullOrBlank()) return name.trim()
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

    private fun parseSongFromMap(item: Map<String, Any?>): Song {
        val idStr = (item["id"] ?: item["song_id"] ?: item["pids"])?.toString() ?: "0"
        val songIdLong = try {
            idStr.toLong()
        } catch (e: Exception) {
            abs(idStr.hashCode().toLong())
        }

        val title = (item["name"] ?: item["title"] ?: item["song"])?.toString()?.takeIf { !it.isNullOrBlank() } ?: "Unknown Track"
        
        val artistsObj = item["artists"] ?: item["primary_artists"] ?: item["singers"] ?: item["artist"]
        val artist = parseSaavnDevArtists(artistsObj)

        val albumObj = item["album"]
        val album = parseSaavnDevAlbum(albumObj)

        val imageObj = item["image"] ?: item["image_url"]
        val artwork = parseSaavnDevArtwork(imageObj)

        val downloadUrlObj = item["downloadUrl"] ?: item["download_url"] ?: item["media_url"] ?: item["encrypted_media_url"]
        val streamUrl = extractHighestQualityUrl(downloadUrlObj)

        val durationObj = item["duration"]
        val durationMs = when (durationObj) {
            is Number -> durationObj.toLong() * 1000L
            is String -> (durationObj.toLongOrNull() ?: 240L) * 1000L
            else -> 240000L
        }

        val externalUrl = (item["url"] ?: item["perma_url"])?.toString()

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
            sourceProvider = "JioSaavn",
            attribution = "$artist • JioSaavn",
            isStreamable = true,
            isFullTrackVerified = true,
            isRightsVerified = true
        )
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

