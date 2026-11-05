package com.usman.musicplayer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface JamendoApiService {

    @GET("v3.0/tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String,
        @Query("search") search: String? = null,
        @Query("tags") tags: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("format") format: String = "json",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("include") include: String = "licenses"
    ): JamendoResponseDto

    @GET("v3.0/tracks/")
    suspend fun getTrendingTracks(
        @Query("client_id") clientId: String,
        @Query("order") order: String = "popularity_week",
        @Query("limit") limit: Int = 30,
        @Query("format") format: String = "json",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("include") include: String = "licenses"
    ): JamendoResponseDto
}
