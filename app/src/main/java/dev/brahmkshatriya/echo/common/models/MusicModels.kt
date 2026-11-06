package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class Artist(
    override val id: String,
    val name: String,
    override val cover: ImageHolder? = null,
    override val description: String? = null,
    override val background: ImageHolder? = null,
    override val subtitle: String? = null,
    override val extras: Map<String, String> = emptyMap(),
    override val isRadioSupported: Boolean = false,
    override val isFollowable: Boolean = false,
    override val isSaveable: Boolean = false,
    override val isLikeable: Boolean = false,
    override val isHideable: Boolean = false,
    override val isShareable: Boolean = false,
    override val isExplicit: Boolean = false
) : EchoMediaItem {
    override val title: String get() = name
}

@Serializable
data class Album(
    override val id: String,
    override val title: String,
    override val cover: ImageHolder? = null,
    val artists: List<Artist> = emptyList(),
    override val description: String? = null,
    override val background: ImageHolder? = null,
    override val subtitle: String? = null,
    override val extras: Map<String, String> = emptyMap(),
    override val isRadioSupported: Boolean = false,
    override val isFollowable: Boolean = false,
    override val isSaveable: Boolean = false,
    override val isLikeable: Boolean = false,
    override val isHideable: Boolean = false,
    override val isShareable: Boolean = false,
    override val isExplicit: Boolean = false
) : EchoMediaItem

@Serializable
data class Track(
    override val id: String,
    override val title: String,
    val type: Type = Type.Song,
    override val cover: ImageHolder? = null,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val duration: Long? = null,
    val playedDuration: Long? = null,
    val plays: Long? = null,
    // Using Long for dates in stubs to avoid serialization complexity with java.util.Date in mock environments
    val releaseDateMs: Long? = null, 
    override val description: String? = null,
    override val background: ImageHolder? = null,
    val genres: List<String> = emptyList(),
    val isrc: String? = null,
    val albumOrderNumber: Long? = null,
    val albumDiscNumber: Long? = null,
    override val isExplicit: Boolean = false,
    override val subtitle: String? = null,
    override val extras: Map<String, String> = emptyMap(),
    val isPlayable: Boolean = true,
    val streamables: List<Streamable> = emptyList(),
    override val isRadioSupported: Boolean = true,
    override val isFollowable: Boolean = false,
    override val isSaveable: Boolean = true,
    override val isLikeable: Boolean = true,
    override val isHideable: Boolean = true,
    override val isShareable: Boolean = true
) : EchoMediaItem {
    enum class Type {
        Song, Podcast, VideoSong, Video, HorizontalVideo
    }
}

@Serializable
data class Streamable(
    val id: String,
    val quality: Int = 0,
    val type: MediaType = MediaType.Server,
    val title: String? = null,
    val extras: Map<String, String> = emptyMap()
) {
    enum class MediaType {
        Server, Background, Subtitle
    }

    @Serializable
    sealed class Media {
        @Serializable
        data class Server(val source: Source) : Media()
        @Serializable
        data class Background(val source: Source) : Media()
    }

    @Serializable
    sealed class Source {
        @Serializable
        data class Http(val url: String) : Source()
    }
}
