package com.usman.musicplayer.data.repository

import com.usman.musicplayer.model.Album
import com.usman.musicplayer.model.Artist
import com.usman.musicplayer.model.MusicFolder
import com.usman.musicplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalSearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<MusicFolder> = emptyList()
)

private data class IndexedSong(
    val song: Song,
    val searchTarget: String
)

private data class IndexedAlbum(
    val album: Album,
    val searchTarget: String
)

private data class IndexedArtist(
    val artist: Artist,
    val searchTarget: String
)

private data class IndexedFolder(
    val folder: MusicFolder,
    val searchTarget: String
)

private class SimpleLruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun evictAll() {
        map.clear()
    }
}

/**
 * Fast, thread-safe local music library indexer and LRU search cache.
 * Executes search matching on Dispatchers.IO and maintains pre-lowercased
 * search targets for sub-millisecond query evaluation.
 */
class LocalSearchIndexer {

    @Volatile
    private var indexedSongs: List<IndexedSong> = emptyList()
    @Volatile
    private var indexedAlbums: List<IndexedAlbum> = emptyList()
    @Volatile
    private var indexedArtists: List<IndexedArtist> = emptyList()
    @Volatile
    private var indexedFolders: List<IndexedFolder> = emptyList()

    private val searchCache = SimpleLruCache<String, LocalSearchResults>(100)

    val songCount: Int get() = indexedSongs.size

    suspend fun updateIndex(
        songs: List<Song>,
        albums: List<Album>,
        artists: List<Artist>,
        folders: List<MusicFolder>
    ) = withContext(Dispatchers.Default) {
        val iSongs = songs.map { song ->
            val target = "${song.title} ${song.artist} ${song.album} ${song.folderName}".lowercase()
            IndexedSong(song, target)
        }
        val iAlbums = albums.map { album ->
            val target = "${album.title} ${album.artist}".lowercase()
            IndexedAlbum(album, target)
        }
        val iArtists = artists.map { artist ->
            val target = artist.name.lowercase()
            IndexedArtist(artist, target)
        }
        val iFolders = folders.map { folder ->
            val target = "${folder.name} ${folder.path}".lowercase()
            IndexedFolder(folder, target)
        }

        indexedSongs = iSongs
        indexedAlbums = iAlbums
        indexedArtists = iArtists
        indexedFolders = iFolders

        // Invalidate LRU cache when local music library updates
        searchCache.evictAll()
    }

    fun getIndexedSongs(): List<Song> = indexedSongs.map { it.song }

    suspend fun search(query: String, limit: Int = 100): LocalSearchResults = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            return@withContext LocalSearchResults()
        }

        // LRU Cache Check: Instant hit (< 1ms)
        searchCache.get(trimmed)?.let {
            return@withContext it
        }

        val tokens = trimmed.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return@withContext LocalSearchResults()
        }

        val currentSongs = indexedSongs
        val currentAlbums = indexedAlbums
        val currentArtists = indexedArtists
        val currentFolders = indexedFolders

        val matchedSongs = mutableListOf<Song>()
        for (item in currentSongs) {
            var matchesAll = true
            for (token in tokens) {
                if (!item.searchTarget.contains(token)) {
                    matchesAll = false
                    break
                }
            }
            if (matchesAll) {
                matchedSongs.add(item.song)
                if (matchedSongs.size >= limit) break
            }
        }

        val matchedAlbums = mutableListOf<Album>()
        for (item in currentAlbums) {
            var matchesAll = true
            for (token in tokens) {
                if (!item.searchTarget.contains(token)) {
                    matchesAll = false
                    break
                }
            }
            if (matchesAll) {
                matchedAlbums.add(item.album)
                if (matchedAlbums.size >= limit) break
            }
        }

        val matchedArtists = mutableListOf<Artist>()
        for (item in currentArtists) {
            var matchesAll = true
            for (token in tokens) {
                if (!item.searchTarget.contains(token)) {
                    matchesAll = false
                    break
                }
            }
            if (matchesAll) {
                matchedArtists.add(item.artist)
                if (matchedArtists.size >= limit) break
            }
        }

        val matchedFolders = mutableListOf<MusicFolder>()
        for (item in currentFolders) {
            var matchesAll = true
            for (token in tokens) {
                if (!item.searchTarget.contains(token)) {
                    matchesAll = false
                    break
                }
            }
            if (matchesAll) {
                matchedFolders.add(item.folder)
                if (matchedFolders.size >= limit) break
            }
        }

        val results = LocalSearchResults(
            songs = matchedSongs,
            albums = matchedAlbums,
            artists = matchedArtists,
            folders = matchedFolders
        )

        searchCache.put(trimmed, results)
        results
    }
}
