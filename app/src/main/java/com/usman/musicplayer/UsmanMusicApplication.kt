package com.usman.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.usman.musicplayer.data.AppImageLoader

/**
 * Application class providing Coil's global singleton [ImageLoader] via [ImageLoaderFactory].
 */
class UsmanMusicApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = AppImageLoader.get(this)
}
