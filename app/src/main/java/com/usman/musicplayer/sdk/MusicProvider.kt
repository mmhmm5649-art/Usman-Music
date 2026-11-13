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

data class AlbumSearchResult(
    val id: String,
    val title: String,
    val movieName: String? = null,
    val artist: String,
    val year: String? = null,
    val songCount: Int? = null,
    val artworkUri: String? = null,
    val sourceProvider: String,
    val externalUrl: String? = null,
    val language: String? = null,
    val isCompleteAlbum: Boolean = false,
    val isSoundtrackCollection: Boolean = false,
    val constituentAlbums: List<AlbumSearchResult> = emptyList()
)

data class AlbumDetails(
    val id: String,
    val title: String,
    val movieName: String? = null,
    val artist: String,
    val year: String? = null,
    val songCount: Int,
    val artworkUri: String? = null,
    val sourceProvider: String,
    val externalUrl: String? = null,
    val language: String? = null,
    val isComplete: Boolean = true,
    val tracks: List<Song> = emptyList(),
    val discoveredCount: Int = tracks.size,
    val officialExpectedCount: Int? = null,
    val contributingAlbumsCount: Int = 1,
    val contributingProviders: List<String> = listOf(sourceProvider),
    val isSoundtrackCollection: Boolean = false
)

interface MusicProvider {
    val metadata: ProviderMetadata
    
    suspend fun search(query: String, limit: Int = 30): Result<List<Song>>
    suspend fun searchPaginated(query: String, limit: Int = 30, page: Int = 1): Result<List<Song>> =
        if (page == 1) search(query, limit) else Result.success(emptyList())
    suspend fun getStreamUrl(song: Song): Result<String>
    suspend fun getLyrics(song: Song): Result<String?> = Result.success(null)

    // Optional album search & complete album details retrieval
    suspend fun searchAlbums(query: String, limit: Int = 20): Result<List<AlbumSearchResult>> = Result.success(emptyList())
    suspend fun searchAlbumsPaginated(query: String, limit: Int = 20, page: Int = 1): Result<List<AlbumSearchResult>> =
        if (page == 1) searchAlbums(query, limit) else Result.success(emptyList())
    suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> = Result.success(null)
    
    // Optional settings for Echo extensions
    suspend fun getSettingItems(): List<dev.brahmkshatriya.echo.common.settings.Setting> = emptyList()
    fun updateSetting(key: String, value: Any) {}
}
