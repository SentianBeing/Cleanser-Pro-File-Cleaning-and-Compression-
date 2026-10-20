package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.ui.thumbnails.MediaThumbKeyer
import com.example.ui.thumbnails.MediaThumbnailFetcher

class RoomyApplication : Application(), ImageLoaderFactory {

    // One shared image loader that knows how to load fast system thumbnails
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(MediaThumbKeyer())
            add(MediaThumbnailFetcher.Factory())
        }
        .crossfade(100)
        .build()
}
