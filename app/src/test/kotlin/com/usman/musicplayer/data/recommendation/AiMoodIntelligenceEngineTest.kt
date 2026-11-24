package com.usman.musicplayer.data.recommendation

import com.usman.musicplayer.data.repository.HomeUserSignalsStore
import com.usman.musicplayer.data.repository.PersistedPlayStat
import com.usman.musicplayer.data.repository.PersistedSongRecord
import com.usman.musicplayer.model.ListeningMood
import com.usman.musicplayer.model.MoodTransitionPolicy
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMoodIntelligenceEngineTest {

    private fun song(
        id: Long,
        title: String,
        artist: String = "Test Artist",
        album: String = "Test Album",
        language: String? = "hindi",
        genre: String? = null,
        playbackType: PlaybackType = PlaybackType.FULL,
        isOnline: Boolean = true
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 220_000L,
        filePath = "saavn_$id",
        contentUriString = "https://aac.saavncdn.com/$id.mp4",
        isOnline = isOnline,
        playbackType = playbackType,
        language = language,
        genre = genre,
        sourceProvider = if (isOnline) "JioSaavn" else "Local",
        isStreamable = true
    )

    @Test
    fun analyzesAllTenSupportedMoodsFromMetadata() {
        val cases = listOf(
            song(1, "Heartbreak Sad Alvida", genre = "Sad Ballad") to ListeningMood.SAD_HEARTBREAK,
            song(2, "Ishq Mohabbat Romantic", genre = "Romance") to ListeningMood.ROMANTIC_LOVE,
            song(3, "Happy Sunshine Feel Good") to ListeningMood.HAPPY_FEEL_GOOD,
            song(4, "Chill Acoustic Lofi Breeze", genre = "Chill") to ListeningMood.CHILL_RELAXING,
            song(5, "Emotional Heartfelt Yaadein") to ListeningMood.EMOTIONAL,
            song(6, "Zinda Lakshya Motivational Rise") to ListeningMood.MOTIVATIONAL,
            song(7, "High Energy Workout Beast Adrenaline") to ListeningMood.ENERGETIC,
            song(8, "Club DJ EDM Party Dance Anthem", genre = "Dance EDM") to ListeningMood.PARTY_DANCE,
            song(9, "Midnight Peaceful Moonlight Lullaby") to ListeningMood.PEACEFUL_NIGHT,
            song(10, "Deep Focus Piano Flute Instrumental", genre = "Instrumental Ambient") to ListeningMood.FOCUS_STUDY
        )

        cases.forEach { (track, expectedMood) ->
            val profile = AiMoodIntelligenceEngine.analyzeSongMood(track)
            assertEquals("Expected $expectedMood for '${track.title}'", expectedMood, profile.primaryMood)
            assertTrue(profile.confidence in 0.2f..0.96f)
        }
    }

    @Test
    fun reportsLyricsAndProviderMetadataTruthfully() {
        val noGenreNoLyrics = song(11, "Untitled Mystery Track", artist = "Indie Musician", album = "Session", genre = null)
        val profileNoData = AiMoodIntelligenceEngine.analyzeSongMood(noGenreNoLyrics, lyricsText = null)
        assertFalse(profileNoData.isLyricsAnalyzed)
        assertFalse(profileNoData.isProviderMetadataUsed)
        assertTrue(profileNoData.confidence <= 0.35f)

        val withGenre = noGenreNoLyrics.copy(genre = "Acoustic Chill")
        val profileWithGenre = AiMoodIntelligenceEngine.analyzeSongMood(withGenre, lyricsText = "   ")
        assertFalse(profileWithGenre.isLyricsAnalyzed)
        assertTrue(profileWithGenre.isProviderMetadataUsed)
        assertEquals(ListeningMood.CHILL_RELAXING, profileWithGenre.primaryMood)

        val realLyrics = "Tonight I cry with tears and heartbreak pain remembering goodbye lonely without you"
        val profileWithLyrics = AiMoodIntelligenceEngine.analyzeSongMood(noGenreNoLyrics, lyricsText = realLyrics)
        assertTrue(profileWithLyrics.isLyricsAnalyzed)
        assertEquals(ListeningMood.SAD_HEARTBREAK, profileWithLyrics.primaryMood)
        assertTrue(profileWithLyrics.confidence > profileNoData.confidence)
    }

    @Test
    fun sadSongAutoplaysSadOrEmotionalAndNeverPartyClash() {
        val current = song(100, "Channa Mereya Heartbreak", artist = "Artist A", language = "hindi")
        val currentProfile = AiMoodIntelligenceEngine.analyzeSongMood(current)
        assertEquals(ListeningMood.SAD_HEARTBREAK, currentProfile.primaryMood)

        val candidates = listOf(
            song(101, "DJ Party Dance Floor EDM", artist = "DJ X", genre = "Party EDM", language = "hindi"),
            song(102, "Sad Tanhai Dard Breakup", artist = "Artist B", genre = "Sad", language = "hindi"),
            song(103, "Emotional Yaadein Soulful", artist = "Artist C", genre = "Emotional", language = "telugu"),
            song(104, "DJ Club Remix Preview", artist = "DJ Y", genre = "Dance", playbackType = PlaybackType.PREVIEW)
        )

        val next = AiMoodIntelligenceEngine.selectNextMoodTrack(
            currentSong = current,
            currentMoodProfile = currentProfile,
            candidatePool = candidates
        )

        assertNotNull(next)
        assertEquals(102L, next!!.song.id)
        assertFalse(MoodTransitionPolicy.isAbruptClash(currentProfile.primaryMood, next.targetMood))
        assertEquals(PlaybackType.FULL, next.song.playbackType)
    }

    @Test
    fun romanticHindiSongPrefersRomanticHindiAndMaintainsArtistVariety() {
        val current = song(200, "Kesariya Romantic Love", artist = "Arijit Singh", language = "hindi")
        val currentProfile = AiMoodIntelligenceEngine.analyzeSongMood(current)

        val candidates = listOf(
            song(201, "Tum Hi Ho Romantic Love", artist = "Arijit Singh", language = "hindi"),
            song(202, "Pyaar Ishq Mohabbat Love", artist = "Armaan Malik", language = "hindi"),
            song(203, "Inkem Romantic Love", artist = "Sid Sriram", language = "telugu"),
            song(204, "Hard Rock Gym Pump", artist = "Rock Band", language = "hindi")
        )

        val next = AiMoodIntelligenceEngine.selectNextMoodTrack(
            currentSong = current,
            currentMoodProfile = currentProfile,
            candidatePool = candidates,
            recentArtists = listOf("Arijit Singh")
        )

        assertNotNull(next)
        assertEquals(202L, next!!.song.id)
        assertEquals(ListeningMood.ROMANTIC_LOVE, next.targetMood)
        assertEquals("hindi", next.song.language)
    }

    @Test
    fun chillEnergeticAndMotivationalSongsSelectCompatibleNextTracks() {
        // Chill -> Chill
        val chillCurrent = song(300, "Sukun Monsoon Chill", artist = "Artist 1", language = "hindi")
        val chillNext = AiMoodIntelligenceEngine.selectNextMoodTrack(
            currentSong = chillCurrent,
            currentMoodProfile = AiMoodIntelligenceEngine.analyzeSongMood(chillCurrent),
            candidatePool = listOf(
                song(301, "Soft Acoustic Unplugged Relax", artist = "Artist 2", language = "hindi"),
                song(302, "Club DJ Garmi EDM Party", artist = "Artist 3", language = "hindi")
            )
        )
        assertEquals(301L, chillNext?.song?.id)

        // Energetic -> Energetic/Upbeat
        val energeticCurrent = song(310, "Hukum Mass Energy", artist = "Anirudh Ravichander", language = "tamil")
        val energeticNext = AiMoodIntelligenceEngine.selectNextMoodTrack(
            currentSong = energeticCurrent,
            currentMoodProfile = AiMoodIntelligenceEngine.analyzeSongMood(energeticCurrent),
            candidatePool = listOf(
                song(311, "Thunder Adrenaline Power Beats", artist = "Composer 2", language = "tamil"),
                song(312, "Midnight Sleep Calm Lullaby", artist = "Composer 3", language = "tamil")
            )
        )
        assertEquals(311L, energeticNext?.song?.id)

        // Motivational -> Motivational
        val motivationalCurrent = song(320, "Zinda Lakshya Victory", artist = "Composer A", language = "hindi")
        val motivationalNext = AiMoodIntelligenceEngine.selectNextMoodTrack(
            currentSong = motivationalCurrent,
            currentMoodProfile = AiMoodIntelligenceEngine.analyzeSongMood(motivationalCurrent),
            candidatePool = listOf(
                song(321, "Kar Har Maidaan Motivational Rise", artist = "Composer B", language = "hindi"),
                song(322, "Heartbreak Sad Tears", artist = "Composer C", language = "hindi")
            )
        )
        assertEquals(321L, motivationalNext?.song?.id)
    }

    @Test
    fun penalizesSkippedAndRecentlyAutoplayedTracks() {
        val current = song(400, "Lofi Acoustic Chill", artist = "Artist One", language = "english")
        val currentProfile = AiMoodIntelligenceEngine.analyzeSongMood(current)

        val skippedSong = song(401, "Relax Acoustic Chill Breeze", artist = "Artist Two", language = "english")
        val recentAutoSong = song(402, "Soft Mellow Lofi Relax", artist = "Artist Three", language = "english")
        val freshMatchSong = song(403, "Acoustic Breeze Relaxing Smooth", artist = "Artist Four", language = "english")

        val next = AiMoodIntelligenceEngine.selectNextMoodTrack(
            currentSong = current,
            currentMoodProfile = currentProfile,
            candidatePool = listOf(skippedSong, recentAutoSong, freshMatchSong),
            skippedKeys = setOf(HomeUserSignalsStore.songIdentityKey(skippedSong)),
            recentAutoplayKeys = setOf(HomeUserSignalsStore.songIdentityKey(recentAutoSong))
        )

        assertNotNull(next)
        assertEquals(403L, next!!.song.id)
    }

    @Test
    fun userAffinityLearnsFromHistoryFavoritesAndSkips() {
        val chillFav = song(501, "Acoustic Unplugged Sukun", language = "hindi")
        val peacefulRecent = song(502, "Midnight Peaceful Calm Night", language = "hindi")
        val partySkipped = song(503, "EDM Club Party", language = "hindi")

        val profile = AiMoodIntelligenceEngine.buildUserAffinityProfile(
            recentlyPlayed = listOf(peacefulRecent),
            favoriteSongs = listOf(chillFav),
            playStats = listOf(
                PersistedPlayStat(
                    song = PersistedSongRecord(
                        id = chillFav.id,
                        title = chillFav.title,
                        artist = chillFav.artist,
                        album = chillFav.album,
                        durationMs = chillFav.durationMs,
                        filePath = chillFav.filePath,
                        isOnline = true,
                        language = "hindi",
                        sourceProvider = "JioSaavn"
                    ),
                    count = 5,
                    lastPlayedAtMs = 1_000_000L
                )
            ),
            persistedMoodChoices = mapOf(ListeningMood.CHILL_RELAXING.id to 2.0f),
            skippedSignals = listOf(HomeUserSignalsStore.songIdentityKey(partySkipped) to ListeningMood.PARTY_DANCE)
        )

        assertEquals(ListeningMood.CHILL_RELAXING, profile.dominantMood)
        assertTrue(profile.favoriteLanguages.contains("hindi"))
        assertEquals(0f, profile.moodWeights[ListeningMood.PARTY_DANCE] ?: 0f, 0.01f)
    }

    @Test
    fun recommendationPreferenceAndExcludeRecentlyPlayedAffectSelectionAndRotation() {
        val current = song(600, "Sukun Acoustic Chill", artist = "Artist One", language = "hindi")
        val currentProfile = AiMoodIntelligenceEngine.analyzeSongMood(current)

        val recentExactMatch = song(601, "Chill Acoustic Unplugged Relax", artist = "Artist Two", language = "hindi")
        val freshMatch = song(602, "Soft Mellow Breeze Relax", artist = "Artist Three", language = "hindi")

        val nextWithExclude = AiMoodIntelligenceEngine.selectNextMoodTrack(
            currentSong = current,
            currentMoodProfile = currentProfile,
            candidatePool = listOf(recentExactMatch, freshMatch),
            recentAutoplayKeys = setOf(HomeUserSignalsStore.songIdentityKey(recentExactMatch)),
            recommendationPreference = com.usman.musicplayer.model.RecommendationPreference.DISCOVER_NEW,
            excludeRecentlyPlayed = true
        )
        assertNotNull(nextWithExclude)
        assertEquals(602L, nextWithExclude!!.song.id)

        val emptyProfile = com.usman.musicplayer.model.UserMoodAffinityProfile()
        val rotatedSessionA = AiMoodIntelligenceEngine.scoreAndRotateSongsForSession(
            songs = listOf(recentExactMatch, freshMatch, song(603, "Lofi Midnight Relax", artist = "Artist Four", language = "hindi")),
            sessionSeed = 101L,
            sectionSalt = "daily_mood_mix",
            userMoodProfile = emptyProfile,
            recentlyPlayedKeys = setOf(HomeUserSignalsStore.songIdentityKey(recentExactMatch)),
            recommendationPreference = com.usman.musicplayer.model.RecommendationPreference.DISCOVER_NEW,
            excludeRecentlyPlayed = true
        )
        assertTrue(rotatedSessionA.first().id != recentExactMatch.id)

        // Stable deterministic ordering when sessionSeed is 0 (Dynamic Home Feed OFF)
        val staticPass1 = AiMoodIntelligenceEngine.scoreAndRotateSongsForSession(
            songs = listOf(recentExactMatch, freshMatch),
            sessionSeed = 0L,
            sectionSalt = "daily_mood_mix",
            userMoodProfile = emptyProfile,
            recommendationPreference = com.usman.musicplayer.model.RecommendationPreference.SIMILAR_MOOD,
            excludeRecentlyPlayed = false
        )
        val staticPass2 = AiMoodIntelligenceEngine.scoreAndRotateSongsForSession(
            songs = listOf(recentExactMatch, freshMatch),
            sessionSeed = 0L,
            sectionSalt = "daily_mood_mix",
            userMoodProfile = emptyProfile,
            recommendationPreference = com.usman.musicplayer.model.RecommendationPreference.SIMILAR_MOOD,
            excludeRecentlyPlayed = false
        )
        assertEquals(staticPass1.map { it.id }, staticPass2.map { it.id })
    }
}
