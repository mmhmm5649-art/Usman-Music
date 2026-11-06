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

class UnifiedMusicRepository(context: Context) {

    init {
        ExtensionManager.init(context)
        ProviderRegistry.init(context)
    }

    suspend fun searchAll(query: String, limit: Int = 35): List<Song> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@coroutineScope emptyList()

        val active = ProviderRegistry.activeProviders
        val deferredList = active.map { provider ->
            async {
                runCatching {
                    withTimeoutOrNull(9000L) {
                        provider.search(trimmed, limit)
                    }?.getOrNull() ?: emptyList()
                }.getOrElse { e ->
                    Log.w("UnifiedMusicRepo", "Provider ${provider.metadata.name} search failed: ${e.message}")
                    emptyList()
                }
            }
        }

        val allResults = deferredList.awaitAll().flatten()

        // Prioritize full-length streamable tracks first, then external services, distinct by ID
        allResults
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Song> { it.playbackType == PlaybackType.FULL }
                    .thenByDescending { it.isFullTrackVerified }
                    .thenByDescending { !it.albumArtUri.isNullOrBlank() }
            )
            .take(limit)
    }

    suspend fun getStreamUrl(song: Song): Result<String> {
        return ProviderRegistry.getStreamUrl(song)
    }
}
