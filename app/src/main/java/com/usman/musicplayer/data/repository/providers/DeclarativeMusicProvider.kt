package com.usman.musicplayer.data.repository.providers

import com.usman.musicplayer.extension.DeclarativeProviderSpec
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.CancellationException
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
 * Sandboxed, declarative music provider runtime.
 * Safely executes REST queries defined in imported JSON / ZIP specifications
 * without executing arbitrary executable binaries or DEX bytecode.
 */
class DeclarativeMusicProvider(
    val spec: DeclarativeProviderSpec,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : MusicProvider {

    companion object {
        const val MAX_RESPONSE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB response payload limit
    }

    override val metadata: ProviderMetadata = ProviderMetadata(
        id = spec.manifest.id,
        name = spec.manifest.name,
        version = spec.manifest.version,
        isFullPlaybackSupported = spec.manifest.isFullPlaybackSupported,
        authRequired = false
    )

    override suspend fun search(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        try {
            val config = spec.searchConfig
            val encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
            val resolvedUrl = config.urlTemplate
                .replace("{query}", encodedQuery)
                .replace("{limit}", limit.toString())

            // Security check: Enforce HTTPS for non-local endpoints
            val isLocalTest = resolvedUrl.startsWith("http://127.0.0.1") ||
                    resolvedUrl.startsWith("http://localhost") ||
                    resolvedUrl.startsWith("http://[::1]") ||
                    resolvedUrl.contains("127.0.0.1:") ||
                    resolvedUrl.contains("localhost:")
            if (!isLocalTest && !resolvedUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext Result.failure(
                    SecurityException("Insecure endpoint blocked (${spec.manifest.name}). Declarative extensions require HTTPS.")
                )
            }

            val reqBuilder = Request.Builder().url(resolvedUrl)

            // Inject custom user-specified headers safely
            var hasUserAgent = false
            config.headers.forEach { (k, v) ->
                if (k.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
                reqBuilder.header(k, v)
            }
            if (!hasUserAgent) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) USMAN_MUSIC_PLAYER/1.0")
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("${spec.manifest.name} HTTP ${response.code}"))
            }

            val responseBody = response.body
            val contentLength = responseBody?.contentLength() ?: 0L
            if (contentLength > MAX_RESPONSE_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalStateException("HTTP response payload size exceeds 5MB safety limit for extension ${spec.manifest.name}")
                )
            }

            // Stream response with byte limit safety
            val bytes = responseBody?.bytes() ?: byteArrayOf()
            if (bytes.size > MAX_RESPONSE_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalStateException("HTTP response payload size (${bytes.size} bytes) exceeds 5MB safety limit.")
                )
            }

            val body = String(bytes, StandardCharsets.UTF_8).trim()
            if (body.isBlank()) return@withContext Result.success(emptyList())

            val songs = parseTracks(body, limit)
            Result.success(songs)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> {
        if (!song.contentUriString.isNullOrBlank()) {
            return Result.success(song.contentUriString)
        }

        val template = spec.searchConfig.streamUrlTemplate
        if (!template.isNullOrBlank()) {
            val resolved = template.replace("{id}", song.id.toString())
            return Result.success(resolved)
        }

        return Result.failure(Exception("Direct stream URL not available for ${song.title}"))
    }

    private fun parseTracks(jsonStr: String, limit: Int): List<Song> {
        val config = spec.searchConfig
        val songs = mutableListOf<Song>()

        val trimmed = jsonStr.trim()
        val jsonArray: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val obj = JSONObject(trimmed)
            if (config.tracksJsonPath.isNotBlank()) {
                val pathSegments = config.tracksJsonPath.split(".")
                var currentObj: Any = obj
                for (seg in pathSegments) {
                    if (currentObj is JSONObject) {
                        currentObj = currentObj.opt(seg) ?: JSONObject()
                    }
                }
                when (currentObj) {
                    is JSONArray -> currentObj
                    is JSONObject -> {
                        // Check if it has a list of items
                        currentObj.optJSONArray("items") ?: currentObj.optJSONArray("docs") ?: JSONArray()
                    }
                    else -> JSONArray()
                }
            } else {
                obj.optJSONArray("results")
                    ?: obj.optJSONArray("tracks")
                    ?: obj.optJSONArray("data")
                    ?: obj.optJSONArray("items")
                    ?: JSONArray()
            }
        }

        val maxItems = minOf(jsonArray.length(), limit)
        for (i in 0 until maxItems) {
            val item = jsonArray.optJSONObject(i) ?: continue

            val title = item.optString(config.titleField).takeIf { it.isNotBlank() } ?: continue
            val artist = item.optString(config.artistField).takeIf { it.isNotBlank() } ?: spec.manifest.name
            val album = item.optString(config.albumField).takeIf { it.isNotBlank() } ?: spec.manifest.name
            val streamUrl = item.optString(config.streamUrlField).takeIf { it.isNotBlank() }
            val artworkUrl = item.optString(config.artworkUrlField).takeIf { it.isNotBlank() }

            val rawDuration = item.optLong(config.durationField, 180L)
            val durationMs = if (config.durationUnit.equals("seconds", ignoreCase = true)) {
                rawDuration * 1000L
            } else {
                rawDuration
            }

            val idStr = item.optString(config.idField)
            val id = if (idStr.isNotBlank()) {
                abs("${spec.manifest.id}_$idStr".hashCode().toLong())
            } else {
                abs("${spec.manifest.id}_${title}_$artist".hashCode().toLong())
            }

            val isStreamable = !streamUrl.isNullOrBlank() && spec.manifest.playbackType == PlaybackType.FULL

            songs.add(
                Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    filePath = streamUrl ?: "",
                    folderName = spec.manifest.name,
                    albumArtUri = artworkUrl,
                    contentUriString = streamUrl,
                    mimeType = "audio/mpeg",
                    sizeBytes = 0L,
                    isFavorite = false,
                    isOnline = true,
                    playbackType = spec.manifest.playbackType,
                    externalUrl = streamUrl,
                    genre = "Community Extension",
                    sourceProvider = spec.manifest.name,
                    license = spec.manifest.license,
                    attribution = "$artist • ${spec.manifest.name}",
                    isStreamable = isStreamable,
                    isFullTrackVerified = isStreamable,
                    isRightsVerified = true,
                    playbackRestrictions = if (!isStreamable) "Requires external player" else null
                )
            )
        }

        return songs
    }
}
