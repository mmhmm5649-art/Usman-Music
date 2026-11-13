package com.usman.musicplayer.data.repository.providers

import com.usman.musicplayer.data.remote.ITunesTrackDto
import com.usman.musicplayer.data.remote.MusicApiService
import com.usman.musicplayer.sdk.AlbumDetails
import com.usman.musicplayer.sdk.AlbumSearchResult
import com.usman.musicplayer.sdk.MusicProvider
import com.usman.musicplayer.sdk.PlaybackType
import com.usman.musicplayer.sdk.ProviderMetadata
import com.usman.musicplayer.sdk.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Apple / iTunes Search & Lookup provider for official album/movie soundtrack metadata
 * and verified preview streams across India & Global catalogs.
 */
class ITunesMusicProvider(
    private val apiService: MusicApiService
) : MusicProvider {

    override val metadata = ProviderMetadata(
        id = "itunes_catalog_provider",
        name = "iTunes",
        version = "1.1.0",
        isFullPlaybackSupported = false,
        authRequired = false
    )

    override suspend fun search(query: String, limit: Int): Result<List<Song>> =
        searchPaginated(query, limit, page = 1)

    override suspend fun searchPaginated(query: String, limit: Int, page: Int): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())

            try {
                val offset = ((page - 1).coerceAtLeast(0)) * limit
                val combined = coroutineScope {
                    val inDeferred = async {
                        runCatching {
                            apiService.searchTracks(
                                term = trimmed,
                                limit = limit,
                                country = "in",
                                offset = if (offset > 0) offset else null
                            ).results
                        }.getOrDefault(emptyList())
                    }
                    val globalDeferred = async {
                        runCatching {
                            apiService.searchTracks(
                                term = trimmed,
                                limit = limit,
                                country = null,
                                offset = if (offset > 0) offset else null
                            ).results
                        }.getOrDefault(emptyList())
                    }
                    inDeferred.await() + globalDeferred.await()
                }

                val songs = combined
                    .filter { !it.trackName.isNullOrBlank() }
                    .distinctBy { it.trackId ?: abs("${it.trackName}_${it.artistName}_${it.collectionId}".hashCode().toLong()) }
                    .map { mapTrackDtoToSong(it) }
                    .take(limit)

                Result.success(songs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun searchAlbums(query: String, limit: Int): Result<List<AlbumSearchResult>> =
        searchAlbumsPaginated(query, limit, page = 1)

    override suspend fun searchAlbumsPaginated(query: String, limit: Int, page: Int): Result<List<AlbumSearchResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())

            try {
                val offset = ((page - 1).coerceAtLeast(0)) * limit
                val combined = coroutineScope {
                    val inDeferred = async {
                        runCatching {
                            apiService.searchAlbums(
                                term = trimmed,
                                limit = limit,
                                country = "in",
                                offset = if (offset > 0) offset else null
                            ).results
                        }.getOrDefault(emptyList())
                    }
                    val globalDeferred = async {
                        runCatching {
                            apiService.searchAlbums(
                                term = trimmed,
                                limit = limit,
                                country = null,
                                offset = if (offset > 0) offset else null
                            ).results
                        }.getOrDefault(emptyList())
                    }
                    inDeferred.await() + globalDeferred.await()
                }

                val albums = combined
                    .mapNotNull { mapCollectionDtoToAlbum(it) }
                    .distinctBy { it.id }
                    .take(limit)

                Result.success(albums)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getAlbumDetails(album: AlbumSearchResult): Result<AlbumDetails?> =
        withContext(Dispatchers.IO) {
            val albumId = album.id.trim()
            if (albumId.isBlank() || albumId.toLongOrNull() == null) {
                return@withContext Result.success(null)
            }

            try {
                var results = runCatching {
                    apiService.lookupCollection(collectionId = albumId, country = "in").results
                }.getOrDefault(emptyList())

                if (results.none { it.wrapperType == "track" || !it.trackName.isNullOrBlank() }) {
                    results = runCatching {
                        apiService.lookupCollection(collectionId = albumId, country = null).results
                    }.getOrDefault(emptyList())
                }

                if (results.isEmpty()) return@withContext Result.success(null)

                val collectionDto = results.firstOrNull {
                    it.wrapperType.equals("collection", ignoreCase = true) || it.trackName.isNullOrBlank()
                }
                val trackDtos = results
                    .filter { !it.trackName.isNullOrBlank() && (it.wrapperType == null || it.wrapperType == "track") }
                    .sortedWith(compareBy<ITunesTrackDto> { it.discNumber ?: 1 }.thenBy { it.trackNumber ?: Int.MAX_VALUE })

                val title = collectionDto?.collectionName?.takeIf { it.isNotBlank() } ?: album.title
                val artist = collectionDto?.artistName?.takeIf { it.isNotBlank() } ?: album.artist
                val year = collectionDto?.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                    ?: album.year
                val artwork = upgradeArtwork(collectionDto?.artworkUrl100 ?: collectionDto?.artworkUrl60)
                    ?: album.artworkUri
                val declaredCount = collectionDto?.trackCount?.takeIf { it > 0 } ?: album.songCount
                val movieName = extractMovieName(title, collectionDto?.primaryGenreName) ?: album.movieName

                val tracks = trackDtos.map { dto ->
                    mapTrackDtoToSong(dto, fallbackArtwork = artwork, fallbackYear = year)
                }

                val isComplete = tracks.isNotEmpty() && declaredCount != null && tracks.size >= declaredCount

                Result.success(
                    AlbumDetails(
                        id = albumId,
                        title = title,
                        movieName = movieName,
                        artist = artist,
                        year = year,
                        songCount = maxOf(tracks.size, declaredCount ?: tracks.size),
                        artworkUri = artwork,
                        sourceProvider = "iTunes",
                        externalUrl = collectionDto?.collectionViewUrl ?: album.externalUrl,
                        language = collectionDto?.country ?: album.language,
                        isComplete = isComplete,
                        tracks = tracks,
                        discoveredCount = tracks.size,
                        officialExpectedCount = declaredCount,
                        contributingAlbumsCount = 1,
                        contributingProviders = listOf("iTunes"),
                        isSoundtrackCollection = false
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getStreamUrl(song: Song): Result<String> {
        val url = song.contentUriString?.takeIf { it.isNotBlank() }
        return if (url != null) {
            Result.success(url)
        } else {
            Result.failure(Exception("Preview stream unavailable for '${song.title}' on iTunes"))
        }
    }

    private fun mapCollectionDtoToAlbum(dto: ITunesTrackDto): AlbumSearchResult? {
        val cid = dto.collectionId?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val title = dto.collectionName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val artist = dto.artistName?.trim()?.takeIf { it.isNotBlank() } ?: "Various Artists"
        val year = dto.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val count = dto.trackCount?.takeIf { it > 0 }
        val artwork = upgradeArtwork(dto.artworkUrl100 ?: dto.artworkUrl60)
        val movieName = extractMovieName(title, dto.primaryGenreName)

        return AlbumSearchResult(
            id = cid,
            title = title,
            movieName = movieName,
            artist = artist,
            year = year,
            songCount = count,
            artworkUri = artwork,
            sourceProvider = "iTunes",
            externalUrl = dto.collectionViewUrl,
            language = dto.country,
            isCompleteAlbum = count != null && count > 1
        )
    }

    private fun mapTrackDtoToSong(
        dto: ITunesTrackDto,
        fallbackArtwork: String? = null,
        fallbackYear: String? = null
    ): Song {
        val songId = dto.trackId ?: abs("${dto.trackName}_${dto.artistName}_${dto.collectionId}".hashCode().toLong())
        val art = upgradeArtwork(dto.artworkUrl100 ?: dto.artworkUrl60) ?: fallbackArtwork
        val preview = dto.previewUrl?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http://")) it.replace("http://", "https://") else it
        }
        val hasPreview = !preview.isNullOrBlank()
        val year = dto.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: fallbackYear

        return Song(
            id = songId,
            title = dto.trackName?.trim() ?: "Unknown Track",
            artist = dto.artistName?.trim() ?: "Unknown Artist",
            album = dto.collectionName?.trim()?.takeIf { it.isNotBlank() } ?: "Single",
            durationMs = dto.trackTimeMillis ?: if (hasPreview) 30_000L else 180_000L,
            filePath = preview ?: (dto.trackViewUrl ?: songId.toString()),
            folderName = "iTunes",
            albumArtUri = art,
            contentUriString = preview,
            mimeType = "audio/mp4",
            sizeBytes = 0L,
            isOnline = true,
            playbackType = if (hasPreview) PlaybackType.PREVIEW else PlaybackType.EXTERNAL_SERVICE,
            externalUrl = dto.trackViewUrl ?: dto.collectionViewUrl,
            genre = dto.primaryGenreName,
            language = dto.country,
            sourceProvider = "iTunes",
            attribution = "${dto.artistName ?: "Artist"} • iTunes",
            isStreamable = hasPreview,
            isFullTrackVerified = false,
            isRightsVerified = true,
            albumId = dto.collectionId?.toString(),
            releaseYear = year,
            albumUrl = dto.collectionViewUrl,
            trackNumber = dto.trackNumber
        )
    }

    private fun extractMovieName(title: String, genre: String?): String? {
        val fromMatch = Regex("""\((?:From|from)\s+"?([^")]+)"?\)""").find(title)?.groupValues?.getOrNull(1)?.trim()
        if (!fromMatch.isNullOrBlank()) return fromMatch

        val soundtrackRegex = Regex(
            """\s*[-–]?\s*\(?(?:Original Motion Picture Soundtrack|Original Soundtrack|Motion Picture Soundtrack|Soundtrack|OST)\)?\s*$""",
            RegexOption.IGNORE_CASE
        )
        if (soundtrackRegex.containsMatchIn(title)) {
            return title.replace(soundtrackRegex, "").trim().takeIf { it.isNotBlank() }
        }
        if (genre?.contains("Soundtrack", ignoreCase = true) == true) {
            return title.substringBefore(" - Single").substringBefore(" - EP").trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun upgradeArtwork(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.replace("100x100bb", "600x600bb")
            .replace("60x60bb", "600x600bb")
            .replace("http://", "https://")
    }
}
