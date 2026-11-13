package com.usman.musicplayer.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.data.remote.JioSaavnApiService
import com.usman.musicplayer.data.repository.providers.JioSaavnMusicProvider
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.sdk.ProviderRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

enum class ExternalServiceType(val displayName: String) {
    SPOTIFY("Spotify"),
    APPLE_MUSIC("Apple Music")
}

data class MusicGenreCategory(
    val title: String,
    val searchTerm: String,
    val country: String? = null,
    val group: String
)

object OnlineMusicRepository {

    private const val TAG = "OnlineMusicRepo"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val jioSaavnApiService: JioSaavnApiService = Retrofit.Builder()
        .baseUrl("https://www.jiosaavn.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(JioSaavnApiService::class.java)

    private val fallbackJioSaavnProvider: JioSaavnMusicProvider by lazy {
        JioSaavnMusicProvider(jioSaavnApiService, moshi)
    }

    private fun jioSaavnProvider(): JioSaavnMusicProvider {
        return ProviderRegistry.activeProviders
            .filterIsInstance<JioSaavnMusicProvider>()
            .firstOrNull()
            ?: fallbackJioSaavnProvider
    }

    // Predefined curated categories requested by user
    val INDIAN_CATEGORIES = listOf(
        MusicGenreCategory("Telugu Hits", "telugu hits", "in", "Indian"),
        MusicGenreCategory("Hindi Bollywood", "bollywood hindi hits", "in", "Indian"),
        MusicGenreCategory("Tamil Hits", "tamil hits", "in", "Indian"),
        MusicGenreCategory("Kannada Songs", "kannada hits", "in", "Indian"),
        MusicGenreCategory("Malayalam Songs", "malayalam hits", "in", "Indian"),
        MusicGenreCategory("Punjabi Hits", "punjabi hits", "in", "Indian"),
        MusicGenreCategory("Bengali Songs", "bengali hits", "in", "Indian"),
        MusicGenreCategory("Marathi Songs", "marathi hits", "in", "Indian"),
        MusicGenreCategory("Indian Classical", "indian classical instrumental", "in", "Indian"),
        MusicGenreCategory("Indian Indie", "indian independent pop", "in", "Indian"),
        MusicGenreCategory("Latest Indian Releases", "latest indian songs", "in", "Indian"),
        MusicGenreCategory("Romantic Songs", "romantic hindi songs", "in", "Indian"),
        MusicGenreCategory("Sad Songs", "sad hindi songs", "in", "Indian"),
        MusicGenreCategory("Trending India", "trending india top songs", "in", "Indian")
    )

    val ENGLISH_CATEGORIES = listOf(
        MusicGenreCategory("English Pop", "pop hits", null, "English"),
        MusicGenreCategory("Global Top Hits", "top 40 hits", null, "English"),
        MusicGenreCategory("English Hip-Hop", "hip hop rap hits", null, "English"),
        MusicGenreCategory("R&B", "r&b hits", null, "English"),
        MusicGenreCategory("Rock", "rock classics", null, "English"),
        MusicGenreCategory("Alternative", "alternative rock indie", null, "English"),
        MusicGenreCategory("Indie Pop", "indie pop songs", null, "English"),
        MusicGenreCategory("EDM / Dance", "electronic dance music edm", null, "English"),
        MusicGenreCategory("Chill / Lofi", "chill acoustic lofi", null, "English"),
        MusicGenreCategory("Romantic English", "romantic english pop songs", null, "English"),
        MusicGenreCategory("Sad English Songs", "sad emotional english songs", null, "English"),
        MusicGenreCategory("Workout Music", "workout gym motivation hype", null, "English")
    )

    val POPULAR_ENGLISH_ARTISTS = listOf(
        "The Weeknd", "Billie Eilish", "Taylor Swift", "Ed Sheeran", "Justin Bieber",
        "Bruno Mars", "Ariana Grande", "Drake", "Eminem", "Travis Scott",
        "Rihanna", "Imagine Dragons", "Coldplay", "Dua Lipa", "Post Malone", "Linkin Park"
    )

    val PHONK_CATEGORIES = listOf(
        MusicGenreCategory("Brazilian Phonk", "brazilian phonk funk", null, "Phonk"),
        MusicGenreCategory("Drift Phonk", "drift phonk", null, "Phonk"),
        MusicGenreCategory("Aggressive Phonk", "aggressive phonk", null, "Phonk"),
        MusicGenreCategory("Dark Phonk", "dark phonk", null, "Phonk"),
        MusicGenreCategory("Atmospheric Phonk", "atmospheric phonk", null, "Phonk"),
        MusicGenreCategory("Memphis Phonk", "memphis phonk", null, "Phonk"),
        MusicGenreCategory("Gym Phonk", "gym phonk workout", null, "Phonk"),
        MusicGenreCategory("Speed-Up Phonk", "speed up phonk", null, "Phonk"),
        MusicGenreCategory("Slowed Phonk", "slowed phonk bass", null, "Phonk"),
        MusicGenreCategory("Trending Phonk", "phonk", null, "Phonk"),
        MusicGenreCategory("Phonk Mixes", "phonk mix", null, "Phonk")
    )

    val PHONK_ARTISTS = listOf(
        "Kordhell", "DVRST", "INTERWORLD", "MoonDeity", "Hensonn", "PlayaPhonk"
    )

    val GLOBAL_CATEGORIES = listOf(
        MusicGenreCategory("Japanese J-Pop", "j-pop japanese hits", "jp", "Global"),
        MusicGenreCategory("Korean K-Pop", "k-pop hits", "kr", "Global"),
        MusicGenreCategory("Anime Music", "anime soundtrack openings", "jp", "Global"),
        MusicGenreCategory("Arabic Music", "arabic hits pop", "ae", "Global"),
        MusicGenreCategory("Spanish Music", "musica espanola spanish", "es", "Global"),
        MusicGenreCategory("French Pop", "musique francaise french", "fr", "Global"),
        MusicGenreCategory("African Afrobeats", "afrobeats hits", null, "Global"),
        MusicGenreCategory("Turkish Music", "turkce pop turkish", "tr", "Global"),
        MusicGenreCategory("Latin Reggaeton", "latin hits reggaeton", null, "Global"),
        MusicGenreCategory("Global Trending", "global viral hits", null, "Global")
    )

    // In-memory cache for JioSaavn search queries to prevent repeat network hits
    private val queryCache = java.util.concurrent.ConcurrentHashMap<String, List<Song>>()

    suspend fun searchOnline(
        query: String,
        country: String? = null,
        limit: Int = 30
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        val cacheKey = "${trimmed.lowercase()}_${country ?: "global"}_$limit"
        queryCache[cacheKey]?.let {
            return@withContext Result.success(it)
        }

        try {
            val songs = jioSaavnProvider().searchPaginated(trimmed, limit, page = 1)
                .getOrDefault(emptyList())
                .filter {
                    it.playbackType == PlaybackType.FULL &&
                        it.sourceProvider.equals("JioSaavn", ignoreCase = true) &&
                        (it.isStreamable || !it.contentUriString.isNullOrBlank())
                }
                .distinctBy { "${it.title}_${it.artist}".lowercase() }
                .take(limit)

            if (songs.isNotEmpty()) {
                queryCache[cacheKey] = songs
            }
            Result.success(songs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "searchOnline JioSaavn error for query '$trimmed': ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchCategorySongs(category: MusicGenreCategory, limit: Int = 25): Result<List<Song>> {
        return searchOnline(category.searchTerm, category.country, limit)
    }

    fun openInOfficialService(context: Context, song: Song, serviceType: ExternalServiceType) {
        val query = "${song.title} ${song.artist}".trim()
        val encoded = try {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            query.replace(" ", "+")
        }

        when (serviceType) {
            ExternalServiceType.SPOTIFY -> {
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(appIntent)
                } catch (e: Exception) {
                    try { context.startActivity(webIntent) } catch (e2: Exception) { /* Ignore */ }
                }
            }

            ExternalServiceType.APPLE_MUSIC -> {
                val targetUrl = song.externalUrl ?: "https://music.apple.com/us/search?term=$encoded"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot launch Apple Music web intent", e)
                }
            }
        }
    }
}
