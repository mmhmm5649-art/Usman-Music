package com.usman.musicplayer.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApiService {

    @GET("api.php")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("n") limit: Int = 20,
        @Query("p") page: Int = 1,
        @Query("__call") call: String = "search.getResults",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<JioSaavnSearchResponse>

    @GET("api.php")
    suspend fun getSongDetails(
        @Query("pids") pids: String,
        @Query("__call") call: String = "song.getDetails",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun getSongDetailsWithReferer(
        @retrofit2.http.Header("Referer") referer: String = "https://www.jiosaavn.com/",
        @retrofit2.http.Header("User-Agent") userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        @Query("pids") pids: String,
        @Query("__call") call: String = "song.getDetails",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>


    @GET("api.php")
    suspend fun getLyrics(
        @Query("lyrics_id") songId: String,
        @Query("__call") call: String = "lyrics.getLyrics",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<JioSaavnLyricsResponse>

    // --- sumitkolhe / saavn.dev API endpoints ---
    @GET
    suspend fun searchSongsCustom(
        @retrofit2.http.Url url: String
    ): Response<ResponseBody>

    @GET
    suspend fun getSongDetailsCustom(
        @retrofit2.http.Url url: String
    ): Response<SaavnDevSongDetailsResponse>
}

