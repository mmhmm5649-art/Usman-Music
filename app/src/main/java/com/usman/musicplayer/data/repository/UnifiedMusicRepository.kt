package com.usman.musicplayer.data.repository

import android.content.Context
import android.util.Log
import com.usman.musicplayer.extension.ExtensionManager
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderRegistry
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class UnifiedMusicRepository(context: Context) {

    private data class CachedResult(val songs: List<Song>, val timestamp: Long)
    private val searchCache = ConcurrentHashMap<String, CachedResult>()
    private val CACHE_TTL_MS = 30_000L // 30 seconds TTL for fresh online release searches

    init {
        ExtensionManager.init(context)
        ProviderRegistry.init(context)
    }

    suspend fun searchAll(query: String, limit: Int = 35, offset: Int = 0): List<Song> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@coroutineScope emptyList()

        val cacheKey = "${trimmed.lowercase()}_${limit}_$offset"
        val cached = searchCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS && cached.songs.isNotEmpty()) {
            Log.d("SearchTiming", "Cache hit for query '$trimmed' (offset $offset)")
            return@coroutineScope cached.songs
        }

        val active = ProviderRegistry.activeProviders
        val deferredList = active.map { provider ->
            async {
                val startTime = System.currentTimeMillis()
                val results = runCatching {
                    withTimeoutOrNull(5000L) {
                        provider.search(trimmed, limit)
                    }?.getOrNull() ?: emptyList()
                }.getOrElse { e ->
                    Log.w("UnifiedMusicRepo", "Provider ${provider.metadata.name} search failed: ${e.message}")
                    emptyList()
                }
                val elapsed = System.currentTimeMillis() - startTime
                Log.d("SearchTiming", "Provider ${provider.metadata.name} took ${elapsed}ms for query '$trimmed' (found ${results.size} songs)")
                results
            }
        }

        val allResults = deferredList.awaitAll().flatten()

        val queryTokens = trimmed.lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

        val sortedResults = allResults
            .distinctBy { it.id }
            .map { song ->
                val score = calculateRelevanceScore(song, queryTokens)
                song to score
            }
            .filter { (song, score) ->
                // Filter out unrelated archive or broad matches that have zero word overlap with specific queries
                if (queryTokens.isNotEmpty() && song.sourceProvider.equals("Internet Archive", ignoreCase = true)) {
                    score >= 40
                } else {
                    true
                }
            }
            .sortedWith(
                compareByDescending<Pair<Song, Int>> { it.second } // Relevance score descending
                    .thenByDescending { it.first.playbackType == PlaybackType.FULL }
                    .thenByDescending { it.first.isFullTrackVerified }
                    .thenByDescending { !it.first.albumArtUri.isNullOrBlank() }
            )
            .map { it.first }
            .let { list ->
                if (offset > 0 && offset < list.size) {
                    list.drop(offset)
                } else {
                    list
                }
            }
            .take(limit)

        if (sortedResults.isNotEmpty()) {
            searchCache[cacheKey] = CachedResult(sortedResults, System.currentTimeMillis())
        } else {
            searchCache.remove(cacheKey)
        }

        sortedResults
    }

    private fun calculateRelevanceScore(song: Song, queryTokens: List<String>): Int {
        val title = song.title.lowercase()
        val artist = song.artist.lowercase()
        if (queryTokens.isEmpty()) return 0

        var score = 0
        val fullQuery = queryTokens.joinToString(" ")

        if (title == fullQuery) {
            score += 300
        } else if (title.contains(fullQuery)) {
            score += 150
        }

        for (token in queryTokens) {
            if (title.contains(token)) {
                score += 50
            }
            if (artist.contains(token)) {
                score += 40
            }
        }

        return score
    }

    suspend fun getStreamUrl(song: Song): Result<String> {
        return ProviderRegistry.getStreamUrl(song)
    }
}
