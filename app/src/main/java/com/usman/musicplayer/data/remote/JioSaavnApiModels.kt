package com.usman.musicplayer.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JioSaavnSearchResponse(
    @Json(name = "results") val results: List<JioSaavnSong> = emptyList(),
    @Json(name = "total") val total: Int = 0,
    @Json(name = "start") val start: Int = 0
)

@JsonClass(generateAdapter = true)
data class JioSaavnSong(
    @Json(name = "id") val id: String? = null,
    @Json(name = "song") val song: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "album") val album: Any? = null,
    @Json(name = "primary_artists") val primaryArtists: Any? = null,
    @Json(name = "singers") val singers: Any? = null,
    @Json(name = "artist") val artist: Any? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "duration") val duration: Any? = null,
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "encrypted_media_url") val encryptedMediaUrl: String? = null,
    @Json(name = "perma_url") val permaUrl: String? = null,
    @Json(name = "album_url") val albumUrl: String? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "year") val year: String? = null,
    @Json(name = "more_info") val moreInfo: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class JioSaavnLyricsResponse(
    @Json(name = "lyrics") val lyrics: String? = null,
    @Json(name = "script") val script: String? = null
)
