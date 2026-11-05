package com.usman.musicplayer.data.repository

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.BuildConfig
import com.usman.musicplayer.data.remote.YouTubeApiService
import com.usman.musicplayer.data.remote.YouTubeSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object YouTubeRepository {

    private const val TAG = "YouTubeRepo"
    private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val apiService: YouTubeApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YouTubeApiService::class.java)

    suspend fun searchMusicVideos(query: String): Result<List<YouTubeSearchResult>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey == "YOUR_YOUTUBE_API_KEY" || apiKey.isBlank()) {
            Log.e(TAG, "YouTube API Key is missing! Configure it in the Secrets panel.")
            return@withContext Result.failure(Exception("YouTube API Key not configured."))
        }

        try {
            // Append "music video" or "official audio" to query if not present for better results
            val enhancedQuery = if (query.lowercase().contains("video") || query.lowercase().contains("music")) {
                query
            } else {
                "$query music video"
            }

            val response = apiService.searchVideos(query = enhancedQuery, apiKey = apiKey)
            Result.success(response.items.filter { it.id.videoId != null })
        } catch (e: Exception) {
            Log.e(TAG, "searchMusicVideos failed", e)
            Result.failure(e)
        }
    }
}
