package com.usman.musicplayer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {

    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 30,
        @Query("country") country: String? = null
    ): ITunesSearchResponse
}
