package com.usman.musicplayer.data.repository.providers

import android.util.Log
import com.usman.musicplayer.data.remote.LrcLibApiService
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LrcLibMusicProvider(
    private val apiService: LrcLibApiService
) : MusicProvider {

    companion object {
        private const val TAG = "USMAN_LYRICS"
    }

    private fun safeLogD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (e: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun safeLogW(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (e: Throwable) {
            println("[$TAG] WARN: $msg")
        }
    }

    private fun safeLogE(msg: String, tr: Throwable? = null) {
        try {
            Log.e(TAG, msg, tr)
        } catch (e: Throwable) {
            println("[$TAG] ERROR: $msg ($tr)")
        }
    }

    override val metadata = ProviderMetadata(
        id = "lrclib_lyrics_provider",
        name = "LRCLIB Lyrics",
        version = "2.0.0",
        isFullPlaybackSupported = false,
        authRequired = false
    )

    override suspend fun search(query: String, limit: Int): Result<List<Song>> = Result.success(emptyList())

    override suspend fun getStreamUrl(song: Song): Result<String> = Result.failure(Exception("LRCLIB provides lyrics only"))

    override suspend fun getLyrics(song: Song): Result<String?> = withContext(Dispatchers.IO) {
        val title = song.title.trim()
        val artist = song.artist.trim()
        val album = song.album.trim().takeIf { it.isNotBlank() && it != "Unknown Album" && it != "Single" }
        val durationSec = (song.durationMs / 1000).toInt()

        if (title.isBlank()) {
            safeLogW("[LrcLibMusicProvider] Song title is blank. Cannot query LRCLIB.")
            return@withContext Result.success(null)
        }

        safeLogD("=== LRCLIB LIVE REQUEST TRACE ===")
        safeLogD("Song Title : '$title'")
        safeLogD("Artist     : '$artist'")
        safeLogD("Album      : '${album ?: "None"}'")
        safeLogD("Duration   : ${durationSec}s (${song.durationMs}ms)")

        val artistsToTry = mutableListOf(artist)
        if (artist.contains(",")) {
            val primaryArtist = artist.substringBefore(",").trim()
            if (primaryArtist.isNotBlank()) {
                artistsToTry.add(primaryArtist)
            }
        }
        // Also try general search without artist if needed
        val cleanArtist = artist.substringBefore("feat").substringBefore("ft.").trim()
        if (cleanArtist.isNotBlank() && !artistsToTry.contains(cleanArtist)) {
            artistsToTry.add(cleanArtist)
        }

        try {
            // Stage 1 & 2: Try exact /api/get for each artist variation
            for (tryArtist in artistsToTry) {
                safeLogD("[LrcLibMusicProvider] Trying GET /api/get with artist='$tryArtist', track='$title', duration=$durationSec")
                val response = apiService.getLyrics(
                    trackName = title,
                    artistName = tryArtist,
                    albumName = album,
                    duration = durationSec.takeIf { it > 0 }
                )
                safeLogD("[LrcLibMusicProvider] GET /api/get HTTP status: ${response.code()}")
                if (response.isSuccessful) {
                    val track = response.body()
                    if (track != null) {
                        val synced = track.syncedLyrics
                        val plain = track.plainLyrics
                        val hasSynced = !synced.isNullOrBlank()
                        val hasPlain = !plain.isNullOrBlank()
                        safeLogD("[LrcLibMusicProvider] Found exact match: '${track.trackName}' by '${track.artistName}' | hasSynced=$hasSynced | hasPlain=$hasPlain")
                        val lyrics = if (hasSynced) synced else plain
                        if (!lyrics.isNullOrBlank()) {
                            return@withContext Result.success(lyrics)
                        }
                    }
                }
            }

            // Stage 3: Try /api/search with track_name and artist_name
            for (tryArtist in artistsToTry) {
                safeLogD("[LrcLibMusicProvider] Trying GET /api/search with track='$title', artist='$tryArtist'")
                val resp = apiService.searchLyrics(
                    trackName = title,
                    artistName = tryArtist,
                    duration = durationSec.takeIf { it > 0 }
                )
                safeLogD("[LrcLibMusicProvider] GET /api/search HTTP status: ${resp.code()}")
                if (resp.isSuccessful) {
                    val tracks = resp.body().orEmpty()
                    safeLogD("[LrcLibMusicProvider] Search returned ${tracks.size} results:")
                    for ((idx, t) in tracks.withIndex()) {
                        safeLogD("  [$idx] '${t.trackName}' by '${t.artistName}' (dur: ${t.duration}s, synced: ${!t.syncedLyrics.isNullOrBlank()})")
                    }

                    val bestMatch = tracks.find { !it.syncedLyrics.isNullOrBlank() }
                        ?: tracks.find { !it.plainLyrics.isNullOrBlank() }

                    if (bestMatch != null) {
                        val lyrics = bestMatch.syncedLyrics?.takeIf { it.isNotBlank() } ?: bestMatch.plainLyrics
                        safeLogD("[LrcLibMusicProvider] Selected search match: '${bestMatch.trackName}' by '${bestMatch.artistName}'")
                        if (!lyrics.isNullOrBlank()) {
                            return@withContext Result.success(lyrics)
                        }
                    }
                }
            }

            // Stage 4: Free text search query `q = "$title $artist"`
            val freeQuery = "$title $artist"
            safeLogD("[LrcLibMusicProvider] Trying free-text search GET /api/search?q='$freeQuery'")
            val freeResp = apiService.searchLyrics(query = freeQuery)
            safeLogD("[LrcLibMusicProvider] Free search HTTP status: ${freeResp.code()}")
            if (freeResp.isSuccessful) {
                val tracks = freeResp.body().orEmpty()
                safeLogD("[LrcLibMusicProvider] Free search returned ${tracks.size} results:")
                for ((idx, t) in tracks.withIndex()) {
                    safeLogD("  [$idx] '${t.trackName}' by '${t.artistName}' (dur: ${t.duration}s)")
                }
                val bestMatch = tracks.find { !it.syncedLyrics.isNullOrBlank() }
                    ?: tracks.find { !it.plainLyrics.isNullOrBlank() }
                if (bestMatch != null) {
                    val lyrics = bestMatch.syncedLyrics?.takeIf { it.isNotBlank() } ?: bestMatch.plainLyrics
                    safeLogD("[LrcLibMusicProvider] Selected free search match: '${bestMatch.trackName}' by '${bestMatch.artistName}'")
                    if (!lyrics.isNullOrBlank()) {
                        return@withContext Result.success(lyrics)
                    }
                }
            }

            safeLogW("[LrcLibMusicProvider] All LRCLIB query stages exhausted without finding lyrics for '$title' - '$artist'")
        } catch (e: Exception) {
            if (e is java.net.UnknownHostException || e is java.io.IOException || e is java.net.SocketTimeoutException) {
                safeLogW("[LrcLibMusicProvider] Network offline or unavailable during LRCLIB query: ${e.message}")
            } else {
                safeLogE("[LrcLibMusicProvider] Exception during LRCLIB query: ${e.message}", e)
            }
        }

        Result.success(null)
    }
}
