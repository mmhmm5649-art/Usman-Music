package com.usman.musicplayer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface AudiusApiService {

    @GET("tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("app_name") appName: String = "USMAN_MUSIC_PLAYER",
        @Query("limit") limit: Int = 30
    ): AudiusTracksResponse

    @GET("tracks/trending")
    suspend fun getTrendingTracks(
        @Query("genre") genre: String? = null,
        @Query("app_name") appName: String = "USMAN_MUSIC_PLAYER",
        @Query("limit") limit: Int = 30
    ): AudiusTracksResponse
}
