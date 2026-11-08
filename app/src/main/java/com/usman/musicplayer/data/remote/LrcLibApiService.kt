package com.usman.musicplayer.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class LrcLibTrack(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "trackName") val trackName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    @Json(name = "albumName") val albumName: String? = null,
    @Json(name = "duration") val duration: Double? = null,
    @Json(name = "instrumental") val instrumental: Boolean? = null,
    @Json(name = "plainLyrics") val plainLyrics: String? = null,
    @Json(name = "syncedLyrics") val syncedLyrics: String? = null
)

interface LrcLibApiService {
    @GET("api/get")
    suspend fun getLyrics(
        @Header("User-Agent") userAgent: String = "USMAN Music Player (https://github.com/usman/musicplayer)",
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") duration: Int? = null
    ): Response<LrcLibTrack>

    @GET("api/search")
    suspend fun searchLyrics(
        @Header("User-Agent") userAgent: String = "USMAN Music Player (https://github.com/usman/musicplayer)",
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null,
        @Query("duration") duration: Int? = null
    ): Response<List<LrcLibTrack>>
}
