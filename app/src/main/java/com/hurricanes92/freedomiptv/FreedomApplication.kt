package com.hurricanes92.freedomiptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Shared Coil loader with bounded memory and persistent artwork caching. */
class FreedomApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context = this@FreedomApplication, percent = 0.20).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("freedom_artwork"))
                .maxSizeBytes(100L * 1024L * 1024L)
                .build()
        }
        .crossfade(false)
        .build()
}
