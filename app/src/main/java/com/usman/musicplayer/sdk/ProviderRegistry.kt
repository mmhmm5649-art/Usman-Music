package com.usman.musicplayer.sdk

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.data.remote.AudiusApiService
import com.usman.musicplayer.data.repository.LocalMusicProvider
import com.usman.musicplayer.data.repository.providers.AudiusProvider
import com.usman.musicplayer.data.repository.providers.CcMixterProvider
import com.usman.musicplayer.data.repository.providers.InternetArchiveMusicProvider
import com.usman.musicplayer.data.remote.JioSaavnApiService
import com.usman.musicplayer.data.repository.providers.JioSaavnMusicProvider
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

object ProviderRegistry {
    private val _providers = CopyOnWriteArrayList<MusicProvider>()
    private val _enabledMap = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var internalSoundtrackVerifier: MusicProvider? = null

    val providers: List<MusicProvider> get() = _providers
    val activeProviders: List<MusicProvider> get() = _providers.filter { _enabledMap[it.metadata.id] != false }

    /**
     * Hidden internal-only metadata verifier for soundtrack tracklists/ordering.
     * Never exposed in user-facing provider lists, search results, or playback queues.
     */
    fun getInternalSoundtrackVerifier(): MusicProvider? = internalSoundtrackVerifier

    fun init(context: Context) {
        if (_providers.isNotEmpty()) return

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val moshiConverter = MoshiConverterFactory.create(moshi)

        val audiusApiService = Retrofit.Builder()
            .baseUrl("https://discoveryprovider.audius.co/v1/")
            .client(httpClient)
            .addConverterFactory(moshiConverter)
            .build()
            .create(AudiusApiService::class.java)

        val jioSaavnApiService = Retrofit.Builder()
            .baseUrl("https://www.jiosaavn.com/")
            .client(httpClient)
            .addConverterFactory(moshiConverter)
            .build()
            .create(JioSaavnApiService::class.java)

        val itunesApiService = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(httpClient)
            .addConverterFactory(moshiConverter)
            .build()
            .create(com.usman.musicplayer.data.remote.MusicApiService::class.java)

        val lrclibApiService = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(httpClient)
            .addConverterFactory(moshiConverter)
            .build()
            .create(com.usman.musicplayer.data.remote.LrcLibApiService::class.java)

        internalSoundtrackVerifier = com.usman.musicplayer.data.repository.providers.ITunesMusicProvider(itunesApiService)

        registerProvider(LocalMusicProvider(context), enabled = true)
        registerProvider(AudiusProvider(audiusApiService), enabled = true)
        registerProvider(CcMixterProvider(), enabled = true)
        registerProvider(InternetArchiveMusicProvider(), enabled = true)
        registerProvider(com.usman.musicplayer.data.repository.providers.LrcLibMusicProvider(lrclibApiService), enabled = true)
        registerProvider(JioSaavnMusicProvider(jioSaavnApiService, moshi), enabled = true)
    }

    fun registerProvider(provider: MusicProvider, enabled: Boolean = true) {
        if (provider.metadata.id == "itunes_catalog_provider" || provider.metadata.name.equals("iTunes", ignoreCase = true)) {
            internalSoundtrackVerifier = provider
            return
        }
        // Avoid duplicate registration
        _providers.removeAll { it.metadata.id == provider.metadata.id }
        _providers.add(provider)
        _enabledMap[provider.metadata.id] = enabled
    }

    fun unregisterProvider(providerId: String) {
        _providers.removeAll { it.metadata.id == providerId }
        _enabledMap.remove(providerId)
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        _enabledMap[providerId] = enabled
    }

    fun isProviderEnabled(providerId: String): Boolean {
        return _enabledMap[providerId] ?: true
    }

    suspend fun getStreamUrl(song: Song): Result<String> {
        // Reject preview-only or iTunes streams from resolution/playback
        if (song.isOnline && (song.playbackType == PlaybackType.PREVIEW || song.sourceProvider.equals("iTunes", ignoreCase = true))) {
            return Result.failure(Exception("Preview tracks are disabled; only full-length streams are supported."))
        }

        // Direct stream already present in song model
        if (!song.contentUriString.isNullOrBlank()) {
            return Result.success(song.contentUriString)
        }

        // Match provider by source name
        val matchingProvider = activeProviders.find {
            it.metadata.name.equals(song.sourceProvider, ignoreCase = true) ||
            it.metadata.id.equals(song.sourceProvider, ignoreCase = true)
        }

        if (matchingProvider != null) {
            return matchingProvider.getStreamUrl(song)
        }

        // Fallback: try active full-playback providers
        for (provider in activeProviders.filter { it.metadata.isFullPlaybackSupported }) {
            val result = provider.getStreamUrl(song)
            if (result.isSuccess) {
                return result
            }
        }

        return Result.failure(Exception("No streaming provider available for ${song.title}"))
    }

    suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> {
        if (album.sourceProvider.equals("iTunes", ignoreCase = true)) {
            return Result.failure(Exception("iTunes albums are not shown in user-facing results"))
        }
        val matchingProvider = activeProviders.find {
            it.metadata.name.equals(album.sourceProvider, ignoreCase = true) ||
            it.metadata.id.equals(album.sourceProvider, ignoreCase = true)
        }
        if (matchingProvider != null) {
            return matchingProvider.getAlbumDetails(album)
        }
        return Result.failure(Exception("Provider '${album.sourceProvider}' is not available for album '${album.title}'"))
    }
}
