package com.usman.musicplayer.data.repository.providers

import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.usman.musicplayer.data.remote.JioSaavnApiService
import com.usman.musicplayer.data.remote.JioSaavnSong
import com.usman.musicplayer.extension.JioSaavnDecryptor
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
    private val moshi: Moshi
) : MusicProvider {

    private companion object {
        private const val TAG = "USMAN_PLAYBACK"
    }

    override val metadata = ProviderMetadata(
        id = "jiosaavn_native_provider",
        name = "JioSaavn",
        version = "2.0.0",
        isFullPlaybackSupported = true,
        authRequired = false
    )

    override suspend fun search(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        try {
            Log.d(TAG, "JioSaavn search requested for query: '$trimmed'")
            val response = apiService.searchSongs(query = trimmed, limit = limit)
            if (response.isSuccessful) {
                val results = response.body()?.results ?: emptyList()
                val songs = results.map { it.toSong() }
                Log.d(TAG, "JioSaavn search succeeded, returned ${songs.size} tracks")
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
            Log.d(TAG, "Resolving stream URL for song: '${song.title}' (ID: ${song.id}, filePath: ${song.filePath})")
            val cachedUrl = song.contentUriString
            if (!cachedUrl.isNullOrBlank()) {
                val sanitizedHost = runCatching { Uri.parse(cachedUrl).host }.getOrNull() ?: "unknown"
                Log.d(TAG, "Using cached stream URL for '${song.title}', host: $sanitizedHost")
                return@withContext Result.success(cachedUrl)
            }

            val songId = song.filePath ?: run {
                Log.e(TAG, "Missing song ID (filePath) for '${song.title}'")
                return@withContext Result.failure(Exception("Missing song ID for stream resolution"))
            }

            val response = apiService.getSongDetails(pids = songId)
            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("Empty response body"))
                }

                val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val rawBody = adapter.fromJson(responseBody)
                
                val songDetailsMap = extractSongDetailsFromResponse(rawBody, songId)

                val encryptedUrl = songDetailsMap?.get("encrypted_media_url") as? String
                    ?: songDetailsMap?.get("encryptedMediaUrl") as? String
                val mediaUrl = songDetailsMap?.get("media_url") as? String
                    ?: songDetailsMap?.get("mediaUrl") as? String

                Log.d(TAG, "[Stage 4] ID: ${song.id}, Title: '${song.title}', EncryptedUrlFound: ${encryptedUrl != null}, MediaUrlFound: ${mediaUrl != null}")

                val decryptedUrl = JioSaavnDecryptor.decryptUrl(encryptedUrl) ?: mediaUrl

                if (!decryptedUrl.isNullOrBlank()) {
                    val sanitizedHost = runCatching { Uri.parse(decryptedUrl).host }.getOrNull() ?: "unknown"
                    Log.d(TAG, "[Stage 4] Stream resolution success for '${song.title}', host: $sanitizedHost")
                    Result.success(decryptedUrl)
                } else {
                    Log.e(TAG, "Failed to decrypt or resolve stream URL for '${song.title}'. Encrypted: $encryptedUrl, Media: $mediaUrl")
                    Result.failure(Exception("Failed to decrypt stream URL for ${song.title}"))
                }
            } else {
                Log.e(TAG, "JioSaavn API details failed for '${song.title}' with code: ${response.code()}")
                Result.failure(IOException("JioSaavn API details failed: ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception during JioSaavn stream resolution for '${song.title}': ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(song: Song): Result<String?> = withContext(Dispatchers.IO) {
        val songId = song.filePath ?: return@withContext Result.success(null)
        try {
            val response = apiService.getLyrics(songId)
            if (response.isSuccessful) {
                Result.success(response.body()?.lyrics)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    private fun extractSongDetailsFromResponse(body: Map<String, Any>?, songId: String): Map<String, Any?>? {
        if (body == null) return null
        
        val direct = body[songId] as? Map<String, Any?>
        if (direct != null) return direct

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

        if (body["id"] != null || body["encrypted_media_url"] != null || body["encryptedMediaUrl"] != null) {
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

        return Song(
            id = songIdLong,
            title = titleName,
            artist = artistName,
            album = albumName,
            durationMs = durationMs,
            filePath = songIdStr,
            folderName = "JioSaavn",
            albumArtUri = artwork,
            contentUriString = null,
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
}
