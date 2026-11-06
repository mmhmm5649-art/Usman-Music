package com.usman.musicplayer.sdk

sealed class MusicSourceError : Exception() {
    object NetworkError : MusicSourceError()
    object Unavailable : MusicSourceError()
    data class AuthError(val errorMessage: String) : MusicSourceError()
    data class Unknown(val error: Throwable) : MusicSourceError()
}

data class ProviderMetadata(
    val id: String,
    val name: String,
    val version: String,
    val isFullPlaybackSupported: Boolean,
    val authRequired: Boolean
)

interface MusicProvider {
    val metadata: ProviderMetadata
    
    suspend fun search(query: String, limit: Int = 30): Result<List<Song>>
    suspend fun getStreamUrl(song: Song): Result<String>
    
    // Optional settings for Echo extensions
    suspend fun getSettingItems(): List<dev.brahmkshatriya.echo.common.settings.Setting> = emptyList()
    fun updateSetting(key: String, value: Any) {}
}
