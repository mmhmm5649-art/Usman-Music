package com.usman.musicplayer.sdk

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.data.remote.AudiusApiService
import com.usman.musicplayer.data.repository.LocalMusicProvider
import com.usman.musicplayer.data.repository.providers.AudiusProvider
import com.usman.musicplayer.data.repository.providers.CcMixterProvider
import com.usman.musicplayer.data.repository.providers.InternetArchiveMusicProvider
import com.usman.musicplayer.data.repository.providers.JioSaavnHubExtensionProvider
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

object ProviderRegistry {
    private val _providers = CopyOnWriteArrayList<MusicProvider>()
    private val _enabledMap = ConcurrentHashMap<String, Boolean>()

    val providers: List<MusicProvider> get() = _providers
    val activeProviders: List<MusicProvider> get() = _providers.filter { _enabledMap[it.metadata.id] != false }

    fun init(context: Context) {
        if (_providers.isNotEmpty()) return

        val audiusApiService = Retrofit.Builder()
            .baseUrl("https://discoveryprovider.audius.co/v1/")
            .client(OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).build())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .build()
            .create(AudiusApiService::class.java)

        registerProvider(LocalMusicProvider(context), enabled = true)
        registerProvider(AudiusProvider(audiusApiService), enabled = true)
        registerProvider(CcMixterProvider(), enabled = true)
        registerProvider(InternetArchiveMusicProvider(), enabled = true)
        registerProvider(JioSaavnHubExtensionProvider(), enabled = true)
    }

    fun registerProvider(provider: MusicProvider, enabled: Boolean = true) {
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

        // Fallback: try active providers
        for (provider in activeProviders) {
            val result = provider.getStreamUrl(song)
            if (result.isSuccess) {
                return result
            }
        }

        return Result.failure(Exception("No streaming provider available for ${song.title}"))
    }
}
