package com.usman.musicplayer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {

    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 30,
        @Query("country") country: String? = null,
        @Query("offset") offset: Int? = null
    ): ITunesSearchResponse

    @GET("search")
    suspend fun searchAlbums(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 25,
        @Query("country") country: String? = null,
        @Query("offset") offset: Int? = null
    ): ITunesSearchResponse

    @GET("lookup")
    suspend fun lookupCollection(
        @Query("id") collectionId: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 200,
        @Query("country") country: String? = null
    ): ITunesSearchResponse
}
