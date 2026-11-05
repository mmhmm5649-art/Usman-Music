package com.usman.musicplayer.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ITunesSearchResponse(
    @Json(name = "resultCount") val resultCount: Int = 0,
    @Json(name = "results") val results: List<ITunesTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ITunesTrackDto(
    @Json(name = "trackId") val trackId: Long? = null,
    @Json(name = "trackName") val trackName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    @Json(name = "collectionName") val collectionName: String? = null,
    @Json(name = "previewUrl") val previewUrl: String? = null,
    @Json(name = "artworkUrl100") val artworkUrl100: String? = null,
    @Json(name = "artworkUrl60") val artworkUrl60: String? = null,
    @Json(name = "trackViewUrl") val trackViewUrl: String? = null,
    @Json(name = "artistViewUrl") val artistViewUrl: String? = null,
    @Json(name = "primaryGenreName") val primaryGenreName: String? = null,
    @Json(name = "trackTimeMillis") val trackTimeMillis: Long? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null
)

@JsonClass(generateAdapter = true)
data class AudiusTracksResponse(
    @Json(name = "data") val data: List<AudiusTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AudiusTrackDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "track_id") val trackId: Long? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "mood") val mood: String? = null,
    @Json(name = "user") val user: AudiusUserDto? = null,
    @Json(name = "artwork") val artwork: AudiusArtworkDto? = null,
    @Json(name = "permalink") val permalink: String? = null,
    @Json(name = "is_streamable") val isStreamable: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class AudiusUserDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "handle") val handle: String? = null
)

@JsonClass(generateAdapter = true)
data class AudiusArtworkDto(
    @Json(name = "150x150") val art150: String? = null,
    @Json(name = "480x480") val art480: String? = null,
    @Json(name = "1000x1000") val art1000: String? = null
)
