package com.usman.musicplayer.sdk

import com.usman.musicplayer.data.repository.providers.AudiusProvider
import com.usman.musicplayer.data.repository.LocalMusicProvider
import android.content.Context
import com.usman.musicplayer.data.remote.AudiusApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

object ProviderRegistry {
    private val _providers = mutableListOf<MusicProvider>()
    val providers: List<MusicProvider> get() = _providers

    fun init(context: Context) {
        val audiusApiService = Retrofit.Builder()
            .baseUrl("https://discoveryprovider.audius.co/v1/")
            .client(OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).build())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .build()
            .create(AudiusApiService::class.java)

        _providers.add(LocalMusicProvider(context))
        _providers.add(AudiusProvider(audiusApiService))
    }
}
