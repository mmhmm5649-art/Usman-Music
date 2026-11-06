package com.usman.musicplayer.data.repository.providers

import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Legitimate open-music provider connecting to ccMixter.org query API.
 * Streams full-length Creative Commons music and remixes.
 */
class CcMixterProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MusicProvider {

    override val metadata = ProviderMetadata(
        id = "ccmixter_provider",
        name = "ccMixter",
        version = "1.0.0",
        isFullPlaybackSupported = true,
        authRequired = false
    )

    private val BASE_URL = "https://ccmixter.org/api/query"

    override suspend fun search(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        try {
            val encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
            // Search via query parameter with JSON output format and standard headers
            val url = "$BASE_URL?search=$encodedQuery&f=json&limit=$limit"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) USMAN_MUSIC_PLAYER/1.0")
                .header("Referer", "https://ccmixter.org/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("ccMixter HTTP ${response.code}"))
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank() || !body.trim().startsWith("[")) {
                return@withContext Result.success(emptyList())
            }

            val jsonArray = JSONArray(body)
            val songs = mutableListOf<Song>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val song = mapJsonToSong(item)
                if (song != null) {
                    songs.add(song)
                }
            }

            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> {
        return if (!song.contentUriString.isNullOrBlank()) {
            Result.success(song.contentUriString)
        } else {
            Result.failure(Exception("Stream not available for ${song.title}"))
        }
    }

    private fun mapJsonToSong(item: JSONObject): Song? {
        val title = item.optString("upload_name").takeIf { it.isNotBlank() } ?: return null
        val artist = item.optString("user_name").takeIf { it.isNotBlank() } ?: "ccMixter Artist"
        val uploadId = item.optLong("upload_id", abs("${title}_${artist}".hashCode().toLong()))
        val licenseName = item.optString("license_name", "Creative Commons")
        val licenseUrl = item.optString("license_url", "https://creativecommons.org/")
        val pageUrl = item.optString("file_page_url", "https://ccmixter.org/")

        // Extract MP3 file download/stream URL
        val files = item.optJSONArray("files") ?: return null
        var mp3Url: String? = null
        var durationMs: Long = 180_000L

        for (j in 0 until files.length()) {
            val fileObj = files.optJSONObject(j) ?: continue
            val formatInfo = fileObj.optJSONObject("file_format_info")
            val defaultExt = formatInfo?.optString("default-ext") ?: ""
            if (defaultExt.equals("mp3", ignoreCase = true)) {
                mp3Url = fileObj.optString("download_url")
                val ps = formatInfo?.optString("ps")
                if (!ps.isNullOrBlank()) {
                    durationMs = parsePlaybackSeconds(ps)
                }
                break
            }
        }

        if (mp3Url.isNullOrBlank()) return null

        return Song(
            id = uploadId,
            title = title,
            artist = artist,
            album = "ccMixter",
            durationMs = durationMs,
            filePath = mp3Url,
            folderName = "ccMixter Discovery",
            albumArtUri = null,
            contentUriString = mp3Url,
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            isFavorite = false,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            externalUrl = pageUrl,
            genre = "Creative Commons",
            sourceProvider = "ccMixter",
            license = licenseName,
            attribution = artist,
            isStreamable = true,
            isFullTrackVerified = true,
            isRightsVerified = true,
            playbackRestrictions = null
        )
    }

    private fun parsePlaybackSeconds(ps: String): Long {
        return try {
            val parts = ps.trim().split(":")
            if (parts.size == 2) {
                val mins = parts[0].toLong()
                val secs = parts[1].toLong()
                ((mins * 60) + secs) * 1000L
            } else if (parts.size == 3) {
                val hrs = parts[0].toLong()
                val mins = parts[1].toLong()
                val secs = parts[2].toLong()
                ((hrs * 3600) + (mins * 60) + secs) * 1000L
            } else {
                180_000L
            }
        } catch (e: Exception) {
            180_000L
        }
    }
}
