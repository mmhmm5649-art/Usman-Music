package com.usman.musicplayer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.ArtistDetailsData
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.ListeningMood
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.model.UserMoodAffinityProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DynamicHomeFeedSessionTest {

    private class FakeHomeGateway(private val pool: List<Song>) : HomeMusicGateway {
        override suspend fun searchSongs(query: String, limit: Int, page: Int): List<Song> {
            return pool.take(limit)
        }

        override suspend fun searchAlbums(query: String, limit: Int, page: Int): List<AlbumSearchResult> {
            return (1..6).map { i ->
                AlbumSearchResult(
                    id = "alb_$i",
                    title = "Album $i",
                    artist = "Sid Sriram",
                    year = "2025",
                    songCount = 6,
                    artworkUri = "https://example.com/alb_$i.jpg",
                    sourceProvider = "JioSaavn",
                    language = "telugu"
                )
            }
        }

        override suspend fun searchArtists(query: String, limit: Int): List<OnlineArtistResult> {
            return listOf(
                OnlineArtistResult(
                    id = "art_$query",
                    name = query,
                    artworkUri = "https://example.com/$query.jpg",
                    subtitle = "Telugu • Hindi",
                    sourceProvider = "JioSaavn",
                    languages = listOf("Telugu", "Hindi")
                )
            )
        }

        override suspend fun getArtistDetails(artist: OnlineArtistResult): ArtistDetailsData? = null

        override suspend fun searchPlaylists(query: String, limit: Int): List<CuratedPlaylist> {
            return (1..5).map { idx ->
                CuratedPlaylist(
                    id = "pl_${query.hashCode()}_$idx",
                    title = "Romantic Mix $idx",
                    description = "Curated Telugu & Hindi melody mix",
                    coverArtUri = "https://example.com/pl_$idx.jpg",
                    songs = pool.take(5)
                )
            }
        }

        override suspend fun getPlaylistDetails(playlist: CuratedPlaylist): CuratedPlaylist? = playlist
    }

    private fun buildSamplePool(): List<Song> {
        val languages = listOf("telugu", "tamil", "hindi", "english")
        val moods = listOf("Romantic Love", "Chill Acoustic", "Motivational Rise", "Sad Heartbreak", "Happy Feel Good")
        return (1..20).map { idx ->
            val lang = languages[idx % languages.size]
            val moodHint = moods[idx % moods.size]
            Song(
                id = idx.toLong(),
                title = "$moodHint Track $idx",
                artist = if (idx % 2 == 0) "Arijit Singh" else "Sid Sriram",
                album = "Discovery Album $idx",
                durationMs = 210_000L,
                filePath = "saavn_$idx",
                contentUriString = "https://aac.saavncdn.com/$idx.mp4",
                albumArtUri = "https://c.saavncdn.com/$idx.jpg",
                isOnline = true,
                playbackType = PlaybackType.FULL,
                language = lang,
                sourceProvider = "JioSaavn",
                isStreamable = true
            )
        }
    }

    @Test
    fun homeSessionSeedIsStablePerSessionAndChangesAcrossColdStarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = HomeUserSignalsStore(context)

        val session1 = store.startNewHomeSession()
        val session2 = store.startNewHomeSession()

        assertTrue(session1.sessionSeed != 0L)
        assertTrue(session2.sessionSeed != 0L)
        assertNotEquals(session1.sessionSeed, session2.sessionSeed)
        assertEquals(session1.sessionNumber + 1L, session2.sessionNumber)

        val order1A = HomeDiscoveryCatalog.orderedDefinitionsForSession(
            filter = HomeLanguageFilter.ALL,
            sessionSeed = session1.sessionSeed,
            dominantMood = ListeningMood.ROMANTIC_LOVE
        ).map { it.id }

        val order1B = HomeDiscoveryCatalog.orderedDefinitionsForSession(
            filter = HomeLanguageFilter.ALL,
            sessionSeed = session1.sessionSeed,
            dominantMood = ListeningMood.ROMANTIC_LOVE
        ).map { it.id }

        // Stable within the same session (recomposition / scroll never reshuffles)
        assertEquals(order1A, order1B)
        // Essential sections stay present
        assertTrue(order1A.contains("discovery_trending_now"))
        assertTrue(order1A.contains("discovery_mood_mixes"))
        assertTrue(order1A.contains("discovery_popular_artists"))
        assertEquals(10, HomeDiscoveryCatalog.TELUGU_SECTIONS.size)
        assertEquals(20, HomeDiscoveryCatalog.POPULAR_ARTIST_SEEDS[HomeLanguageFilter.ALL]?.size)
    }

    @Test
    fun homeSectionOrderAndItemsRotateAcrossSessionsWithAntiRepeat() = runBlocking {
        val pool = buildSamplePool()
        val repository = HomeDiscoveryRepository(FakeHomeGateway(pool))
        val trendingDef = HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("discovery_trending_now")

        val firstSessionIds = repository.loadSection(
            definition = trendingDef,
            forceRefresh = true,
            sessionSeed = 111111L,
            previousSessionShownKeys = emptySet(),
            userMoodProfile = UserMoodAffinityProfile(dominantMood = ListeningMood.ROMANTIC_LOVE)
        ).songs

        val firstShownKeys = firstSessionIds.take(6).map { HomeUserSignalsStore.songIdentityKey(it) }.toSet()

        // Same session seed returns stable ordering without spurious reshuffling
        val sameSessionRepeat = repository.loadSection(
            definition = trendingDef,
            forceRefresh = true,
            sessionSeed = 111111L,
            previousSessionShownKeys = emptySet(),
            userMoodProfile = UserMoodAffinityProfile(dominantMood = ListeningMood.ROMANTIC_LOVE)
        ).songs
        assertEquals(firstSessionIds.map { it.id }, sameSessionRepeat.map { it.id })

        // New session penalizes items shown in top spots of previous session
        val secondSessionSongs = repository.loadSection(
            definition = trendingDef,
            forceRefresh = true,
            sessionSeed = 999999L,
            previousSessionShownKeys = firstShownKeys,
            userMoodProfile = UserMoodAffinityProfile(dominantMood = ListeningMood.CHILL_RELAXING)
        ).songs

        assertNotEquals(
            firstSessionIds.take(5).map { it.id },
            secondSessionSongs.take(5).map { it.id }
        )
        // Verify no duplicates inside the section
        assertEquals(secondSessionSongs.size, secondSessionSongs.map { it.filePath }.distinct().size)
    }

    @Test
    fun moodMixesSectionProducesCleanPlaylistsWithoutForbiddenProviderCopy() = runBlocking {
        val pool = buildSamplePool()
        val repository = HomeDiscoveryRepository(FakeHomeGateway(pool))
        val moodMixDef = HomeDiscoveryCatalog.DEFINITION_BY_ID.getValue("discovery_mood_mixes")

        val section = repository.loadSection(
            definition = moodMixDef,
            recentlyPlayed = pool.take(4),
            favoriteSongs = pool.drop(4).take(4),
            forceRefresh = true,
            sessionSeed = 424242L,
            userMoodProfile = UserMoodAffinityProfile(dominantMood = ListeningMood.ROMANTIC_LOVE)
        )

        assertEquals(HomeSectionContentType.PLAYLISTS, section.contentType)
        assertTrue(section.playlists.isNotEmpty())
        section.playlists.forEach { pl ->
            assertFalse(pl.title.contains("JioSaavn", ignoreCase = true))
            assertFalse(pl.description.contains("Malayalam", ignoreCase = true))
            assertFalse(pl.description.contains("Phonk", ignoreCase = true))
            assertTrue(pl.songs.isNotEmpty())
        }
    }
}
