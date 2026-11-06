package com.usman.musicplayer.data.repository

import android.content.Context
import com.usman.musicplayer.sdk.Song
import com.usman.musicplayer.sdk.ProviderRegistry

class UnifiedMusicRepository(context: Context) {
    
    init {
        ProviderRegistry.init(context)
    }

    suspend fun searchAll(query: String, limit: Int = 30): List<Song> {
        val results = mutableListOf<Song>()
        for (provider in ProviderRegistry.providers) {
            provider.search(query, limit).onSuccess {
                results.addAll(it)
            }
        }
        return results.distinctBy { it.id }
    }
}
