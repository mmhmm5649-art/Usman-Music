package com.usman.musicplayer.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchResult>
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchResult(
    val id: YouTubeVideoId,
    val snippet: YouTubeSnippet
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoId(
    val videoId: String?
)

@JsonClass(generateAdapter = true)
data class YouTubeSnippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: YouTubeThumbnails
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    val high: YouTubeThumbnail?
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnail(
    val url: String
)
