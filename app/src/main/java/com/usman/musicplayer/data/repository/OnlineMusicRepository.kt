package com.usman.musicplayer.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.usman.musicplayer.data.remote.AudiusApiService
import com.usman.musicplayer.data.remote.AudiusTrackDto
import com.usman.musicplayer.data.remote.ITunesTrackDto
import com.usman.musicplayer.data.remote.MusicApiService
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.abs

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
    private const val BASE_URL = "https://itunes.apple.com/"
    private const val AUDIUS_BASE_URL = "https://discoveryprovider.audius.co/v1/"

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

    private val apiService: MusicApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(MusicApiService::class.java)

    private val audiusApiService: AudiusApiService = Retrofit.Builder()
        .baseUrl(AUDIUS_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(AudiusApiService::class.java)

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

    // In-memory cache for search queries to prevent repeat network hits
    private val queryCache = java.util.concurrent.ConcurrentHashMap<String, List<Song>>()

    suspend fun searchOnline(
        query: String,
        country: String? = null,
        limit: Int = 30
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        val cacheKey = "${query.trim().lowercase()}_${country ?: "global"}_$limit"
        queryCache[cacheKey]?.let {
            return@withContext Result.success(it)
        }

        try {
            // Parallel search across iTunes and Audius
            val iTunesDeferred = async {
                try {
                    apiService.searchTracks(term = query, limit = limit / 2, country = country).results
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val audiusDeferred = async {
                try {
                    audiusApiService.searchTracks(query = query, limit = limit / 2).data
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val iTunesResults = iTunesDeferred.await()
            val audiusResults = audiusDeferred.await()

            val songs = (
                audiusResults.map { mapAudiusDtoToSong(it) } +
                iTunesResults.map { mapDtoToSong(it) }
            ).distinctBy { "${it.title}_${it.artist}".lowercase() }

            if (songs.isNotEmpty()) {
                queryCache[cacheKey] = songs
            }
            Result.success(songs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "searchOnline network error for query '$query': ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchCategorySongs(category: MusicGenreCategory, limit: Int = 25): Result<List<Song>> {
        return searchOnline(category.searchTerm, category.country, limit)
    }

    suspend fun fetchAudiusTrending(limit: Int = 20): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = audiusApiService.getTrendingTracks(limit = limit)
            val songs = response.data.map { mapAudiusDtoToSong(it) }
            Result.success(songs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchAudiusTrending failed", e)
            Result.failure(e)
        }
    }

    private fun mapDtoToSong(dto: ITunesTrackDto): Song {
        val songId = dto.trackId ?: abs(("${dto.trackName}_${dto.artistName}").hashCode().toLong())
        val highResArt = dto.artworkUrl100
            ?.replace("100x100bb", "600x600bb")
            ?.replace("60x60bb", "600x600bb")
            ?: dto.artworkUrl60

        val securePreviewUrl = dto.previewUrl?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http://")) it.replace("http://", "https://") else it
        }
        val hasPreview = !securePreviewUrl.isNullOrBlank()
        val duration = if (hasPreview) {
            // Official preview streams are 30 seconds (30,000ms)
            30_000L
        } else {
            dto.trackTimeMillis ?: 180_000L
        }

        return Song(
            id = songId,
            title = dto.trackName ?: "Unknown Track",
            artist = dto.artistName ?: "Unknown Artist",
            album = dto.collectionName ?: "Single",
            durationMs = duration,
            filePath = securePreviewUrl ?: "",
            folderName = "iTunes Discovery",
            albumArtUri = highResArt,
            contentUriString = securePreviewUrl,
            mimeType = "audio/mp4",
            sizeBytes = 0L,
            isFavorite = false,
            isOnline = true,
            playbackType = if (hasPreview) PlaybackType.PREVIEW else PlaybackType.EXTERNAL_SERVICE,
            externalUrl = dto.trackViewUrl,
            genre = dto.primaryGenreName,
            language = dto.country,
            sourceProvider = "iTunes"
        )
    }

    private fun mapAudiusDtoToSong(dto: AudiusTrackDto): Song {
        val trackId = dto.trackId ?: dto.id?.hashCode()?.toLong() ?: 0L
        val streamUrl = "${AUDIUS_BASE_URL}tracks/${dto.id}/stream?app_name=USMAN_MUSIC_PLAYER"
        val highResArt = dto.artwork?.art1000 ?: dto.artwork?.art480 ?: dto.artwork?.art150

        return Song(
            id = trackId,
            title = dto.title ?: "Unknown Audius Track",
            artist = dto.user?.name ?: "Unknown Artist",
            album = "Audius",
            durationMs = (dto.duration ?: 180).toLong() * 1000L,
            filePath = streamUrl,
            folderName = "Audius Discovery",
            albumArtUri = highResArt,
            contentUriString = streamUrl,
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            isFavorite = false,
            isOnline = true,
            playbackType = PlaybackType.FULL,
            externalUrl = "https://audius.co${dto.permalink}",
            genre = dto.genre,
            sourceProvider = "Audius"
        )
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
