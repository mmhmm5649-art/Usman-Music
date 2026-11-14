package com.usman.musicplayer.data

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized, singleton Coil ImageLoader and request builder for USMAN MUSIC.
 *
 * Eliminates repeated per-image OkHttp/ImageLoader instantiations, pools TCP/TLS
 * connections across album-art CDNs, bounds concurrent network requests to prevent
 * main-thread / decoder contention, and enforces durable memory + 64 MB disk caching.
 */
object AppImageLoader {

    const val SIZE_MICRO_THUMB_PX = 160
    const val SIZE_CARD_THUMB_PX = 360
    const val SIZE_HERO_WIDTH_PX = 720
    const val SIZE_HERO_HEIGHT_PX = 360
    const val SIZE_FULL_ARTWORK_PX = 640

    private const val DISK_CACHE_DIR = "artwork_cache"
    private const val DISK_CACHE_MAX_BYTES = 64L * 1024L * 1024L
    private const val DEFAULT_CROSSFADE_MS = 140

    @Volatile
    private var instance: ImageLoader? = null

    @Volatile
    private var sharedOkHttpClient: OkHttpClient? = null

    @Volatile
    private var sharedApiOkHttpClient: OkHttpClient? = null

    @Volatile
    private var okHttpClientOverrideForTest: OkHttpClient? = null

    fun getOkHttpClient(): OkHttpClient {
        okHttpClientOverrideForTest?.let { return it }
        return sharedOkHttpClient ?: synchronized(this) {
            sharedOkHttpClient ?: OkHttpClient.Builder()
                .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
                .dispatcher(
                    Dispatcher().apply {
                        maxRequests = 24
                        maxRequestsPerHost = 6
                    }
                )
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
                .also { sharedOkHttpClient = it }
        }
    }

    /**
     * Shared OkHttpClient for music catalog JSON API calls (Search, Home Discovery, Albums, Artists).
     * Reuses a tuned connection pool across ViewModel/repositories to eliminate repeated TLS/TCP handshakes.
     */
    fun getApiOkHttpClient(): OkHttpClient {
        okHttpClientOverrideForTest?.let { return it }
        return sharedApiOkHttpClient ?: synchronized(this) {
            sharedApiOkHttpClient ?: OkHttpClient.Builder()
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .dispatcher(
                    Dispatcher().apply {
                        maxRequests = 32
                        maxRequestsPerHost = 10
                    }
                )
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
                .also { sharedApiOkHttpClient = it }
        }
    }

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        return ImageLoader.Builder(appContext)
            .okHttpClient { getOkHttpClient() }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.22)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve(DISK_CACHE_DIR))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }

    fun buildImageRequest(
        context: Context,
        data: Any?,
        widthPx: Int,
        heightPx: Int = widthPx,
        crossfadeMs: Int = DEFAULT_CROSSFADE_MS,
        allowRgb565: Boolean = true,
        allowHardware: Boolean = true
    ): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(data)
            .size(widthPx.coerceAtLeast(32), heightPx.coerceAtLeast(32))
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .allowRgb565(allowRgb565)
            .allowHardware(allowHardware)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)

        if (crossfadeMs > 0) {
            builder.crossfade(crossfadeMs)
        } else {
            builder.crossfade(false)
        }
        return builder.build()
    }

    fun prefetchArtwork(
        context: Context,
        url: String?,
        widthPx: Int = SIZE_CARD_THUMB_PX,
        heightPx: Int = widthPx
    ): Disposable? {
        val cleanUrl = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val loader = get(context)
        val request = buildImageRequest(
            context = context,
            data = cleanUrl,
            widthPx = widthPx,
            heightPx = heightPx,
            crossfadeMs = 0
        )
        return loader.enqueue(request)
    }

    internal fun setTestOkHttpClient(client: OkHttpClient?) {
        synchronized(this) {
            okHttpClientOverrideForTest = client
            instance?.shutdown()
            instance = null
        }
    }

    internal fun resetForTest() {
        synchronized(this) {
            instance?.shutdown()
            instance = null
            sharedOkHttpClient = null
            sharedApiOkHttpClient = null
            okHttpClientOverrideForTest = null
        }
    }
}
