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

    @GET("api.php")
    suspend fun searchAlbums(
        @Query("q") query: String,
        @Query("n") limit: Int = 20,
        @Query("p") page: Int = 1,
        @Query("__call") call: String = "search.getAlbumResults",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun getAlbumDetailsWithReferer(
        @retrofit2.http.Header("Referer") referer: String = "https://www.jiosaavn.com/",
        @retrofit2.http.Header("User-Agent") userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        @Query("albumid") albumId: String,
        @Query("__call") call: String = "content.getAlbumDetails",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("n") limit: Int = 20,
        @Query("p") page: Int = 1,
        @Query("__call") call: String = "search.getArtistResults",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun getArtistPageDetailsWithReferer(
        @retrofit2.http.Header("Referer") referer: String = "https://www.jiosaavn.com/",
        @retrofit2.http.Header("User-Agent") userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        @Query("artistId") artistId: String,
        @Query("n_song") songCount: Int = 25,
        @Query("n_album") albumCount: Int = 15,
        @Query("__call") call: String = "artist.getArtistPageDetails",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun searchPlaylists(
        @Query("q") query: String,
        @Query("n") limit: Int = 15,
        @Query("p") page: Int = 1,
        @Query("__call") call: String = "search.getPlaylistResults",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun getPlaylistDetailsWithReferer(
        @retrofit2.http.Header("Referer") referer: String = "https://www.jiosaavn.com/",
        @retrofit2.http.Header("User-Agent") userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        @Query("listid") listId: String,
        @Query("__call") call: String = "playlist.getDetails",
        @Query("_format") format: String = "json",
        @Query("_marker") marker: Int = 0,
        @Query("ctx") context: String = "web6dot0",
        @Query("api_version") apiVersion: Int = 4
    ): Response<ResponseBody>

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

