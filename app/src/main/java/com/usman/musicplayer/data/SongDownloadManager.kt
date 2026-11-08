package com.usman.musicplayer.data

import android.content.Context
import android.util.Log
import com.usman.musicplayer.data.local.AppDatabase
import com.usman.musicplayer.data.local.DownloadEntity
import com.usman.musicplayer.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class SongDownloadManager(private val context: Context) {
    private val downloadDao = AppDatabase.getInstance(context).downloadDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()

    fun startDownload(song: Song, streamUrl: String) {
        if (streamUrl.isBlank()) {
            Log.e("USMAN_DOWNLOAD", "Cannot download song '${song.title}': empty stream URL")
            return
        }

        scope.launch {
            val existing = downloadDao.getDownload(song.id)
            if (existing != null && existing.status == "COMPLETED") {
                Log.d("USMAN_DOWNLOAD", "Song '${song.title}' already downloaded.")
                return@launch
            }

            val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: context.filesDir
            if (!musicDir.exists()) musicDir.mkdirs()

            val sanitizedTitle = song.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetFile = File(musicDir, "usman_dl_${song.id}_$sanitizedTitle.mp3")

            val entity = DownloadEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                albumArtUri = song.albumArtUri,
                streamUrl = streamUrl,
                filePath = targetFile.absolutePath,
                status = "DOWNLOADING",
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = 0L
            )
            downloadDao.insertDownload(entity)

            launchDownloadJob(entity, targetFile)
        }
    }

    private fun launchDownloadJob(entity: DownloadEntity, targetFile: File) {
        val job = scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(entity.streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength().coerceAtLeast(1L)
                
                downloadDao.updateDownload(entity.copy(totalBytes = totalBytes, status = "DOWNLOADING"))

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8 * 1024)
                var downloadedBytes = 0L
                var lastProgress = 0

                inputStream.use { input ->
                    outputStream.use { output ->
                        var bytesRead = input.read(buffer)
                        while (bytesRead != -1 && isActive) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            
                            if (progress != lastProgress || downloadedBytes == totalBytes) {
                                lastProgress = progress
                                downloadDao.updateDownload(
                                    entity.copy(
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                        status = "DOWNLOADING"
                                    )
                                )
                            }
                            bytesRead = input.read(buffer)
                        }
                    }
                }

                if (!isActive) {
                    // Cancelled
                    if (targetFile.exists()) targetFile.delete()
                    return@launch
                }

                // Completed successfully
                downloadDao.updateDownload(
                    entity.copy(
                        downloadedBytes = totalBytes,
                        totalBytes = totalBytes,
                        progress = 100,
                        status = "COMPLETED",
                        filePath = targetFile.absolutePath
                    )
                )
                Log.d("USMAN_DOWNLOAD", "Successfully downloaded song '${entity.title}' to ${targetFile.absolutePath}")

            } catch (e: Exception) {
                if (e is CancellationException) {
                    if (targetFile.exists()) targetFile.delete()
                    throw e
                }
                Log.e("USMAN_DOWNLOAD", "Download failed for '${entity.title}': ${e.message}", e)
                downloadDao.updateDownload(entity.copy(status = "FAILED"))
            } finally {
                activeJobs.remove(entity.songId)
            }
        }

        activeJobs[entity.songId] = job
    }

    fun pauseDownload(songId: Long) {
        scope.launch {
            activeJobs[songId]?.cancel()
            activeJobs.remove(songId)
            val current = downloadDao.getDownload(songId)
            if (current != null && current.status == "DOWNLOADING") {
                downloadDao.updateDownload(current.copy(status = "PAUSED"))
            }
        }
    }

    fun resumeDownload(songId: Long) {
        scope.launch {
            val current = downloadDao.getDownload(songId)
            if (current != null && (current.status == "PAUSED" || current.status == "FAILED")) {
                val file = File(current.filePath)
                downloadDao.updateDownload(current.copy(status = "DOWNLOADING"))
                launchDownloadJob(current, file)
            }
        }
    }

    fun cancelDownload(songId: Long) {
        scope.launch {
            activeJobs[songId]?.cancel()
            activeJobs.remove(songId)
            val current = downloadDao.getDownload(songId)
            if (current != null) {
                val file = File(current.filePath)
                if (file.exists()) file.delete()
                downloadDao.deleteDownload(songId)
            }
        }
    }

    fun retryDownload(songId: Long) {
        scope.launch {
            val current = downloadDao.getDownload(songId)
            if (current != null) {
                val file = File(current.filePath)
                downloadDao.updateDownload(current.copy(status = "DOWNLOADING", progress = 0, downloadedBytes = 0L))
                launchDownloadJob(current, file)
            }
        }
    }

    fun removeDownload(songId: Long) {
        scope.launch {
            activeJobs[songId]?.cancel()
            activeJobs.remove(songId)
            val current = downloadDao.getDownload(songId)
            if (current != null) {
                val file = File(current.filePath)
                if (file.exists()) file.delete()
                downloadDao.deleteDownload(songId)
            }
        }
    }

    fun convertToOfflineSong(entity: DownloadEntity): Song {
        return Song(
            id = entity.songId,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            durationMs = entity.durationMs,
            filePath = entity.filePath,
            albumArtUri = entity.albumArtUri,
            isOnline = false,
            sourceProvider = "Offline Download"
        )
    }
}
