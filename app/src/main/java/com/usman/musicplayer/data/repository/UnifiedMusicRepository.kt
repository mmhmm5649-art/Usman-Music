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

    private val searchCache = ConcurrentHashMap<String, List<Song>>()

    init {
        ExtensionManager.init(context)
        ProviderRegistry.init(context)
    }

    suspend fun searchAll(query: String, limit: Int = 35, offset: Int = 0): List<Song> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@coroutineScope emptyList()

        val cacheKey = "${trimmed.lowercase()}_${limit}_$offset"
        searchCache[cacheKey]?.let {
            Log.d("SearchTiming", "Cache hit for query '$trimmed' (offset $offset)")
            return@coroutineScope it
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

        val sortedResults = allResults
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Song> { it.playbackType == PlaybackType.FULL }
                    .thenByDescending { it.isFullTrackVerified }
                    .thenByDescending { !it.albumArtUri.isNullOrBlank() }
            )
            .let { list ->
                if (offset > 0 && offset < list.size) {
                    list.drop(offset)
                } else {
                    list
                }
            }
            .take(limit)

        if (sortedResults.isNotEmpty()) {
            searchCache[cacheKey] = sortedResults
        }

        sortedResults
    }

    suspend fun getStreamUrl(song: Song): Result<String> {
        return ProviderRegistry.getStreamUrl(song)
    }
}
