package com.usman.musicplayer.data.recommendation

import com.usman.musicplayer.data.repository.HomeUserSignalsStore
import com.usman.musicplayer.data.repository.PersistedPlayStat
import com.usman.musicplayer.model.ArtistMatchingPolicy
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.ListeningMood
import com.usman.musicplayer.model.MoodNextRecommendation
import com.usman.musicplayer.model.MoodTransitionPolicy
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.RecommendationPreference
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.model.SongMoodProfile
import com.usman.musicplayer.model.UserMoodAffinityProfile
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.min

/**
 * In-app AI Mood Intelligence Engine for USMAN MUSIC.
 *
 * Truthfully analyzes available song metadata (title, album, artist, genre, folder),
 * optional fetched lyrics text, and behavioral listening history (favorites, play stats,
 * playlists, skips, mood choices). Never fabricates audio or lyrics analysis when data
 * is missing—reports confidence and source flags transparently.
 */
object AiMoodIntelligenceEngine {

    private val MOOD_KEYWORDS: Map<ListeningMood, List<Pair<String, Float>>> = mapOf(
        ListeningMood.SAD_HEARTBREAK to listOf(
            "sad" to 3.4f, "heartbreak" to 3.8f, "breakup" to 3.6f, "broken" to 2.6f,
            "cry" to 2.4f, "tears" to 2.8f, "lonely" to 2.6f, "miss you" to 2.8f,
            "goodbye" to 2.2f, "hurt" to 2.2f, "pain" to 2.4f, "sorrow" to 2.8f,
            "grief" to 2.8f, "alone" to 2.0f, "regret" to 2.4f, "betray" to 2.6f,
            "bewafa" to 3.6f, "judai" to 3.6f, "dard" to 3.4f, "tanhai" to 3.4f,
            "tanha" to 3.0f, "alvida" to 2.8f, "aansu" to 3.2f, "ansoo" to 3.2f,
            "gham" to 3.0f, "dhoka" to 3.2f, "channa mereya" to 3.6f, "agar tum saath" to 3.0f,
            "bekhayali" to 3.2f, "tadap" to 3.0f, "rooth" to 2.8f, "bichad" to 3.0f,
            "zakhm" to 3.2f, "dil toot" to 3.6f, "viraha" to 3.4f, "kanneer" to 3.4f,
            "love failure" to 3.8f, "yedhaki" to 2.8f, "prema desam" to 2.0f,
            "unplugged sad" to 3.2f, "khuda jaane" to 1.6f
        ),
        ListeningMood.ROMANTIC_LOVE to listOf(
            "romantic" to 3.6f, "romance" to 3.5f, "love" to 3.0f, "darling" to 2.6f,
            "sweetheart" to 2.8f, "forever" to 2.2f, "kiss" to 2.4f, "hug" to 2.2f,
            "crush" to 2.4f, "valentine" to 3.0f, "soulmate" to 2.8f, "melody" to 2.2f,
            "pyaar" to 3.2f, "pyar" to 3.2f, "ishq" to 3.4f, "mohabbat" to 3.4f,
            "mohabbatein" to 3.2f, "dil" to 1.8f, "jaan" to 2.4f, "sanam" to 3.0f,
            "humsafar" to 3.0f, "tum hi ho" to 3.2f, "kesariya" to 3.0f, "tera hone" to 2.8f,
            "prema" to 3.2f, "prem" to 2.8f, "inkem" to 3.0f, "samajavaragamana" to 3.0f,
            "kaadhal" to 3.4f, "kadhal" to 3.4f, "anbe" to 3.0f, "enai" to 2.4f,
            "munbe vaa" to 3.2f, "vaseegara" to 3.2f, "perfect" to 2.0f, "beloved" to 2.6f
        ),
        ListeningMood.HAPPY_FEEL_GOOD to listOf(
            "happy" to 3.4f, "feel good" to 3.8f, "joy" to 3.0f, "smile" to 2.6f,
            "sunshine" to 3.0f, "cheerful" to 3.4f, "bright" to 2.2f, "celebrate" to 2.6f,
            "breezy" to 2.8f, "uplifting" to 3.0f, "good vibes" to 3.2f, "sunny" to 2.4f,
            "khushi" to 3.2f, "khush" to 3.0f, "muskurane" to 2.6f, "mazaa" to 2.4f,
            "mast" to 2.4f, "balam pichkari" to 2.6f, "butta bomma" to 3.0f, "sooseki" to 2.4f,
            "vaathi coming" to 2.0f, "arabic kuthu" to 2.0f, "golden" to 2.2f, "levitating" to 2.6f
        ),
        ListeningMood.CHILL_RELAXING to listOf(
            "chill" to 3.6f, "relax" to 3.6f, "relaxing" to 3.6f, "lofi" to 3.8f,
            "lo-fi" to 3.8f, "acoustic" to 3.2f, "unplugged" to 3.4f, "mellow" to 3.0f,
            "soft" to 2.6f, "breeze" to 2.4f, "slowed" to 3.4f, "reverb" to 3.2f,
            "easy" to 2.0f, "cool" to 1.8f, "smooth" to 2.4f, "coffee" to 2.6f,
            "rain" to 2.2f, "monsoon" to 2.8f, "baarish" to 2.6f, "sukun" to 3.4f,
            "sukoon" to 3.4f, "shaam" to 2.6f, "hawa" to 2.0f, "ilahi" to 2.2f
        ),
        ListeningMood.EMOTIONAL to listOf(
            "emotional" to 3.8f, "emotion" to 3.4f, "soulful" to 3.4f, "soul" to 2.4f,
            "heartfelt" to 3.4f, "memories" to 3.0f, "memory" to 2.8f, "nostalgia" to 3.2f,
            "feelings" to 2.8f, "deep" to 2.0f, "story" to 1.8f, "mother" to 2.4f,
            "yaad" to 3.2f, "yaadein" to 3.4f, "ehsaas" to 3.2f, "lamhe" to 3.0f,
            "zindagi" to 2.8f, "khamoshiyan" to 3.2f, "hamari adhuri" to 3.4f, "phir le aya" to 3.2f,
            "ninnu kori" to 2.8f, "life of ram" to 3.4f, "ammi" to 2.6f, "amma" to 2.4f
        ),
        ListeningMood.MOTIVATIONAL to listOf(
            "motivational" to 3.8f, "motivation" to 3.8f, "inspire" to 3.4f, "inspiring" to 3.4f,
            "champion" to 3.2f, "victory" to 3.2f, "win" to 2.8f, "rise" to 2.8f,
            "unstoppable" to 3.4f, "believer" to 3.2f, "dream" to 2.2f, "fight" to 2.4f,
            "stronger" to 3.0f, "courage" to 3.0f, "grind" to 2.8f, "hustle" to 2.8f,
            "hero" to 2.2f, "zinda" to 3.4f, "lakshya" to 3.6f, "kar har maidaan" to 3.8f,
            "dangal" to 3.4f, "chak de" to 3.6f, "apna time aayega" to 3.6f, "josh" to 3.2f,
            "jeete" to 3.0f, "sultan" to 2.6f, "aashayein" to 3.6f, "brothers anthem" to 3.4f
        ),
        ListeningMood.ENERGETIC to listOf(
            "energetic" to 3.8f, "energy" to 3.4f, "power" to 3.0f, "workout" to 3.6f,
            "gym" to 3.6f, "pump" to 3.2f, "beast" to 3.2f, "thunder" to 3.0f,
            "fire" to 2.6f, "adrenaline" to 3.6f, "rock" to 2.8f, "hip hop" to 2.6f,
            "rap" to 2.6f, "mass" to 3.4f, "elevation" to 3.4f, "bgm" to 2.2f,
            "dhoom" to 3.2f, "malhari" to 3.4f, "tattad" to 3.0f, "khalibali" to 3.2f,
            "hukum" to 3.4f, "naatu" to 3.2f, "oo antava" to 2.8f, "ramulo" to 2.6f,
            "badass" to 3.4f, "vikram" to 3.0f, "pushpa" to 2.4f, "leo" to 2.2f
        ),
        ListeningMood.PARTY_DANCE to listOf(
            "party" to 3.8f, "dance" to 3.6f, "club" to 3.6f, "dj" to 3.6f,
            "edm" to 3.6f, "electronic" to 3.0f, "remix" to 3.2f, "disco" to 3.4f,
            "groove" to 2.8f, "floor" to 2.4f, "banger" to 3.2f, "house" to 2.4f,
            "techno" to 3.2f, "kuthu" to 3.6f, "item" to 3.2f, "nach" to 3.4f,
            "naach" to 3.4f, "nashe" to 3.2f, "garmi" to 3.4f, "saturday" to 3.0f,
            "kala chashma" to 3.6f, "kar gayi chull" to 3.6f, "badtameez dil" to 3.4f,
            "laila" to 2.4f, "jhoome" to 3.2f, "besharam" to 2.6f, "tamma" to 3.0f
        ),
        ListeningMood.PEACEFUL_NIGHT to listOf(
            "peaceful" to 3.8f, "peace" to 3.4f, "night" to 3.2f, "midnight" to 3.6f,
            "moon" to 2.8f, "moonlight" to 3.4f, "stars" to 2.8f, "sleep" to 3.4f,
            "calm" to 3.4f, "serene" to 3.6f, "quiet" to 3.0f, "lullaby" to 3.6f,
            "sufi" to 3.4f, "raat" to 3.2f, "raatein" to 3.4f, "chand" to 2.8f,
            "chaand" to 2.8f, "neend" to 3.0f, "kun faya kun" to 3.8f, "nadaan parindey" to 3.0f,
            "khwaja" to 3.2f, "vennila" to 3.2f, "nilave" to 3.0f, "raatri" to 3.2f
        ),
        ListeningMood.FOCUS_STUDY to listOf(
            "focus" to 3.8f, "study" to 3.8f, "instrumental" to 3.8f, "piano" to 3.4f,
            "flute" to 3.4f, "violin" to 3.2f, "guitar solo" to 3.2f, "ambient" to 3.6f,
            "meditation" to 3.6f, "zen" to 3.4f, "classical" to 3.2f, "orchestra" to 3.0f,
            "soundtrack" to 2.0f, "score" to 2.4f, "theme" to 2.6f, "interlude" to 3.2f,
            "prelude" to 3.2f, "nocturne" to 3.4f, "bansuri" to 3.6f, "sitar" to 3.4f,
            "veena" to 3.4f, "carnatic" to 3.0f, "hindustani" to 2.8f, "concentration" to 3.8f
        )
    )

    private val ARTIST_PRIORS: List<Pair<String, Map<ListeningMood, Float>>> = listOf(
        "arijit singh" to mapOf(ListeningMood.ROMANTIC_LOVE to 1.4f, ListeningMood.SAD_HEARTBREAK to 1.2f, ListeningMood.EMOTIONAL to 1.1f),
        "sid sriram" to mapOf(ListeningMood.ROMANTIC_LOVE to 1.4f, ListeningMood.EMOTIONAL to 1.3f, ListeningMood.SAD_HEARTBREAK to 1.0f),
        "shreya ghoshal" to mapOf(ListeningMood.ROMANTIC_LOVE to 1.4f, ListeningMood.PEACEFUL_NIGHT to 1.0f, ListeningMood.EMOTIONAL to 0.9f),
        "armaan malik" to mapOf(ListeningMood.ROMANTIC_LOVE to 1.4f, ListeningMood.CHILL_RELAXING to 1.0f),
        "a. r. rahman" to mapOf(ListeningMood.PEACEFUL_NIGHT to 1.2f, ListeningMood.EMOTIONAL to 1.1f, ListeningMood.ROMANTIC_LOVE to 1.1f),
        "ar rahman" to mapOf(ListeningMood.PEACEFUL_NIGHT to 1.2f, ListeningMood.EMOTIONAL to 1.1f, ListeningMood.ROMANTIC_LOVE to 1.1f),
        "yuvan shankar raja" to mapOf(ListeningMood.EMOTIONAL to 1.2f, ListeningMood.SAD_HEARTBREAK to 1.1f, ListeningMood.PEACEFUL_NIGHT to 0.9f),
        "anirudh ravichander" to mapOf(ListeningMood.ENERGETIC to 1.3f, ListeningMood.PARTY_DANCE to 1.1f, ListeningMood.ROMANTIC_LOVE to 0.8f),
        "devi sri prasad" to mapOf(ListeningMood.PARTY_DANCE to 1.3f, ListeningMood.ENERGETIC to 1.2f, ListeningMood.HAPPY_FEEL_GOOD to 0.9f),
        "thaman s" to mapOf(ListeningMood.ENERGETIC to 1.3f, ListeningMood.PARTY_DANCE to 1.1f, ListeningMood.ROMANTIC_LOVE to 0.7f),
        "neha kakkar" to mapOf(ListeningMood.PARTY_DANCE to 1.5f, ListeningMood.HAPPY_FEEL_GOOD to 1.0f),
        "pritam" to mapOf(ListeningMood.ROMANTIC_LOVE to 1.2f, ListeningMood.HAPPY_FEEL_GOOD to 0.9f, ListeningMood.EMOTIONAL to 0.8f),
        "sonu nigam" to mapOf(ListeningMood.EMOTIONAL to 1.2f, ListeningMood.ROMANTIC_LOVE to 1.2f),
        "billie eilish" to mapOf(ListeningMood.CHILL_RELAXING to 1.3f, ListeningMood.SAD_HEARTBREAK to 1.2f, ListeningMood.PEACEFUL_NIGHT to 1.0f),
        "the weeknd" to mapOf(ListeningMood.PEACEFUL_NIGHT to 1.2f, ListeningMood.ENERGETIC to 1.0f, ListeningMood.CHILL_RELAXING to 0.9f),
        "ed sheeran" to mapOf(ListeningMood.ROMANTIC_LOVE to 1.4f, ListeningMood.CHILL_RELAXING to 1.1f),
        "taylor swift" to mapOf(ListeningMood.HAPPY_FEEL_GOOD to 1.1f, ListeningMood.ROMANTIC_LOVE to 1.1f, ListeningMood.EMOTIONAL to 0.9f),
        "bruno mars" to mapOf(ListeningMood.HAPPY_FEEL_GOOD to 1.4f, ListeningMood.PARTY_DANCE to 1.2f),
        "justin bieber" to mapOf(ListeningMood.HAPPY_FEEL_GOOD to 1.1f, ListeningMood.ROMANTIC_LOVE to 1.1f),
        "santhosh narayanan" to mapOf(ListeningMood.CHILL_RELAXING to 1.1f, ListeningMood.EMOTIONAL to 1.1f, ListeningMood.ENERGETIC to 0.9f)
    )

    private val TIMESTAMP_OR_MARKUP_REGEX = Regex("\\[[^\\]]*]|<[^>]+>")

    fun analyzeSongMood(
        song: Song,
        lyricsText: String? = null,
        userMoodProfile: UserMoodAffinityProfile? = null
    ): SongMoodProfile {
        val scoreMap = EnumMap<ListeningMood, Float>(ListeningMood::class.java).apply {
            ListeningMood.entries.forEach { put(it, 0f) }
        }

        val titleLower = song.title.lowercase()
        val albumLower = song.album.lowercase()
        val folderLower = song.folderName.lowercase()
        val genreLower = song.genre.orEmpty().lowercase().trim()
        val artistLower = song.artist.lowercase()

        val hasGenreMetadata = genreLower.isNotBlank() && genreLower != "null"
        var totalSignalMagnitude = 0f
        var genreSignalMagnitude = 0f

        // 1. Explicit provider/ID3 genre & mood metadata
        if (hasGenreMetadata) {
            for ((mood, terms) in MOOD_KEYWORDS) {
                for ((term, weight) in terms) {
                    if (containsKeyword(genreLower, term)) {
                        val delta = weight * 1.15f
                        scoreMap[mood] = (scoreMap[mood] ?: 0f) + delta
                        genreSignalMagnitude += delta
                        totalSignalMagnitude += delta
                    }
                }
            }
            // Broad music genre hints
            if (genreLower.contains("edm") || genreLower.contains("dance") || genreLower.contains("club") || genreLower.contains("disco")) {
                scoreMap[ListeningMood.PARTY_DANCE] = (scoreMap[ListeningMood.PARTY_DANCE] ?: 0f) + 3.2f
                genreSignalMagnitude += 3.2f
                totalSignalMagnitude += 3.2f
            }
            if (genreLower.contains("rock") || genreLower.contains("metal") || genreLower.contains("hip-hop") || genreLower.contains("hip hop") || genreLower.contains("rap")) {
                scoreMap[ListeningMood.ENERGETIC] = (scoreMap[ListeningMood.ENERGETIC] ?: 0f) + 2.8f
                genreSignalMagnitude += 2.8f
                totalSignalMagnitude += 2.8f
            }
            if (genreLower.contains("classical") || genreLower.contains("instrumental") || genreLower.contains("ambient")) {
                scoreMap[ListeningMood.FOCUS_STUDY] = (scoreMap[ListeningMood.FOCUS_STUDY] ?: 0f) + 3.0f
                genreSignalMagnitude += 3.0f
                totalSignalMagnitude += 3.0f
            }
            if (genreLower.contains("acoustic") || genreLower.contains("indie") || genreLower.contains("chill")) {
                scoreMap[ListeningMood.CHILL_RELAXING] = (scoreMap[ListeningMood.CHILL_RELAXING] ?: 0f) + 2.6f
                genreSignalMagnitude += 2.6f
                totalSignalMagnitude += 2.6f
            }
            if (genreLower.contains("sufi") || genreLower.contains("ghazal")) {
                scoreMap[ListeningMood.PEACEFUL_NIGHT] = (scoreMap[ListeningMood.PEACEFUL_NIGHT] ?: 0f) + 2.6f
                scoreMap[ListeningMood.EMOTIONAL] = (scoreMap[ListeningMood.EMOTIONAL] ?: 0f) + 1.8f
                genreSignalMagnitude += 4.4f
                totalSignalMagnitude += 4.4f
            }
        }

        // 2. Title, album & folder metadata
        for ((mood, terms) in MOOD_KEYWORDS) {
            for ((term, weight) in terms) {
                if (containsKeyword(titleLower, term)) {
                    scoreMap[mood] = (scoreMap[mood] ?: 0f) + weight
                    totalSignalMagnitude += weight
                }
                if (albumLower.isNotBlank() && albumLower != "unknown album" && containsKeyword(albumLower, term)) {
                    val delta = weight * 0.62f
                    scoreMap[mood] = (scoreMap[mood] ?: 0f) + delta
                    totalSignalMagnitude += delta
                }
                if (folderLower.isNotBlank() && folderLower != "jiosaavn" && containsKeyword(folderLower, term)) {
                    val delta = weight * 0.55f
                    scoreMap[mood] = (scoreMap[mood] ?: 0f) + delta
                    totalSignalMagnitude += delta
                }
            }
        }

        // 3. Optional real lyrics sentiment analysis (only when lyrics text genuinely exists)
        val cleanedLyrics = lyricsText
            ?.replace(TIMESTAMP_OR_MARKUP_REGEX, " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        val lyricsValid = cleanedLyrics.length >= 24 && cleanedLyrics.count { it.isLetter() } >= 16
        var lyricsSignalMagnitude = 0f
        if (lyricsValid) {
            val lyricsLower = cleanedLyrics.lowercase()
            for ((mood, terms) in MOOD_KEYWORDS) {
                var moodLyricSum = 0f
                for ((term, weight) in terms) {
                    if (containsKeyword(lyricsLower, term)) {
                        moodLyricSum += (weight * 0.55f)
                    }
                }
                val capped = min(moodLyricSum, 7.5f)
                if (capped > 0f) {
                    scoreMap[mood] = (scoreMap[mood] ?: 0f) + capped
                    lyricsSignalMagnitude += capped
                    totalSignalMagnitude += capped
                }
            }
        }

        // 4. Weak artist prior tie-breaker (never overrides strong title/genre/lyrics signals)
        var artistPriorApplied = false
        for ((artistKey, moodMap) in ARTIST_PRIORS) {
            if (artistLower.contains(artistKey)) {
                artistPriorApplied = true
                for ((mood, bonus) in moodMap) {
                    scoreMap[mood] = (scoreMap[mood] ?: 0f) + bonus
                }
            }
        }

        // 5. Fallback if no strong track signals exist
        val maxExplicitScore = scoreMap.values.maxOrNull() ?: 0f
        val isFallbackOnly = totalSignalMagnitude < 0.8f && !artistPriorApplied
        if (isFallbackOnly && userMoodProfile?.dominantMood != null) {
            val dom = userMoodProfile.dominantMood
            scoreMap[dom] = (scoreMap[dom] ?: 0f) + 1.2f
        } else if (maxExplicitScore <= 0.01f) {
            scoreMap[ListeningMood.ROMANTIC_LOVE] = 0.8f
            scoreMap[ListeningMood.CHILL_RELAXING] = 0.7f
        }

        val sorted = scoreMap.entries.sortedByDescending { it.value }
        val primary = sorted.first().key
        val primaryScore = sorted.first().value
        val secondEntry = sorted.getOrNull(1)
        val secondary = if (secondEntry != null && secondEntry.value >= primaryScore * 0.45f && secondEntry.value > 0.3f) {
            secondEntry.key
        } else {
            MoodTransitionPolicy.compatibleMoods(primary).getOrNull(1)
        }

        // Honest confidence calculation reflecting real signal density
        val margin = if (secondEntry != null) (primaryScore - secondEntry.value).coerceAtLeast(0f) else primaryScore
        val baseConfidence = when {
            isFallbackOnly -> 0.28f
            lyricsValid && lyricsSignalMagnitude > 1.0f -> 0.62f + min(0.24f, primaryScore * 0.025f) + min(0.09f, margin * 0.03f)
            genreSignalMagnitude > 1.0f || totalSignalMagnitude >= 3.0f -> 0.52f + min(0.24f, primaryScore * 0.03f) + min(0.10f, margin * 0.03f)
            artistPriorApplied -> 0.42f + min(0.18f, primaryScore * 0.04f)
            else -> 0.35f + min(0.20f, primaryScore * 0.04f)
        }.coerceIn(0.20f, 0.96f)

        val evidenceSummary = when {
            lyricsValid && lyricsSignalMagnitude > 0.5f && hasGenreMetadata ->
                "Lyrics, genre & title metadata"
            lyricsValid && lyricsSignalMagnitude > 0.5f ->
                "Lyrics sentiment & track metadata"
            hasGenreMetadata && genreSignalMagnitude > 0.5f ->
                "Provider genre & title metadata"
            totalSignalMagnitude >= 1.5f ->
                "Title, album & artist metadata (lyrics unavailable)"
            artistPriorApplied ->
                "Artist & album style inference (metadata-only)"
            else ->
                "Metadata fallback (limited track tags)"
        }

        val totalSum = scoreMap.values.sum().takeIf { it > 0f } ?: 1f
        val normalizedScores = scoreMap.mapValues { (_, v) -> (v / totalSum).coerceIn(0f, 1f) }

        return SongMoodProfile(
            primaryMood = primary,
            secondaryMood = secondary,
            confidence = baseConfidence,
            isLyricsAnalyzed = lyricsValid,
            isProviderMetadataUsed = hasGenreMetadata,
            scores = normalizedScores,
            evidenceSummary = evidenceSummary
        )
    }

    fun buildUserAffinityProfile(
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        playStats: List<PersistedPlayStat>,
        playlistSongs: List<Song> = emptyList(),
        persistedMoodChoices: Map<String, Float> = emptyMap(),
        skippedSignals: List<Pair<String, ListeningMood?>> = emptyList()
    ): UserMoodAffinityProfile {
        val weights = EnumMap<ListeningMood, Float>(ListeningMood::class.java).apply {
            ListeningMood.entries.forEach { put(it, 0f) }
        }

        fun addSongSignal(song: Song, multiplier: Float) {
            val profile = analyzeSongMood(song)
            val confWeight = 0.55f + profile.confidence * 0.45f
            weights[profile.primaryMood] = (weights[profile.primaryMood] ?: 0f) + (multiplier * confWeight)
            profile.secondaryMood?.let { sec ->
                weights[sec] = (weights[sec] ?: 0f) + (multiplier * confWeight * 0.35f)
            }
        }

        favoriteSongs.forEach { addSongSignal(it, 2.4f) }
        playlistSongs.forEach { addSongSignal(it, 1.8f) }

        recentlyPlayed.forEachIndexed { index, song ->
            val decay = (1.0f - index * 0.045f).coerceAtLeast(0.25f)
            addSongSignal(song, 1.6f * decay)
        }

        playStats.forEach { stat ->
            val s = stat.song
            val mapped = Song(
                id = s.id,
                title = s.title,
                artist = s.artist,
                album = s.album,
                durationMs = s.durationMs,
                filePath = s.filePath,
                folderName = s.folderName,
                albumArtUri = s.albumArtUri,
                contentUriString = s.contentUriString,
                isOnline = s.isOnline,
                language = s.language,
                genre = s.genre,
                sourceProvider = s.sourceProvider
            )
            addSongSignal(mapped, 1.15f * min(stat.count, 6))
        }

        persistedMoodChoices.forEach { (moodId, weight) ->
            val mood = ListeningMood.fromId(moodId) ?: return@forEach
            weights[mood] = (weights[mood] ?: 0f) + (weight * 2.8f)
        }

        skippedSignals.forEach { (_, mood) ->
            if (mood != null) {
                weights[mood] = ((weights[mood] ?: 0f) - 1.1f).coerceAtLeast(0f)
            }
        }

        val allSongs = favoriteSongs + recentlyPlayed + playlistSongs
        val favoriteLanguages = allSongs
            .mapNotNull { HomeLanguagePolicy.normalizeLanguage(it.language) }
            .filter { it in HomeLanguagePolicy.ALLOWED_LANGUAGES }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val favoriteArtists = allSongs
            .flatMap { it.artist.split(",") }
            .map { it.trim() }
            .filter { it.length >= 3 && !it.equals("Unknown Artist", true) && !it.equals("Various Artists", true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(8)

        val recentTrail = recentlyPlayed
            .take(6)
            .map { analyzeSongMood(it).primaryMood }
            .distinct()

        val dominantMood = weights.entries
            .filter { it.value > 0.1f }
            .maxByOrNull { it.value }
            ?.key

        return UserMoodAffinityProfile(
            moodWeights = weights.toMap(),
            dominantMood = dominantMood,
            favoriteLanguages = favoriteLanguages,
            favoriteArtists = favoriteArtists,
            recentMoodTrail = recentTrail
        )
    }

    fun selectNextMoodTrack(
        currentSong: Song,
        currentMoodProfile: SongMoodProfile,
        candidatePool: List<Song>,
        recentlyPlayed: List<Song> = emptyList(),
        favoriteSongs: List<Song> = emptyList(),
        playStats: List<PersistedPlayStat> = emptyList(),
        skippedKeys: Set<String> = emptySet(),
        recentAutoplayKeys: Set<String> = emptySet(),
        recentArtists: List<String> = emptyList(),
        userMoodProfile: UserMoodAffinityProfile = UserMoodAffinityProfile(),
        sessionSeed: Long = 0L,
        recommendationPreference: RecommendationPreference = RecommendationPreference.BALANCED,
        excludeRecentlyPlayed: Boolean = true
    ): MoodNextRecommendation? {
        val currentKey = HomeUserSignalsStore.songIdentityKey(currentSong)
        val currentNormTitle = normalizeTitleArtistKey(currentSong)
        val currentLang = inferNormalizedSongLanguage(currentSong)
        val currentGenreLower = currentSong.genre.orEmpty().lowercase()

        val recentTopKeys = recentlyPlayed.take(8).mapTo(hashSetOf()) { HomeUserSignalsStore.songIdentityKey(it) }
        val favoriteKeys = favoriteSongs.mapTo(hashSetOf()) { HomeUserSignalsStore.songIdentityKey(it) }
        val playCountByKey = playStats.associate { stat ->
            val key = if (stat.song.isOnline) {
                "online|${stat.song.sourceProvider.orEmpty().lowercase()}|${stat.song.filePath.ifBlank { stat.song.id.toString() }}"
            } else {
                "local|${stat.song.id}|${stat.song.filePath}"
            }
            key to stat.count
        }
        val maxAffinity = userMoodProfile.moodWeights.values.maxOrNull()?.takeIf { it > 0f } ?: 1f

        val validCandidates = candidatePool
            .asSequence()
            .filter { candidate ->
                candidate.playbackType == PlaybackType.FULL &&
                    (!candidate.isOnline || HomeLanguagePolicy.isAllowedJioSaavnSong(candidate)) &&
                    (!candidate.isOnline || !candidate.contentUriString.isNullOrBlank() || candidate.isStreamable)
            }
            .filter { candidate ->
                HomeUserSignalsStore.songIdentityKey(candidate) != currentKey &&
                    normalizeTitleArtistKey(candidate) != currentNormTitle
            }
            .distinctBy { HomeUserSignalsStore.songIdentityKey(it) }
            .toList()

        if (validCandidates.isEmpty()) return null

        // Prefer candidates that do NOT trigger an abrupt mood clash (e.g., never Sad -> Party)
        val nonClashing = validCandidates.filter { cand ->
            val candMood = analyzeSongMood(itOrEmpty(cand)).primaryMood
            !MoodTransitionPolicy.isAbruptClash(currentMoodProfile.primaryMood, candMood)
        }
        val effectivePool = if (nonClashing.isNotEmpty()) nonClashing else validCandidates

        var bestCandidate: Song? = null
        var bestMoodProfile: SongMoodProfile? = null
        var bestScore = Float.NEGATIVE_INFINITY
        var bestReason = ""

        for (candidate in effectivePool) {
            val candKey = HomeUserSignalsStore.songIdentityKey(candidate)
            val candProfile = analyzeSongMood(candidate, userMoodProfile = userMoodProfile)
            val candLang = inferNormalizedSongLanguage(candidate)

            // 1. Mood similarity + natural transition compatibility (0..10 scale)
            val primaryCompat = MoodTransitionPolicy.compatibility(currentMoodProfile.primaryMood, candProfile.primaryMood)
            val secondaryCompat = currentMoodProfile.secondaryMood?.let {
                MoodTransitionPolicy.compatibility(it, candProfile.primaryMood)
            } ?: 0f

            val moodWeightMultiplier = when (recommendationPreference) {
                RecommendationPreference.SIMILAR_MOOD -> 11.5f
                RecommendationPreference.BALANCED -> 8.5f
                RecommendationPreference.DISCOVER_NEW -> 6.8f
            }
            var score = primaryCompat * moodWeightMultiplier + secondaryCompat * 1.8f
            if (MoodTransitionPolicy.isAbruptClash(currentMoodProfile.primaryMood, candProfile.primaryMood)) {
                score -= 24f
            }

            // 2. Language coherence
            when {
                currentLang != null && candLang != null && currentLang == candLang -> score += 3.8f
                currentLang != null && candLang != null && currentLang != candLang -> score -= 3.4f
                currentLang != null && candLang == null -> score += 0.3f
            }

            // 3. Genre resonance
            val candGenreLower = candidate.genre.orEmpty().lowercase()
            if (currentGenreLower.isNotBlank() && candGenreLower.isNotBlank()) {
                val curTokens = currentGenreLower.split(' ', ',', '/', '&').filter { it.length >= 3 }.toSet()
                val candTokens = candGenreLower.split(' ', ',', '/', '&').filter { it.length >= 3 }.toSet()
                if (curTokens.intersect(candTokens).isNotEmpty()) {
                    score += 1.4f
                }
            }

            // 4. Artist variety (prevent endless same-artist loops, reward fresh artist with same mood)
            val sharesArtistWithCurrent = ArtistMatchingPolicy.songMatchesArtist(candidate, currentSong.artist) ||
                ArtistMatchingPolicy.artistsMatch(candidate.artist, currentSong.artist)
            val isRecentArtist = recentArtists.any { recentArtist ->
                recentArtist.isNotBlank() && ArtistMatchingPolicy.artistsMatch(candidate.artist, recentArtist)
            }
            val discoverBoost = if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) 1.7f else 1.0f
            when {
                sharesArtistWithCurrent -> score -= 2.4f * discoverBoost
                isRecentArtist -> score -= 1.5f * discoverBoost
                else -> score += 1.1f * discoverBoost
            }

            // 5. User mood affinity + listening history
            val affinityWeight = (userMoodProfile.moodWeights[candProfile.primaryMood] ?: 0f) / maxAffinity
            score += affinityWeight * 1.4f
            if (candKey in favoriteKeys) {
                score += if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) 0.15f else 0.75f
            }
            val playCount = playCountByKey[candKey] ?: 0
            if (playCount > 0) {
                score += if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) {
                    -min(playCount, 5) * 0.65f
                } else {
                    min(playCount, 5) * 0.22f
                }
            } else if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) {
                score += 1.6f
            }

            // 6. Anti-repeat & skip penalties
            if (candKey in recentTopKeys) {
                score -= if (excludeRecentlyPlayed) 8.5f else 1.8f
            }
            if (candKey in recentAutoplayKeys) score -= 15.0f
            if (candKey in skippedKeys) score -= 12.0f

            // 7. Stable session-seed tie-breaker
            val jitterWeight = if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) 0.95f else 0.38f
            val jitter = (abs(deterministicHash(sessionSeed, candKey) % 1000L) / 1000f) * jitterWeight
            score += jitter

            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
                bestMoodProfile = candProfile
                val langLabel = candLang?.replaceFirstChar { it.uppercase() }
                bestReason = buildString {
                    append(candProfile.primaryMood.shortLabel)
                    if (!langLabel.isNullOrBlank()) {
                        append(" • ")
                        append(langLabel)
                    }
                    if (candProfile.primaryMood == currentMoodProfile.primaryMood) {
                        append(" vibe match")
                    } else {
                        append(" smooth transition")
                    }
                }
            }
        }

        val winner = bestCandidate ?: return null
        val winnerProfile = bestMoodProfile ?: return null
        val combinedConfidence = ((currentMoodProfile.confidence + winnerProfile.confidence) * 0.5f).coerceIn(0.25f, 0.96f)

        return MoodNextRecommendation(
            song = winner,
            score = bestScore,
            targetMood = winnerProfile.primaryMood,
            sourceMood = currentMoodProfile.primaryMood,
            confidence = combinedConfidence,
            reason = bestReason
        )
    }

    private fun itOrEmpty(song: Song): Song = song

    fun buildNextSongDiscoveryQueries(
        profile: SongMoodProfile,
        language: String?,
        sessionSeed: Long = 0L
    ): List<String> {
        val normLang = HomeLanguagePolicy.normalizeLanguage(language)
        val langKey = if (normLang != null && normLang in HomeLanguagePolicy.ALLOWED_LANGUAGES) normLang else "all"

        val primarySeeds = profile.primaryMood.querySeedsByLanguage[langKey]
            ?: profile.primaryMood.querySeedsByLanguage["all"].orEmpty()
        val compatibleMoods = MoodTransitionPolicy.compatibleMoods(profile.primaryMood, minScore = 0.65f)
            .filter { it != profile.primaryMood }
        val secondarySeeds = compatibleMoods.firstOrNull()?.let { compat ->
            compat.querySeedsByLanguage[langKey] ?: compat.querySeedsByLanguage["all"]
        }.orEmpty()

        val rotatedPrimary = rotateListForSession(primarySeeds, sessionSeed, salt = "next_primary")
        val rotatedSecondary = rotateListForSession(secondarySeeds, sessionSeed, salt = "next_secondary")
        return (rotatedPrimary + rotatedSecondary.take(1)).distinct()
    }

    fun scoreAndRotateSongsForSession(
        songs: List<Song>,
        sessionSeed: Long,
        sectionSalt: String,
        userMoodProfile: UserMoodAffinityProfile,
        previousShownKeys: Set<String> = emptySet(),
        targetLanguage: HomeLanguageFilter = HomeLanguageFilter.ALL,
        prioritizeMoodMatch: Boolean = false,
        recommendationPreference: RecommendationPreference = RecommendationPreference.BALANCED,
        recentlyPlayedKeys: Set<String> = emptySet(),
        excludeRecentlyPlayed: Boolean = false
    ): List<Song> {
        if (songs.size <= 1) return songs
        if (sessionSeed == 0L && previousShownKeys.isEmpty() && !prioritizeMoodMatch && !excludeRecentlyPlayed) {
            return songs
        }
        val dominant = userMoodProfile.dominantMood
        val favArtistsLower = userMoodProfile.favoriteArtists.map { it.lowercase() }

        return songs.sortedByDescending { song ->
            val key = HomeUserSignalsStore.songIdentityKey(song)
            var score = 0f

            // Penalize items already prominently shown in previous app session so Home feels fresh
            if (key in previousShownKeys) {
                score -= if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) 3.6f else 2.4f
            }
            if (excludeRecentlyPlayed && key in recentlyPlayedKeys) {
                score -= 4.5f
            }

            // Reward explicit language match when filter active
            val requiredLang = targetLanguage.jioSaavnLanguage
            if (requiredLang != null && HomeLanguagePolicy.normalizeLanguage(song.language) == requiredLang) {
                score += 1.6f
            }

            // Subtle affinity boost
            if (dominant != null) {
                val songMood = analyzeSongMood(song, userMoodProfile = userMoodProfile).primaryMood
                val compat = MoodTransitionPolicy.compatibility(dominant, songMood)
                val prefMult = when (recommendationPreference) {
                    RecommendationPreference.SIMILAR_MOOD -> 1.55f
                    RecommendationPreference.BALANCED -> 1.0f
                    RecommendationPreference.DISCOVER_NEW -> 0.7f
                }
                score += compat * (if (prioritizeMoodMatch) 3.2f else 1.4f) * prefMult
            }
            if (favArtistsLower.any { artist -> ArtistMatchingPolicy.songMatchesArtist(song, artist) }) {
                score += if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) -0.4f else 1.2f
            }

            // Deterministic session permutation so same session stays 100% stable across recomposition/scroll
            val hash = deterministicHash(sessionSeed, "$sectionSalt|$key")
            val jitterSpan = if (recommendationPreference == RecommendationPreference.DISCOVER_NEW) 3.2f else 2.2f
            score += (abs(hash % 10_000L) / 10_000f) * jitterSpan
            score
        }
    }

    fun <T> rotateListForSession(
        items: List<T>,
        sessionSeed: Long,
        salt: String,
        keyExtractor: ((T) -> String)? = null,
        previousShownKeys: Set<String> = emptySet()
    ): List<T> {
        if (items.size <= 1 || sessionSeed == 0L) return items
        return items.sortedByDescending { item ->
            val itemKey = keyExtractor?.invoke(item) ?: item.toString()
            val freshPenalty = if (itemKey in previousShownKeys) -10_000L else 0L
            val hash = abs(deterministicHash(sessionSeed, "$salt|$itemKey") % 10_000L)
            freshPenalty + hash
        }
    }

    fun deterministicHash(seed: Long, key: String): Long {
        var h = seed xor -0x61c8864680b583ebL
        for (ch in key) {
            h = (h xor ch.code.toLong()) * 0x100000001b3L
        }
        h = h xor (h ushr 33)
        h *= -0xae502812aa7333L
        h = h xor (h ushr 33)
        return h
    }

    fun inferNormalizedSongLanguage(song: Song): String? {
        HomeLanguagePolicy.normalizeLanguage(song.language)?.let { norm ->
            if (norm in HomeLanguagePolicy.ALLOWED_LANGUAGES) return norm
        }
        val combined = "${song.title} ${song.album}".lowercase()
        return when {
            combined.contains("telugu") || combined.contains("tollywood") -> "telugu"
            combined.contains("tamil") || combined.contains("kollywood") -> "tamil"
            combined.contains("hindi") || combined.contains("bollywood") -> "hindi"
            combined.contains("english") -> "english"
            else -> null
        }
    }

    private fun normalizeTitleArtistKey(song: Song): String {
        return "${song.title.trim().lowercase()}|${song.artist.trim().lowercase()}"
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        if (keyword.isBlank() || !text.contains(keyword)) return false
        if (' ' in keyword) return true
        val idx = text.indexOf(keyword)
        var pos = idx
        while (pos >= 0) {
            val leftOk = pos == 0 || !text[pos - 1].isLetterOrDigit()
            val end = pos + keyword.length
            val rightOk = end >= text.length || !text[end].isLetterOrDigit()
            if (leftOk && rightOk) return true
            pos = text.indexOf(keyword, startIndex = pos + 1)
        }
        return false
    }
}
