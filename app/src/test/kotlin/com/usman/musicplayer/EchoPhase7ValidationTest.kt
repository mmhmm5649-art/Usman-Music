package com.usman.musicplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.usman.musicplayer.extension.EchoMusicProviderBridge
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.sdk.ProviderMetadata
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.settings.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EchoPhase7ValidationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // Mock Extension that implements core Echo interfaces
    class MockEchoExtension : ExtensionClient, SearchFeedClient, TrackClient {
        var lastSettings: Settings? = null
        var updateCount = 0

        override fun setSettings(settings: Settings) {
            lastSettings = settings
        }

        override suspend fun getSettingItems(): List<Setting> {
            return listOf(
                SettingSwitch(key = "high_quality", title = "High Quality Audio", defaultValue = true),
                SettingList(key = "theme", title = "Theme", entries = listOf("Dark", "Light", "Glass"), defaultValue = 0),
                SettingMultipleChoice(key = "languages", title = "Languages", entries = listOf("English", "Telugu", "Hindi"), defaultValues = setOf(0, 1))
            )
        }

        override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
            return Feed(
                loader = { tab ->
                    Feed.Data(
                        pagedData = PagedData(
                            items = listOf(
                                Shelf.Lists.Tracks(
                                    id = "results",
                                    title = "Search Results",
                                    list = listOf(
                                        Track(id = "yt_123", title = "Mock Song from Echo")
                                    )
                                )
                            )
                        )
                    )
                }
            )
        }

        override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
            return track.copy(
                streamables = listOf(
                    Streamable(id = "stream_1", type = Streamable.MediaType.Server)
                )
            )
        }

        override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
            return Streamable.Media.Server(
                source = Streamable.Source.Http(url = "https://mock.cdn/song.mp3")
            )
        }

        override suspend fun loadFeed(track: Track): Feed<Shelf>? = null
    }

    @Test
    fun testEchoBridgeCompatibility() = runBlocking {
        val mockExt = MockEchoExtension()
        val metadata = ProviderMetadata(
            id = "mock.echo.ext",
            name = "Mock Echo Extension",
            version = "1.0.0",
            isFullPlaybackSupported = true,
            authRequired = false
        )
        
        val sharedPrefs = context.getSharedPreferences("test_settings", Context.MODE_PRIVATE)
        val echoSettings = object : Settings {
            override fun getString(key: String): String? = sharedPrefs.getString(key, null)
            override fun putString(key: String, value: String?) = sharedPrefs.edit().putString(key, value).apply()
            override fun getBoolean(key: String): Boolean? = if (sharedPrefs.contains(key)) sharedPrefs.getBoolean(key, false) else null
            override fun putBoolean(key: String, value: Boolean?) {
                if (value == null) sharedPrefs.edit().remove(key).apply()
                else sharedPrefs.edit().putBoolean(key, value).apply()
            }
            override fun getInt(key: String): Int? = if (sharedPrefs.contains(key)) sharedPrefs.getInt(key, 0) else null
            override fun putInt(key: String, value: Int?) {
                if (value == null) sharedPrefs.edit().remove(key).apply()
                else sharedPrefs.edit().putInt(key, value).apply()
            }
            override fun getStringSet(key: String): Set<String>? = sharedPrefs.getStringSet(key, null)
            override fun putStringSet(key: String, value: Set<String>?) = sharedPrefs.edit().putStringSet(key, value).apply()
        }

        val bridge = EchoMusicProviderBridge(mockExt, metadata, echoSettings)

        // 1. Verify Search (Feed -> Shelf -> Track mapping)
        val searchResult = bridge.search("test", 10)
        assertTrue(searchResult.isSuccess)
        val songs = searchResult.getOrNull() ?: emptyList()
        assertEquals(1, songs.size)
        assertEquals("Mock Song from Echo", songs[0].title)
        assertEquals("yt_123", songs[0].externalUrl)

        // 2. Verify Stream Resolution (Multi-step flow)
        val song = songs[0]
        val streamResult = bridge.getStreamUrl(com.usman.musicplayer.sdk.Song(
            id = 1L, title = song.title, artist = "", album = "", durationMs = 0L,
            filePath = song.filePath, externalUrl = song.externalUrl, sourceProvider = "Mock Echo Extension"
        ))
        assertTrue(streamResult.isSuccess)
        assertEquals("https://mock.cdn/song.mp3", streamResult.getOrNull())

        // 3. Verify Settings Integration
        val settingsItems = bridge.getSettingItems()
        assertEquals(3, settingsItems.size)
        assertTrue(settingsItems[0] is SettingSwitch)
        
        bridge.updateSetting("high_quality", false)
        assertEquals(false, sharedPrefs.getBoolean("high_quality", true))
        
        bridge.updateSetting("theme", 2)
        assertEquals(2, sharedPrefs.getInt("theme", 0))

        bridge.updateSetting("languages", setOf("English", "Telugu"))
        assertEquals(setOf("English", "Telugu"), sharedPrefs.getStringSet("languages", null))
    }
}
