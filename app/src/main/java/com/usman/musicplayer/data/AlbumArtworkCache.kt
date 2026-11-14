package com.usman.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object AlbumArtworkCache {

    // 16MB+ in-memory LRU cache for decoded album bitmaps (used by NowPlaying background blur & ID3 fallback)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024 * 16)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightLoads = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    fun get(key: String): Bitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    fun getCached(
        songId: Long,
        albumArtUri: String?,
        targetWidth: Int,
        targetHeight: Int = targetWidth
    ): Bitmap? {
        val uriKey = buildUriKey(albumArtUri, targetWidth, targetHeight)
        if (uriKey != null) {
            memoryCache.get(uriKey)?.let { return it }
        }
        return memoryCache.get(buildLegacyKey(songId, targetWidth))
    }

    suspend fun loadArtwork(
        context: Context,
        songId: Long,
        albumArtUri: String?,
        filePath: String,
        targetWidth: Int = 512,
        targetHeight: Int = 512
    ): Bitmap? {
        getCached(songId, albumArtUri, targetWidth, targetHeight)?.let { return it }

        val uriKey = buildUriKey(albumArtUri, targetWidth, targetHeight)
        val legacyKey = buildLegacyKey(songId, targetWidth)
        val dedupKey = uriKey ?: "${legacyKey}_${filePath}"
        val appContext = context.applicationContext

        val deferred = inFlightLoads[dedupKey] ?: synchronized(inFlightLoads) {
            inFlightLoads[dedupKey] ?: cacheScope.async {
                loadArtworkUncached(
                    context = appContext,
                    songId = songId,
                    albumArtUri = albumArtUri,
                    filePath = filePath,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    uriKey = uriKey,
                    legacyKey = legacyKey
                )
            }.also { created ->
                inFlightLoads[dedupKey] = created
                created.invokeOnCompletion {
                    inFlightLoads.remove(dedupKey, created)
                }
            }
        }

        return deferred.await()
    }

    private suspend fun loadArtworkUncached(
        context: Context,
        songId: Long,
        albumArtUri: String?,
        filePath: String,
        targetWidth: Int,
        targetHeight: Int,
        uriKey: String?,
        legacyKey: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        getCached(songId, albumArtUri, targetWidth, targetHeight)?.let { return@withContext it }

        var resultBitmap: Bitmap? = null

        // 1. Load network / content URI artwork through singleton AppImageLoader (pooled HTTP + 64MB disk cache)
        if (!albumArtUri.isNullOrBlank()) {
            try {
                val request = AppImageLoader.buildImageRequest(
                    context = context,
                    data = albumArtUri,
                    widthPx = targetWidth,
                    heightPx = targetHeight,
                    crossfadeMs = 0,
                    allowRgb565 = true,
                    allowHardware = false
                )
                val result = AppImageLoader.get(context).execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    resultBitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                        drawable.bitmap
                    } else {
                        drawable.toBitmap(
                            width = drawable.intrinsicWidth.takeIf { it > 0 } ?: targetWidth,
                            height = drawable.intrinsicHeight.takeIf { it > 0 } ?: targetHeight,
                            config = Bitmap.Config.RGB_565
                        )
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Fallback below for local content:// or ID3
            }

            if (resultBitmap == null && !albumArtUri.startsWith("http://") && !albumArtUri.startsWith("https://")) {
                try {
                    val uri = Uri.parse(albumArtUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        resultBitmap = decodeSampledBitmapFromStream(stream, targetWidth, targetHeight)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    // Local URI unreadable
                }
            }
        }

        // 2. If MediaStore album art is null/unavailable, extract embedded ID3 picture from local file/contentUri
        if (resultBitmap == null && filePath.isNotBlank() && !filePath.startsWith("http")) {
            val mmr = MediaMetadataRetriever()
            try {
                if (filePath.startsWith("content://")) {
                    mmr.setDataSource(context, Uri.parse(filePath))
                } else {
                    mmr.setDataSource(filePath)
                }
                val rawArt = mmr.embeddedPicture
                if (rawArt != null && rawArt.isNotEmpty()) {
                    resultBitmap = decodeSampledBitmapFromByteArray(rawArt, targetWidth, targetHeight)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Metadata retrieval failed
            } finally {
                runCatching { mmr.release() }
            }
        }

        if (resultBitmap != null) {
            if (uriKey != null) {
                put(uriKey, resultBitmap!!)
            }
            put(legacyKey, resultBitmap!!)
        }

        resultBitmap
    }

    private fun buildLegacyKey(songId: Long, targetWidth: Int): String = "art_${songId}_${targetWidth}"

    private fun buildUriKey(albumArtUri: String?, targetWidth: Int, targetHeight: Int): String? {
        val clean = albumArtUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "uri_${clean}_${targetWidth}x${targetHeight}"
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
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun decodeSampledBitmapFromStream(
        stream: InputStream,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val bufferedStream = stream.let {
            if (it is java.io.BufferedInputStream) it else java.io.BufferedInputStream(it)
        }
        bufferedStream.mark(1024 * 1024)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(bufferedStream, null, options)

        try {
            bufferedStream.reset()
        } catch (_: Exception) {
            return null
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

    internal fun clearForTest() {
        memoryCache.evictAll()
        inFlightLoads.values.forEach { it.cancel() }
        inFlightLoads.clear()
    }
}
