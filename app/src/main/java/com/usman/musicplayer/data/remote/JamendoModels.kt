package com.usman.musicplayer.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JamendoResponseDto(
    @Json(name = "headers") val headers: JamendoHeadersDto? = null,
    @Json(name = "results") val results: List<JamendoTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class JamendoHeadersDto(
    @Json(name = "status") val status: String? = null,
    @Json(name = "code") val code: Int? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "results_count") val resultsCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class JamendoTrackDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "artist_id") val artistId: String? = null,
    @Json(name = "artist_name") val artistName: String? = null,
    @Json(name = "album_name") val albumName: String? = null,
    @Json(name = "album_id") val albumId: String? = null,
    @Json(name = "license_ccurl") val licenseCcUrl: String? = null,
    @Json(name = "album_image") val albumImage: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "audio") val audio: String? = null,
    @Json(name = "audiodownload") val audioDownload: String? = null,
    @Json(name = "shareurl") val shareUrl: String? = null,
    @Json(name = "shorturl") val shortUrl: String? = null
)
