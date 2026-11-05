package com.example.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.model.Album
import com.example.model.Artist
import com.example.model.MusicFolder
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMusicScanner(private val context: Context) {

    // Supported extensions for local offline audio
    private val supportedExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "oga"
    )

    private val supportedMimeTypes = setOf(
        "audio/mpeg", "audio/mp3", "audio/mp4", "audio/m4a", "audio/x-m4a",
        "audio/aac", "audio/aac-adts", "audio/flac", "audio/x-flac",
        "audio/wav", "audio/x-wav", "audio/wave", "audio/ogg",
        "audio/x-ogg", "application/ogg", "audio/opus"
    )

    suspend fun scanLocalAudio(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        // Filter is_music != 0
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val rawArtist = cursor.getString(artistCol)
                    val rawAlbum = cursor.getString(albumCol)
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) else null
                    val albumId = if (albumIdCol != -1) cursor.getLong(albumIdCol) else -1L

                    // Check extension & mime type compatibility
                    val extension = path.substringAfterLast('.', "").lowercase()
                    val isSupportedFormat = supportedExtensions.contains(extension) ||
                            (mime != null && supportedMimeTypes.contains(mime.lowercase())) ||
                            (mime != null && mime.startsWith("audio/"))

                    if (!isSupportedFormat && path.isNotEmpty()) {
                        continue
                    }

                    // Fallback to filename if title is missing
                    val fileName = try {
                        File(path).nameWithoutExtension
                    } catch (e: Exception) {
                        ""
                    }
                    val title = when {
                        !rawTitle.isNullOrBlank() && rawTitle != "<unknown>" -> rawTitle.trim()
                        fileName.isNotBlank() -> fileName
                        else -> "Unknown Track"
                    }

                    val artist = when {
                        !rawArtist.isNullOrBlank() && rawArtist != "<unknown>" -> rawArtist.trim()
                        else -> "Unknown Artist"
                    }

                    val album = when {
                        !rawAlbum.isNullOrBlank() && rawAlbum != "<unknown>" -> rawAlbum.trim()
                        else -> "Unknown Album"
                    }

                    // Resolve folder name
                    val folderName = if (relPathCol != -1) {
                        val relPath = cursor.getString(relPathCol)
                        if (!relPath.isNullOrBlank()) {
                            relPath.trimEnd('/').substringAfterLast('/')
                        } else {
                            resolveFolderNameFromFile(path)
                        }
                    } else {
                        resolveFolderNameFromFile(path)
                    }

                    // Standard ContentUri for playback via MediaPlayer
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    // Album artwork Uri
                    val artworkUri = if (albumId > 0) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else null

                    songsList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            filePath = path,
                            folderName = if (folderName.isBlank()) "Music" else folderName,
                            albumArtUri = artworkUri,
                            contentUriString = contentUri,
                            mimeType = mime,
                            sizeBytes = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Permission not yet granted or empty collection
        }

        songsList
    }

    private fun resolveFolderNameFromFile(filePath: String): String {
        return try {
            val file = File(filePath)
            val parentName = file.parentFile?.name
            if (!parentName.isNullOrBlank()) parentName else "Music"
        } catch (e: Exception) {
            "Music"
        }
    }

    /**
     * Scan a directory picked through Storage Access Framework (SAF) using native DocumentsContract
     */
    suspend fun scanSafFolder(treeUri: Uri): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val folderName = treeUri.lastPathSegment?.substringAfterLast(':') ?: "SAF Folder"
            scanSafDocumentTree(treeUri, documentId, folderName, songsList)
        } catch (e: Exception) {
            // SAF access failure
        }
        songsList
    }

    private fun scanSafDocumentTree(
        treeUri: Uri,
        parentDocId: String,
        folderName: String,
        results: MutableList<Song>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            while (cursor != null && cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val displayName = cursor.getString(1) ?: "Track"
                val mimeType = cursor.getString(2) ?: ""
                val size = cursor.getLong(3)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    // Recurse into subdirectories
                    scanSafDocumentTree(treeUri, docId, displayName, results)
                } else {
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    val isAudio = supportedExtensions.contains(ext) ||
                            supportedMimeTypes.contains(mimeType.lowercase()) ||
                            mimeType.startsWith("audio/")

                    if (isAudio) {
                        val fileDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        var title = displayName.substringBeforeLast('.')
                        var artist = "Unknown Artist"
                        var album = "Unknown Album"
                        var durationMs = 0L

                        try {
                            val mmr = MediaMetadataRetriever()
                            mmr.setDataSource(context, fileDocUri)
                            val metaTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            val metaAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            val metaDur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                            if (!metaTitle.isNullOrBlank()) title = metaTitle.trim()
                            if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
                            if (!metaAlbum.isNullOrBlank()) album = metaAlbum.trim()
                            if (!metaDur.isNullOrBlank()) durationMs = metaDur.toLongOrNull() ?: 0L
                            mmr.release()
                        } catch (e: Exception) {
                            // Use filename fallback
                        }

                        results.add(
                            Song(
                                id = fileDocUri.hashCode().toLong(),
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                                filePath = fileDocUri.toString(),
                                folderName = folderName,
                                albumArtUri = null,
                                contentUriString = fileDocUri.toString(),
                                mimeType = mimeType,
                                sizeBytes = size
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Error reading children
        } finally {
            cursor?.close()
        }
    }

    companion object {
        fun extractAlbums(songs: List<Song>): List<Album> {
            return songs.groupBy { it.album }
                .map { (albumTitle, songGroup) ->
                    val first = songGroup.first()
                    Album(
                        id = first.id,
                        title = albumTitle,
                        artist = first.artist,
                        songCount = songGroup.size,
                        artworkUri = first.albumArtUri
                    )
                }
                .sortedBy { it.title.lowercase() }
        }

        fun extractArtists(songs: List<Song>): List<Artist> {
            return songs.groupBy { it.artist }
                .map { (artistName, songGroup) ->
                    val distinctAlbums = songGroup.map { it.album }.distinct().size
                    Artist(
                        name = artistName,
                        songCount = songGroup.size,
                        albumCount = distinctAlbums
                    )
                }
                .sortedBy { it.name.lowercase() }
        }

        fun extractFolders(songs: List<Song>, safFolders: List<MusicFolder> = emptyList()): List<MusicFolder> {
            val mediaStoreFolders = songs.groupBy { it.folderName }
                .map { (folderName, songGroup) ->
                    val path = songGroup.firstOrNull()?.filePath?.let {
                        try {
                            if (it.startsWith("content://")) {
                                "SAF / $folderName"
                            } else {
                                File(it).parent ?: folderName
                            }
                        } catch (e: Exception) {
                            folderName
                        }
                    } ?: folderName

                    MusicFolder(
                        name = folderName,
                        path = path,
                        songCount = songGroup.size,
                        isSafFolder = false
                    )
                }

            val combined = (safFolders + mediaStoreFolders)
                .distinctBy { it.name }
                .sortedBy { it.name.lowercase() }

            return combined
        }
    }
}
