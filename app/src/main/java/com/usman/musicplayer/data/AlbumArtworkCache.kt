package com.usman.musicplayer.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object AlbumArtworkCache {

    // 16MB in-memory LRU cache for decoded album artworks
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024 * 16) // at least 16MB

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    suspend fun loadArtwork(
        context: Context,
        songId: Long,
        albumArtUri: String?,
        filePath: String,
        targetWidth: Int = 512,
        targetHeight: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "art_${songId}_${targetWidth}"
        val cached = get(cacheKey)
        if (cached != null) return@withContext cached

        var resultBitmap: Bitmap? = null

        // 1. Try loading from MediaStore albumArtUri or Network URL if present
        if (!albumArtUri.isNullOrBlank()) {
            try {
                if (albumArtUri.startsWith("http://") || albumArtUri.startsWith("https://")) {
                    val connection = java.net.URL(albumArtUri).openConnection().apply {
                        connectTimeout = 6000
                        readTimeout = 6000
                    }
                    connection.getInputStream().use { stream ->
                        resultBitmap = decodeSampledBitmapFromStream(stream, targetWidth, targetHeight)
                    }
                } else {
                    val uri = Uri.parse(albumArtUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        resultBitmap = decodeSampledBitmapFromStream(stream, targetWidth, targetHeight)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                // Artwork file/network failed or timed out
            }
        }

        // 2. If MediaStore album art is null, extract embedded ID3 picture from local file/contentUri
        if (resultBitmap == null && filePath.isNotBlank() && !filePath.startsWith("http")) {
            try {
                val mmr = MediaMetadataRetriever()
                if (filePath.startsWith("content://")) {
                    mmr.setDataSource(context, Uri.parse(filePath))
                } else {
                    mmr.setDataSource(filePath)
                }
                val rawArt = mmr.embeddedPicture
                if (rawArt != null && rawArt.isNotEmpty()) {
                    resultBitmap = decodeSampledBitmapFromByteArray(rawArt, targetWidth, targetHeight)
                }
                mmr.release()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                // Metadata retrieval failed
            }
        }

        if (resultBitmap != null) {
            put(cacheKey, resultBitmap!!)
        }

        resultBitmap
    }

    private fun decodeSampledBitmapFromByteArray(
        data: ByteArray,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        // Use preferred config for memory efficiency on low-end devices
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun decodeSampledBitmapFromStream(
        stream: InputStream,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        // Wrap in buffered stream to support mark/reset for two-pass decoding
        val bufferedStream = stream.let { if (it is java.io.BufferedInputStream) it else java.io.BufferedInputStream(it) }
        bufferedStream.mark(1024 * 1024) // 1MB buffer should be enough for metadata
        
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(bufferedStream, null, options)
        
        try {
            bufferedStream.reset()
        } catch (e: Exception) {
            return null // Should not happen with BufferedInputStream and large enough mark
        }
        
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        
        return BitmapFactory.decodeStream(bufferedStream, null, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
