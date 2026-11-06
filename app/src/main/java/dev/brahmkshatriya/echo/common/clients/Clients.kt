package dev.brahmkshatriya.echo.common.clients

import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.providers.SettingsProvider

interface ExtensionClient : SettingsProvider {
    suspend fun onExtensionSelected() {}
    suspend fun onInitialize() {}
}

interface TrackClient {
    suspend fun loadTrack(track: Track, isDownload: Boolean): Track
    suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media
    suspend fun loadFeed(track: Track): Feed<Shelf>?
}

interface SearchFeedClient {
    suspend fun loadSearchFeed(query: String): Feed<Shelf>
}
