package com.usman.musicplayer.model

import androidx.compose.runtime.Immutable

/**
 * Supported listening moods recognized by the USMAN MUSIC AI Mood Intelligence Engine.
 */
enum class ListeningMood(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val subtitle: String,
    val accentHex: Long,
    val querySeedsByLanguage: Map<String, List<String>>
) {
    SAD_HEARTBREAK(
        id = "sad_heartbreak",
        displayName = "Sad / Heartbreak",
        shortLabel = "Sad & Heartbreak",
        subtitle = "Heartbreak ballads & soulful healing tracks",
        accentHex = 0xFF60A5FAL,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("hindi sad heartbreak songs", "arijit singh sad songs", "emotional hindi breakup songs"),
            "telugu" to listOf("telugu sad breakup songs", "telugu emotional heartbreak songs", "sid sriram sad telugu"),
            "tamil" to listOf("tamil sad love failure songs", "tamil emotional heartbreak songs", "yuvan sad tamil songs"),
            "english" to listOf("sad heartbreak pop songs", "emotional breakup ballads english", "billie eilish sad songs"),
            "all" to listOf("sad heartbreak emotional songs", "soulful breakup hits", "hindi telugu sad songs")
        )
    ),
    ROMANTIC_LOVE(
        id = "romantic_love",
        displayName = "Romantic / Love",
        shortLabel = "Romantic",
        subtitle = "Warm melodies & timeless love anthems",
        accentHex = 0xFFF472B6L,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("hindi romantic love songs", "bollywood romantic hits", "arijit singh love songs"),
            "telugu" to listOf("telugu romantic love songs", "telugu melody love hits", "sid sriram telugu love"),
            "tamil" to listOf("tamil romantic love songs", "tamil melody love hits", "ar rahman tamil romantic"),
            "english" to listOf("romantic love pop songs", "english love ballads", "ed sheeran love songs"),
            "all" to listOf("romantic love hits", "top melody love songs", "bollywood telugu romantic songs")
        )
    ),
    HAPPY_FEEL_GOOD(
        id = "happy_feel_good",
        displayName = "Happy / Feel Good",
        shortLabel = "Feel Good",
        subtitle = "Bright, uplifting & joyful tunes",
        accentHex = 0xFFFACC15L,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("happy feel good hindi songs", "uplifting bollywood hits", "cheerful hindi pop"),
            "telugu" to listOf("feel good telugu songs", "happy breezy telugu hits", "telugu cheerful melody"),
            "tamil" to listOf("feel good tamil songs", "happy breezy tamil hits", "tamil uplifting songs"),
            "english" to listOf("happy feel good pop hits", "uplifting english pop", "sunshine feel good songs"),
            "all" to listOf("happy feel good hits", "uplifting pop melodies", "cheerful hits")
        )
    ),
    CHILL_RELAXING(
        id = "chill_relaxing",
        displayName = "Chill / Relaxing",
        shortLabel = "Chill",
        subtitle = "Soft acoustic, lofi & easy-going vibes",
        accentHex = 0xFF34D399L,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("chill lofi hindi songs", "acoustic unplugged hindi", "relaxing hindi melody"),
            "telugu" to listOf("chill telugu melody songs", "soft relaxing telugu hits", "telugu unplugged acoustic"),
            "tamil" to listOf("chill tamil melody songs", "soft relaxing tamil hits", "tamil acoustic unplugged"),
            "english" to listOf("chill lofi english pop", "relaxing acoustic chill songs", "mellow indie chill"),
            "all" to listOf("chill relaxing lofi hits", "soft acoustic unplugged melodies", "mellow chill vibes")
        )
    ),
    EMOTIONAL(
        id = "emotional",
        displayName = "Emotional",
        shortLabel = "Emotional",
        subtitle = "Deeply expressive & heartfelt songs",
        accentHex = 0xFFA78BFAL,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("emotional soulful hindi songs", "deep heartfelt bollywood songs", "arijit singh soulful"),
            "telugu" to listOf("telugu emotional soulful songs", "heartfelt telugu melodies", "deep telugu emotion songs"),
            "tamil" to listOf("tamil emotional soulful songs", "heartfelt tamil melodies", "ar rahman soulful tamil"),
            "english" to listOf("emotional soulful english songs", "deep heartfelt pop ballads", "emotional acoustic english"),
            "all" to listOf("emotional soulful hits", "heartfelt deep melodies", "soulful ballads")
        )
    ),
    MOTIVATIONAL(
        id = "motivational",
        displayName = "Motivational",
        shortLabel = "Motivational",
        subtitle = "Inspiring anthems to keep pushing forward",
        accentHex = 0xFFFB923CL,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("hindi motivational inspirational songs", "bollywood workout motivation", "zinda lakshya motivational"),
            "telugu" to listOf("telugu motivational inspirational songs", "telugu uplifting hero theme", "telugu workout motivation"),
            "tamil" to listOf("tamil motivational inspirational songs", "anirudh motivational tamil", "tamil workout motivation"),
            "english" to listOf("motivational workout pop rock", "inspiring anthem songs english", "uplifting gym motivation"),
            "all" to listOf("motivational inspirational songs", "workout motivation anthems", "inspiring hits")
        )
    ),
    ENERGETIC(
        id = "energetic",
        displayName = "Energetic",
        shortLabel = "Energetic",
        subtitle = "High-voltage beats & adrenaline tracks",
        accentHex = 0xFFF87171L,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("high energy hindi workout songs", "energetic bollywood rock rap", "power packed hindi hits"),
            "telugu" to listOf("telugu mass high energy songs", "thaman dsp energetic telugu", "telugu mass beats"),
            "tamil" to listOf("tamil mass high energy songs", "anirudh energetic tamil", "tamil kuthu mass beats"),
            "english" to listOf("high energy pop rock hits", "energetic workout hip hop", "adrenaline pump english"),
            "all" to listOf("high energy mass hits", "energetic workout beats", "power packed hits")
        )
    ),
    PARTY_DANCE(
        id = "party_dance",
        displayName = "Party / Dance",
        shortLabel = "Party",
        subtitle = "Club bangers & dance floor hits",
        accentHex = 0xFFE879F9L,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("hindi party dance hits", "bollywood club dj songs", "dance floor hindi hits"),
            "telugu" to listOf("telugu party dance hits", "telugu dj dance beats", "tollywood party songs"),
            "tamil" to listOf("tamil party dance hits", "tamil dj kuthu dance", "kollywood party songs"),
            "english" to listOf("english edm dance party hits", "club pop dance anthems", "electronic dance hits"),
            "all" to listOf("party dance club hits", "dj dance floor anthems", "edm bollywood party")
        )
    ),
    PEACEFUL_NIGHT(
        id = "peaceful_night",
        displayName = "Peaceful / Night Vibes",
        shortLabel = "Night Vibes",
        subtitle = "Calm late-night melodies & serene skies",
        accentHex = 0xFF38BDF8L,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("late night peaceful hindi songs", "calm sufi night vibes hindi", "slow romantic night bollywood"),
            "telugu" to listOf("late night telugu melody songs", "peaceful calm telugu songs", "midnight telugu vibes"),
            "tamil" to listOf("late night tamil melody songs", "peaceful ilaiyaraaja rahman night", "calm tamil night vibes"),
            "english" to listOf("late night chill vibes english", "peaceful midnight ambient pop", "calm sleep acoustic"),
            "all" to listOf("peaceful late night vibes", "calm midnight melodies", "serene night chill")
        )
    ),
    FOCUS_STUDY(
        id = "focus_study",
        displayName = "Focus / Study",
        shortLabel = "Focus",
        subtitle = "Instrumental, ambient & distraction-free flow",
        accentHex = 0xFF2DD4BFL,
        querySeedsByLanguage = mapOf(
            "hindi" to listOf("hindi instrumental flute piano", "calm study instrumental bollywood", "peaceful hindi bgm"),
            "telugu" to listOf("telugu instrumental bgm piano", "calm telugu flute instrumental", "soothing telugu bgm"),
            "tamil" to listOf("tamil instrumental bgm piano", "ilayaraja rahman instrumental", "soothing tamil bgm"),
            "english" to listOf("focus study lofi instrumental", "ambient piano study music", "deep focus instrumental"),
            "all" to listOf("focus study instrumental", "ambient piano lofi study", "calm bgm instrumental")
        )
    );

    companion object {
        fun fromId(raw: String?): ListeningMood? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) || it.name.equals(raw.trim(), ignoreCase = true) }
        }
    }
}

enum class RecommendationPreference(
    val id: String,
    val displayName: String,
    val description: String
) {
    BALANCED(
        id = "balanced",
        displayName = "Balanced",
        description = "Smooth blend of your current mood and fresh tracks"
    ),
    SIMILAR_MOOD(
        id = "similar_mood",
        displayName = "Similar Mood",
        description = "Stay closely locked to the current emotional vibe"
    ),
    DISCOVER_NEW(
        id = "discover_new",
        displayName = "Discover New",
        description = "Prioritize fresh artists & new songs within safe vibes"
    );

    companion object {
        fun fromId(raw: String?): RecommendationPreference {
            if (raw.isNullOrBlank()) return BALANCED
            return entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) || it.name.equals(raw.trim(), ignoreCase = true) }
                ?: BALANCED
        }
    }
}

@Immutable
data class SongMoodProfile(
    val primaryMood: ListeningMood,
    val secondaryMood: ListeningMood? = null,
    val confidence: Float,
    val isLyricsAnalyzed: Boolean = false,
    val isProviderMetadataUsed: Boolean = false,
    val scores: Map<ListeningMood, Float> = emptyMap(),
    val evidenceSummary: String = "Metadata analysis"
)

@Immutable
data class UserMoodAffinityProfile(
    val moodWeights: Map<ListeningMood, Float> = emptyMap(),
    val dominantMood: ListeningMood? = null,
    val favoriteLanguages: List<String> = emptyList(),
    val favoriteArtists: List<String> = emptyList(),
    val recentMoodTrail: List<ListeningMood> = emptyList()
)

@Immutable
data class MoodNextRecommendation(
    val song: Song,
    val score: Float,
    val targetMood: ListeningMood,
    val sourceMood: ListeningMood,
    val confidence: Float,
    val reason: String
)

/**
 * Defines smooth, natural mood transitions and prevents jarring switches
 * (e.g., never jumping directly from Sad / Heartbreak to Party / Dance).
 */
object MoodTransitionPolicy {

    private val transitionMatrix: Map<ListeningMood, Map<ListeningMood, Float>> = mapOf(
        ListeningMood.SAD_HEARTBREAK to mapOf(
            ListeningMood.SAD_HEARTBREAK to 1.00f,
            ListeningMood.EMOTIONAL to 0.90f,
            ListeningMood.PEACEFUL_NIGHT to 0.72f,
            ListeningMood.CHILL_RELAXING to 0.56f,
            ListeningMood.ROMANTIC_LOVE to 0.44f,
            ListeningMood.FOCUS_STUDY to 0.40f,
            ListeningMood.MOTIVATIONAL to 0.22f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.14f,
            ListeningMood.ENERGETIC to 0.08f,
            ListeningMood.PARTY_DANCE to 0.04f
        ),
        ListeningMood.EMOTIONAL to mapOf(
            ListeningMood.EMOTIONAL to 1.00f,
            ListeningMood.SAD_HEARTBREAK to 0.88f,
            ListeningMood.ROMANTIC_LOVE to 0.80f,
            ListeningMood.PEACEFUL_NIGHT to 0.74f,
            ListeningMood.CHILL_RELAXING to 0.66f,
            ListeningMood.MOTIVATIONAL to 0.45f,
            ListeningMood.FOCUS_STUDY to 0.42f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.30f,
            ListeningMood.ENERGETIC to 0.18f,
            ListeningMood.PARTY_DANCE to 0.08f
        ),
        ListeningMood.ROMANTIC_LOVE to mapOf(
            ListeningMood.ROMANTIC_LOVE to 1.00f,
            ListeningMood.EMOTIONAL to 0.80f,
            ListeningMood.CHILL_RELAXING to 0.76f,
            ListeningMood.PEACEFUL_NIGHT to 0.72f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.66f,
            ListeningMood.SAD_HEARTBREAK to 0.44f,
            ListeningMood.FOCUS_STUDY to 0.40f,
            ListeningMood.MOTIVATIONAL to 0.30f,
            ListeningMood.ENERGETIC to 0.24f,
            ListeningMood.PARTY_DANCE to 0.18f
        ),
        ListeningMood.HAPPY_FEEL_GOOD to mapOf(
            ListeningMood.HAPPY_FEEL_GOOD to 1.00f,
            ListeningMood.ROMANTIC_LOVE to 0.74f,
            ListeningMood.CHILL_RELAXING to 0.70f,
            ListeningMood.PARTY_DANCE to 0.72f,
            ListeningMood.MOTIVATIONAL to 0.68f,
            ListeningMood.ENERGETIC to 0.66f,
            ListeningMood.PEACEFUL_NIGHT to 0.42f,
            ListeningMood.EMOTIONAL to 0.32f,
            ListeningMood.FOCUS_STUDY to 0.34f,
            ListeningMood.SAD_HEARTBREAK to 0.12f
        ),
        ListeningMood.CHILL_RELAXING to mapOf(
            ListeningMood.CHILL_RELAXING to 1.00f,
            ListeningMood.PEACEFUL_NIGHT to 0.88f,
            ListeningMood.FOCUS_STUDY to 0.78f,
            ListeningMood.ROMANTIC_LOVE to 0.74f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.64f,
            ListeningMood.EMOTIONAL to 0.60f,
            ListeningMood.SAD_HEARTBREAK to 0.50f,
            ListeningMood.MOTIVATIONAL to 0.30f,
            ListeningMood.ENERGETIC to 0.16f,
            ListeningMood.PARTY_DANCE to 0.12f
        ),
        ListeningMood.MOTIVATIONAL to mapOf(
            ListeningMood.MOTIVATIONAL to 1.00f,
            ListeningMood.ENERGETIC to 0.88f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.68f,
            ListeningMood.PARTY_DANCE to 0.56f,
            ListeningMood.EMOTIONAL to 0.46f,
            ListeningMood.FOCUS_STUDY to 0.36f,
            ListeningMood.CHILL_RELAXING to 0.28f,
            ListeningMood.ROMANTIC_LOVE to 0.24f,
            ListeningMood.PEACEFUL_NIGHT to 0.20f,
            ListeningMood.SAD_HEARTBREAK to 0.18f
        ),
        ListeningMood.ENERGETIC to mapOf(
            ListeningMood.ENERGETIC to 1.00f,
            ListeningMood.MOTIVATIONAL to 0.86f,
            ListeningMood.PARTY_DANCE to 0.84f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.66f,
            ListeningMood.ROMANTIC_LOVE to 0.24f,
            ListeningMood.CHILL_RELAXING to 0.16f,
            ListeningMood.EMOTIONAL to 0.16f,
            ListeningMood.FOCUS_STUDY to 0.14f,
            ListeningMood.PEACEFUL_NIGHT to 0.10f,
            ListeningMood.SAD_HEARTBREAK to 0.06f
        ),
        ListeningMood.PARTY_DANCE to mapOf(
            ListeningMood.PARTY_DANCE to 1.00f,
            ListeningMood.ENERGETIC to 0.86f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.76f,
            ListeningMood.MOTIVATIONAL to 0.54f,
            ListeningMood.ROMANTIC_LOVE to 0.22f,
            ListeningMood.CHILL_RELAXING to 0.12f,
            ListeningMood.EMOTIONAL to 0.08f,
            ListeningMood.FOCUS_STUDY to 0.06f,
            ListeningMood.PEACEFUL_NIGHT to 0.06f,
            ListeningMood.SAD_HEARTBREAK to 0.04f
        ),
        ListeningMood.PEACEFUL_NIGHT to mapOf(
            ListeningMood.PEACEFUL_NIGHT to 1.00f,
            ListeningMood.CHILL_RELAXING to 0.88f,
            ListeningMood.FOCUS_STUDY to 0.82f,
            ListeningMood.ROMANTIC_LOVE to 0.72f,
            ListeningMood.EMOTIONAL to 0.70f,
            ListeningMood.SAD_HEARTBREAK to 0.66f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.40f,
            ListeningMood.MOTIVATIONAL to 0.20f,
            ListeningMood.ENERGETIC to 0.08f,
            ListeningMood.PARTY_DANCE to 0.05f
        ),
        ListeningMood.FOCUS_STUDY to mapOf(
            ListeningMood.FOCUS_STUDY to 1.00f,
            ListeningMood.PEACEFUL_NIGHT to 0.84f,
            ListeningMood.CHILL_RELAXING to 0.82f,
            ListeningMood.EMOTIONAL to 0.44f,
            ListeningMood.ROMANTIC_LOVE to 0.40f,
            ListeningMood.SAD_HEARTBREAK to 0.36f,
            ListeningMood.HAPPY_FEEL_GOOD to 0.34f,
            ListeningMood.MOTIVATIONAL to 0.32f,
            ListeningMood.ENERGETIC to 0.12f,
            ListeningMood.PARTY_DANCE to 0.06f
        )
    )

    fun compatibility(source: ListeningMood, target: ListeningMood): Float {
        if (source == target) return 1.0f
        return transitionMatrix[source]?.get(target) ?: 0.25f
    }

    fun isAbruptClash(source: ListeningMood, target: ListeningMood): Boolean {
        return compatibility(source, target) < 0.25f
    }

    fun compatibleMoods(source: ListeningMood, minScore: Float = 0.44f): List<ListeningMood> {
        return ListeningMood.entries
            .map { it to compatibility(source, it) }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
