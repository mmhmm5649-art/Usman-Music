package com.usman.musicplayer.data.repository

import com.usman.musicplayer.data.recommendation.AiMoodIntelligenceEngine
import com.usman.musicplayer.model.ArtistMatchingPolicy
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.HomeSectionDefinition
import com.usman.musicplayer.model.ListeningMood
import kotlin.math.abs

object HomeDiscoveryCatalog {

    // Core Spotify-style Discovery Sections (shown in ALL filter + adapted per active language)
    val DISCOVERY_SECTIONS = listOf(
        HomeSectionDefinition(
            id = "discovery_trending_now",
            title = "Trending Now",
            subtitle = "Most played tracks across Telugu, Tamil, Hindi & English",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "trending hits 2025 2026",
            fallbackQueries = listOf("top hits", "viral hits"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "discovery_made_for_you",
            title = "Made For You",
            subtitle = "Curated mixes blended for your vibe",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.PLAYLISTS,
            query = "top hits playlist",
            fallbackQueries = listOf("telugu hits", "hindi hits"),
            isPersonalized = true,
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "discovery_mood_mixes",
            title = "Daily Mood Mix",
            subtitle = "AI-curated mixes tuned to how you feel right now",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.PLAYLISTS,
            query = "romantic chill motivational hits",
            isPersonalized = true,
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "discovery_your_current_mood",
            title = "Your Current Mood",
            subtitle = "Songs matching your active listening vibe",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "soulful romantic chill melody hits",
            isPersonalized = true,
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "discovery_because_you_listened",
            title = "Because You Listened To...",
            subtitle = "Curated full-length tracks inspired by your recent favourites",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "popular melody love hits",
            isPersonalized = true
        ),
        HomeSectionDefinition(
            id = "discovery_continue_your_vibe",
            title = "Continue Your Vibe",
            subtitle = "Natural mood transitions without sudden jumps",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "melodic acoustic chill hits",
            isPersonalized = true
        ),
        HomeSectionDefinition(
            id = "discovery_recently_played_moods",
            title = "Recently Played Moods",
            subtitle = "Jump right back into the vibes you enjoyed lately",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.PLAYLISTS,
            query = "top mood mixes",
            isPersonalized = true
        ),
        HomeSectionDefinition(
            id = "discovery_recommended_for_you",
            title = "Recommended For You",
            subtitle = "Inspired by your artists, favourites & recent plays",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "popular melody love hits",
            isPersonalized = true
        ),
        HomeSectionDefinition(
            id = "discovery_heavy_rotation",
            title = "Your Heavy Rotation",
            subtitle = "The tracks you keep coming back to",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "most played chartbusters",
            isPersonalized = true
        ),
        HomeSectionDefinition(
            id = "discovery_discover_weekly",
            title = "Discover Something New",
            subtitle = "Fresh recommendations across your favourite languages",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "fresh new music hits",
            isPersonalized = true
        ),
        HomeSectionDefinition(
            id = "discovery_new_releases",
            title = "New Releases",
            subtitle = "Latest drops in Telugu, Tamil, English & Hindi",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            query = "latest songs 2025 2026",
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "discovery_featured_albums",
            title = "Featured Albums",
            subtitle = "Full official albums ready to stream",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.ALBUMS,
            query = "latest hit albums 2025",
            fallbackQueries = listOf("popular albums"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "discovery_popular_artists",
            title = "Popular Artists",
            subtitle = "Top Telugu, Tamil, Hindi & English voices",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.ARTISTS,
            query = "Anirudh Ravichander, Sid Sriram, Arijit Singh, A. R. Rahman, Taylor Swift",
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "discovery_movie_soundtracks",
            title = "Movie Soundtracks",
            subtitle = "Official cinema OST collections & blockbuster scores",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.ALBUMS,
            query = "original motion picture soundtrack",
            fallbackQueries = listOf("movie soundtrack hits"),
            isMovieSoundtrack = true
        )
    )

    // 10 Dedicated TELUGU Sections
    val TELUGU_SECTIONS = listOf(
        HomeSectionDefinition(
            id = "telugu_top_hits",
            title = "Telugu Top Hits",
            subtitle = "Biggest Tollywood chartbusters",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "telugu top hits",
            fallbackQueries = listOf("telugu hits", "tollywood hits"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "telugu_latest_songs",
            title = "Latest Telugu Songs",
            subtitle = "Brand new Tollywood singles & soundtrack drops",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "latest telugu songs 2025 2026",
            fallbackQueries = listOf("latest telugu songs"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "telugu_new_releases",
            title = "New Telugu Releases",
            subtitle = "Fresh Telugu releases just added",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "new telugu songs 2026",
            fallbackQueries = listOf("new telugu releases")
        ),
        HomeSectionDefinition(
            id = "telugu_trending_songs",
            title = "Trending Telugu Songs",
            subtitle = "Viral Tollywood tracks trending right now",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "trending telugu songs",
            fallbackQueries = listOf("telugu superhits")
        ),
        HomeSectionDefinition(
            id = "telugu_love_songs",
            title = "Telugu Love Songs",
            subtitle = "Heartfelt Tollywood love anthems",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "telugu love songs"
        ),
        HomeSectionDefinition(
            id = "telugu_melody_hits",
            title = "Telugu Melody Hits",
            subtitle = "Soothing Sid Sriram, Devi Sri Prasad & Thaman melodies",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "telugu melody hits"
        ),
        HomeSectionDefinition(
            id = "telugu_mass_hits",
            title = "Telugu Mass Hits",
            subtitle = "High-energy Tollywood dance & mass beats",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "telugu mass item dance hits"
        ),
        HomeSectionDefinition(
            id = "telugu_movie_soundtracks",
            title = "Telugu Movie Soundtracks",
            subtitle = "Complete Tollywood cinema soundtrack albums",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.ALBUMS,
            query = "telugu movie soundtrack",
            fallbackQueries = listOf("pushpa telugu", "devara telugu"),
            isMovieSoundtrack = true
        ),
        HomeSectionDefinition(
            id = "telugu_romantic_hits",
            title = "Telugu Romantic Hits",
            subtitle = "Late-night romantic Telugu favourites",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.SONGS,
            query = "telugu romantic hits"
        ),
        HomeSectionDefinition(
            id = "telugu_popular_albums",
            title = "Popular Telugu Albums",
            subtitle = "Most streamed Telugu albums & OSTs",
            language = HomeLanguageFilter.TELUGU,
            contentType = HomeSectionContentType.ALBUMS,
            query = "popular telugu albums",
            fallbackQueries = listOf("telugu hit movies")
        )
    )

    // 10 Dedicated TAMIL Sections
    val TAMIL_SECTIONS = listOf(
        HomeSectionDefinition(
            id = "tamil_top_hits",
            title = "Tamil Top Hits",
            subtitle = "Biggest Kollywood chartbusters",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "tamil top hits",
            fallbackQueries = listOf("tamil hits", "kollywood hits"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "tamil_latest_songs",
            title = "Latest Tamil Songs",
            subtitle = "Fresh releases from Anirudh, A.R. Rahman & Yuvan",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "latest tamil songs 2025 2026",
            fallbackQueries = listOf("latest tamil songs"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "tamil_new_releases",
            title = "New Tamil Releases",
            subtitle = "Newest Tamil film singles & indie drops",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "new tamil songs 2026",
            fallbackQueries = listOf("new tamil releases")
        ),
        HomeSectionDefinition(
            id = "tamil_trending_songs",
            title = "Trending Tamil Songs",
            subtitle = "Viral Kollywood songs lighting up the charts",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "trending tamil songs"
        ),
        HomeSectionDefinition(
            id = "tamil_love_songs",
            title = "Tamil Love Songs",
            subtitle = "Soulful Kollywood love tracks",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "tamil love songs"
        ),
        HomeSectionDefinition(
            id = "tamil_melody_hits",
            title = "Tamil Melody Hits",
            subtitle = "Evergreen & modern Tamil acoustic melodies",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "tamil melody hits"
        ),
        HomeSectionDefinition(
            id = "tamil_mass_hits",
            title = "Tamil Mass Hits",
            subtitle = "Electrifying Kollywood kuthu & elevation tracks",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "tamil mass dance hits"
        ),
        HomeSectionDefinition(
            id = "tamil_movie_soundtracks",
            title = "Tamil Movie Soundtracks",
            subtitle = "Official Kollywood cinema OSTs",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.ALBUMS,
            query = "tamil movie soundtrack",
            fallbackQueries = listOf("leo tamil", "jailer tamil"),
            isMovieSoundtrack = true
        ),
        HomeSectionDefinition(
            id = "tamil_romantic_hits",
            title = "Tamil Romantic Hits",
            subtitle = "Dreamy Tamil romance hits",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.SONGS,
            query = "tamil romantic hits"
        ),
        HomeSectionDefinition(
            id = "tamil_popular_albums",
            title = "Popular Tamil Albums",
            subtitle = "Top streamed Tamil film albums",
            language = HomeLanguageFilter.TAMIL,
            contentType = HomeSectionContentType.ALBUMS,
            query = "popular tamil albums",
            fallbackQueries = listOf("anirudh tamil album")
        )
    )

    // 10 Dedicated HINDI Sections
    val HINDI_SECTIONS = listOf(
        HomeSectionDefinition(
            id = "hindi_top_hits",
            title = "Hindi Top Hits",
            subtitle = "India's top Hindi chartbusters",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "hindi top hits",
            fallbackQueries = listOf("bollywood hits"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "hindi_latest_songs",
            title = "Latest Hindi Songs",
            subtitle = "Brand new Bollywood & Hindi pop tracks",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "latest hindi songs 2025 2026",
            fallbackQueries = listOf("latest hindi songs"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "hindi_new_bollywood_releases",
            title = "New Bollywood Releases",
            subtitle = "Fresh releases from Mumbai's biggest films",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "new bollywood songs 2026",
            fallbackQueries = listOf("new bollywood releases")
        ),
        HomeSectionDefinition(
            id = "hindi_trending_songs",
            title = "Trending Hindi Songs",
            subtitle = "What everyone is streaming in Hindi today",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "trending hindi songs"
        ),
        HomeSectionDefinition(
            id = "hindi_love_songs",
            title = "Hindi Love Songs",
            subtitle = "Timeless Bollywood love ballads",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "hindi love songs"
        ),
        HomeSectionDefinition(
            id = "hindi_bollywood_romantic",
            title = "Bollywood Romantic Hits",
            subtitle = "Arijit Singh, Shreya Ghoshal & Pritam favourites",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "bollywood romantic hits"
        ),
        HomeSectionDefinition(
            id = "hindi_sad_songs",
            title = "Hindi Sad Songs",
            subtitle = "Emotional heartbreak & soulful Hindi tracks",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "hindi sad songs"
        ),
        HomeSectionDefinition(
            id = "hindi_party_hits",
            title = "Hindi Party Hits",
            subtitle = "Bollywood dance floor & club bangers",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.SONGS,
            query = "hindi party dance hits"
        ),
        HomeSectionDefinition(
            id = "hindi_movie_soundtracks",
            title = "Hindi Movie Soundtracks",
            subtitle = "Complete Bollywood cinema soundtracks",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.ALBUMS,
            query = "hindi movie soundtrack",
            fallbackQueries = listOf("bollywood soundtrack albums"),
            isMovieSoundtrack = true
        ),
        HomeSectionDefinition(
            id = "hindi_popular_albums",
            title = "Popular Hindi Albums",
            subtitle = "Acclaimed Bollywood & Hindi albums",
            language = HomeLanguageFilter.HINDI,
            contentType = HomeSectionContentType.ALBUMS,
            query = "popular hindi bollywood albums",
            fallbackQueries = listOf("arijit singh albums")
        )
    )

    // 10 Dedicated ENGLISH Sections
    val ENGLISH_SECTIONS = listOf(
        HomeSectionDefinition(
            id = "english_global_top_hits",
            title = "Global Top Hits",
            subtitle = "Biggest international pop & chart hits",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "english top pop hits",
            fallbackQueries = listOf("top 40 english hits"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "english_latest_songs",
            title = "Latest English Songs",
            subtitle = "Fresh international releases",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "latest english songs 2025 2026",
            fallbackQueries = listOf("latest english pop"),
            isPrimarySpotlight = true
        ),
        HomeSectionDefinition(
            id = "english_new_releases",
            title = "New English Releases",
            subtitle = "New global singles & album drops",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "new english songs 2026",
            fallbackQueries = listOf("new english releases")
        ),
        HomeSectionDefinition(
            id = "english_trending_pop",
            title = "Trending Pop",
            subtitle = "Pop anthems dominating global playlists",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "trending english pop hits"
        ),
        HomeSectionDefinition(
            id = "english_love_songs",
            title = "English Love Songs",
            subtitle = "Smooth pop & R&B love tracks",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "english love songs"
        ),
        HomeSectionDefinition(
            id = "english_romantic_hits",
            title = "Romantic Hits",
            subtitle = "Acoustic & soulful English romance",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "romantic english hits"
        ),
        HomeSectionDefinition(
            id = "english_rock_hits",
            title = "English Rock Hits",
            subtitle = "Classic & alternative rock essentials",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "english rock hits classics"
        ),
        HomeSectionDefinition(
            id = "english_hip_hop_hits",
            title = "Hip-Hop Hits",
            subtitle = "Essential rap & hip-hop bangers",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "english hip hop rap hits"
        ),
        HomeSectionDefinition(
            id = "english_edm_dance_hits",
            title = "EDM & Dance Hits",
            subtitle = "Electronic dance, house & club anthems",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.SONGS,
            query = "english edm electronic dance hits"
        ),
        HomeSectionDefinition(
            id = "english_popular_albums",
            title = "Popular English Albums",
            subtitle = "Top streamed international English albums",
            language = HomeLanguageFilter.ENGLISH,
            contentType = HomeSectionContentType.ALBUMS,
            query = "popular english pop albums",
            fallbackQueries = listOf("the weeknd album", "taylor swift album")
        )
    )

    val ALL_SECTIONS: List<HomeSectionDefinition> =
        DISCOVERY_SECTIONS + TELUGU_SECTIONS + TAMIL_SECTIONS + ENGLISH_SECTIONS + HINDI_SECTIONS

    val DEFINITION_BY_ID: Map<String, HomeSectionDefinition> = ALL_SECTIONS.associateBy { it.id }

    fun definitionsForFilter(filter: HomeLanguageFilter): List<HomeSectionDefinition> {
        return orderedDefinitionsForSession(filter = filter, sessionSeed = 0L)
    }

    /**
     * Returns Home sections ordered intelligently for the active app session.
     * - When sessionSeed == 0L, preserves canonical deterministic order.
     * - When sessionSeed != 0L, rotates eligible shelves & language highlights per session
     *   while keeping core discovery & personalized anchors accessible near the top.
     */
    fun orderedDefinitionsForSession(
        filter: HomeLanguageFilter,
        sessionSeed: Long,
        dominantMood: ListeningMood? = null
    ): List<HomeSectionDefinition> {
        return when (filter) {
            HomeLanguageFilter.ALL -> {
                val baseDiscoveryTop = listOf(
                    DEFINITION_BY_ID.getValue("discovery_trending_now"),
                    DEFINITION_BY_ID.getValue("discovery_mood_mixes"),
                    DEFINITION_BY_ID.getValue("discovery_made_for_you"),
                    DEFINITION_BY_ID.getValue("discovery_popular_artists")
                )
                val rotatableDiscovery = listOf(
                    DEFINITION_BY_ID.getValue("discovery_your_current_mood"),
                    DEFINITION_BY_ID.getValue("discovery_because_you_listened"),
                    DEFINITION_BY_ID.getValue("discovery_continue_your_vibe"),
                    DEFINITION_BY_ID.getValue("discovery_recently_played_moods"),
                    DEFINITION_BY_ID.getValue("discovery_recommended_for_you"),
                    DEFINITION_BY_ID.getValue("discovery_heavy_rotation"),
                    DEFINITION_BY_ID.getValue("discovery_new_releases"),
                    DEFINITION_BY_ID.getValue("discovery_featured_albums"),
                    DEFINITION_BY_ID.getValue("discovery_movie_soundtracks"),
                    DEFINITION_BY_ID.getValue("discovery_discover_weekly")
                )

                if (sessionSeed == 0L) {
                    listOf(
                        DEFINITION_BY_ID.getValue("discovery_trending_now"),
                        DEFINITION_BY_ID.getValue("discovery_made_for_you"),
                        DEFINITION_BY_ID.getValue("discovery_popular_artists"),
                        DEFINITION_BY_ID.getValue("discovery_mood_mixes"),
                        DEFINITION_BY_ID.getValue("discovery_your_current_mood"),
                        DEFINITION_BY_ID.getValue("discovery_because_you_listened"),
                        DEFINITION_BY_ID.getValue("discovery_continue_your_vibe"),
                        DEFINITION_BY_ID.getValue("discovery_recently_played_moods"),
                        DEFINITION_BY_ID.getValue("discovery_recommended_for_you"),
                        DEFINITION_BY_ID.getValue("discovery_heavy_rotation"),
                        DEFINITION_BY_ID.getValue("discovery_new_releases"),
                        DEFINITION_BY_ID.getValue("discovery_featured_albums"),
                        DEFINITION_BY_ID.getValue("discovery_movie_soundtracks"),
                        DEFINITION_BY_ID.getValue("discovery_discover_weekly")
                    ) +
                        TELUGU_SECTIONS.take(4) +
                        TAMIL_SECTIONS.take(4) +
                        ENGLISH_SECTIONS.take(4) +
                        HINDI_SECTIONS.take(4) +
                        TELUGU_SECTIONS.drop(4) +
                        TAMIL_SECTIONS.drop(4) +
                        ENGLISH_SECTIONS.drop(4) +
                        HINDI_SECTIONS.drop(4)
                } else {
                    val orderedTop = listOf(
                        baseDiscoveryTop[0],
                        if ((abs(sessionSeed) % 2L) == 0L) baseDiscoveryTop[1] else baseDiscoveryTop[2],
                        baseDiscoveryTop[3],
                        if ((abs(sessionSeed) % 2L) == 0L) baseDiscoveryTop[2] else baseDiscoveryTop[1]
                    )
                    val orderedMiddle = AiMoodIntelligenceEngine.rotateListForSession(
                        items = rotatableDiscovery,
                        sessionSeed = sessionSeed,
                        salt = "home_all_middle",
                        keyExtractor = { it.id }
                    )
                    val languageBuckets = listOf(
                        TELUGU_SECTIONS,
                        TAMIL_SECTIONS,
                        ENGLISH_SECTIONS,
                        HINDI_SECTIONS
                    )
                    val rotatedBuckets = AiMoodIntelligenceEngine.rotateListForSession(
                        items = languageBuckets,
                        sessionSeed = sessionSeed,
                        salt = "home_all_lang_buckets",
                        keyExtractor = { it.first().language.name }
                    )
                    val orderedHighlights = rotatedBuckets.flatMap { bucket ->
                        val anchor = bucket.take(2)
                        val rotatedRest = AiMoodIntelligenceEngine.rotateListForSession(
                            items = bucket.drop(2),
                            sessionSeed = sessionSeed,
                            salt = "lang_bucket_${bucket.first().language.name}",
                            keyExtractor = { it.id }
                        )
                        anchor + rotatedRest.take(2)
                    }
                    val orderedRemaining = rotatedBuckets.flatMap { bucket ->
                        AiMoodIntelligenceEngine.rotateListForSession(
                            items = bucket.drop(2),
                            sessionSeed = sessionSeed,
                            salt = "lang_bucket_${bucket.first().language.name}",
                            keyExtractor = { it.id }
                        ).drop(2)
                    }
                    orderedTop + orderedMiddle + orderedHighlights + orderedRemaining
                }
            }

            HomeLanguageFilter.TELUGU -> buildLanguageFocusedOrder(
                language = HomeLanguageFilter.TELUGU,
                languageSections = TELUGU_SECTIONS,
                sessionSeed = sessionSeed,
                dominantMood = dominantMood
            )

            HomeLanguageFilter.TAMIL -> buildLanguageFocusedOrder(
                language = HomeLanguageFilter.TAMIL,
                languageSections = TAMIL_SECTIONS,
                sessionSeed = sessionSeed,
                dominantMood = dominantMood
            )

            HomeLanguageFilter.ENGLISH -> buildLanguageFocusedOrder(
                language = HomeLanguageFilter.ENGLISH,
                languageSections = ENGLISH_SECTIONS,
                sessionSeed = sessionSeed,
                dominantMood = dominantMood
            )

            HomeLanguageFilter.HINDI -> buildLanguageFocusedOrder(
                language = HomeLanguageFilter.HINDI,
                languageSections = HINDI_SECTIONS,
                sessionSeed = sessionSeed,
                dominantMood = dominantMood
            )
        }
    }

    private fun buildLanguageFocusedOrder(
        language: HomeLanguageFilter,
        languageSections: List<HomeSectionDefinition>,
        sessionSeed: Long = 0L,
        dominantMood: ListeningMood? = null
    ): List<HomeSectionDefinition> {
        val label = language.displayName
        val moodMixForLang = DEFINITION_BY_ID.getValue("discovery_mood_mixes").copy(
            id = "discovery_mood_mix_${language.jioSaavnLanguage}",
            title = "Mood Mixes • $label",
            subtitle = "AI-curated $label mixes tuned to your vibe",
            language = language,
            query = "$label mood mixes"
        )
        val madeForLang = HomeSectionDefinition(
            id = "discovery_made_for_${language.jioSaavnLanguage}",
            title = "Made For You • $label",
            subtitle = "Curated $label playlists & mixes",
            language = language,
            contentType = HomeSectionContentType.PLAYLISTS,
            query = "$label top hits",
            isPersonalized = true,
            isPrimarySpotlight = true
        )
        val artistsForLang = HomeSectionDefinition(
            id = "discovery_artists_${language.jioSaavnLanguage}",
            title = "Popular ${language.displayName} Artists",
            subtitle = "Tap any ${language.displayName} artist for top songs, albums & playlists",
            language = language,
            contentType = HomeSectionContentType.ARTISTS,
            query = POPULAR_ARTIST_SEEDS[language].orEmpty().joinToString(", "),
            isPrimarySpotlight = true
        )
        val recommendedForLang = DEFINITION_BY_ID.getValue("discovery_recommended_for_you").copy(
            id = "discovery_recommended_${language.jioSaavnLanguage}",
            subtitle = "$label picks tailored to your listening taste",
            language = language,
            query = "$label melody romantic hits"
        )
        val heavyForLang = DEFINITION_BY_ID.getValue("discovery_heavy_rotation").copy(
            id = "discovery_heavy_${language.jioSaavnLanguage}",
            language = language,
            query = "$label hits"
        )

        val defaultOrder = listOf(
            languageSections[0], // Top Hits
            languageSections[1], // Latest
            artistsForLang,
            moodMixForLang,
            madeForLang,
            languageSections[2], // New Releases
            languageSections[3], // Trending
            recommendedForLang,
            languageSections[7], // Movie Soundtracks
            languageSections[9], // Popular Albums
            languageSections[4], // Love Songs
            languageSections[5], // Melody Hits / Romantic
            languageSections[6], // Mass / Rock / Sad
            languageSections[8], // Romantic / EDM / Hindi Soundtracks
            heavyForLang
        )
        if (sessionSeed == 0L) return defaultOrder

        val anchors = listOf(languageSections[0], languageSections[1], artistsForLang, moodMixForLang)
        val rotatable = defaultOrder.filterNot { it in anchors }
        val sortedRotatable = rotatable.sortedByDescending { def ->
            val moodBoost = when (dominantMood) {
                ListeningMood.SAD_HEARTBREAK, ListeningMood.EMOTIONAL ->
                    if (def.id.contains("sad") || def.id.contains("melody") || def.id.contains("love")) 2500L else 0L
                ListeningMood.ROMANTIC_LOVE ->
                    if (def.id.contains("romantic") || def.id.contains("love") || def.id.contains("melody")) 2500L else 0L
                ListeningMood.PARTY_DANCE, ListeningMood.ENERGETIC ->
                    if (def.id.contains("mass") || def.id.contains("party") || def.id.contains("edm") || def.id.contains("hip_hop")) 2500L else 0L
                else -> 0L
            }
            val hash = abs(AiMoodIntelligenceEngine.deterministicHash(sessionSeed, "lang_${language.name}_${def.id}") % 10_000L)
            moodBoost + hash
        }
        return anchors + sortedRotatable
    }

    val TELUGU_ARTIST_SEEDS: List<String> = listOf(
        "Anirudh Ravichander",
        "Sid Sriram",
        "Devi Sri Prasad",
        "Thaman S",
        "Armaan Malik",
        "Shreya Ghoshal"
    )

    val TAMIL_ARTIST_SEEDS: List<String> = listOf(
        "A. R. Rahman",
        "Anirudh Ravichander",
        "Yuvan Shankar Raja",
        "Santhosh Narayanan",
        "Sid Sriram"
    )

    val HINDI_ARTIST_SEEDS: List<String> = listOf(
        "Arijit Singh",
        "Shreya Ghoshal",
        "Vishal-Shekhar",
        "Pritam",
        "Sonu Nigam",
        "Neha Kakkar"
    )

    val ENGLISH_ARTIST_SEEDS: List<String> = listOf(
        "Taylor Swift",
        "Ed Sheeran",
        "The Weeknd",
        "Justin Bieber",
        "Bruno Mars",
        "Billie Eilish"
    )

    val ARTISTS_BY_LANGUAGE: Map<HomeLanguageFilter, List<String>> = mapOf(
        HomeLanguageFilter.TELUGU to TELUGU_ARTIST_SEEDS,
        HomeLanguageFilter.TAMIL to TAMIL_ARTIST_SEEDS,
        HomeLanguageFilter.HINDI to HINDI_ARTIST_SEEDS,
        HomeLanguageFilter.ENGLISH to ENGLISH_ARTIST_SEEDS
    )

    private val ALL_LANGUAGE_ARTIST_SEEDS: List<String> = buildList {
        val buckets = listOf(TELUGU_ARTIST_SEEDS, TAMIL_ARTIST_SEEDS, HINDI_ARTIST_SEEDS, ENGLISH_ARTIST_SEEDS)
        val maxLen = buckets.maxOf { it.size }
        val seen = linkedSetOf<String>()
        for (idx in 0 until maxLen) {
            for (bucket in buckets) {
                val candidate = bucket.getOrNull(idx) ?: continue
                val norm = ArtistMatchingPolicy.normalizeArtistName(candidate)
                if (norm.isNotBlank() && seen.add(norm)) {
                    add(candidate)
                }
            }
        }
    }

    val POPULAR_ARTIST_SEEDS: Map<HomeLanguageFilter, List<String>> = mapOf(
        HomeLanguageFilter.TELUGU to TELUGU_ARTIST_SEEDS,
        HomeLanguageFilter.TAMIL to TAMIL_ARTIST_SEEDS,
        HomeLanguageFilter.HINDI to HINDI_ARTIST_SEEDS,
        HomeLanguageFilter.ENGLISH to ENGLISH_ARTIST_SEEDS,
        HomeLanguageFilter.ALL to ALL_LANGUAGE_ARTIST_SEEDS
    )

    fun languagesForArtist(artistName: String): List<String> {
        return ARTISTS_BY_LANGUAGE.entries
            .filter { (_, names) -> names.any { ArtistMatchingPolicy.artistsMatch(it, artistName) } }
            .map { it.key.displayName }
    }
}
