package com.silverphone.app.platform.photos

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache

/**
 * Builds the one ImageLoader used for stored contact photos.
 *
 * Local only: no network components are registered, so no remote image can ever
 * be fetched. The memory cache is deliberately small, because the target devices
 * are old phones with as little as 2 GB of RAM, and the spec asks for a bounded
 * cache rather than an unbounded bitmap map.
 */
object PhotoImageLoader {

    /** Conservative ceiling for bitmap memory on a low-end device. */
    private const val MAX_MEMORY_CACHE_BYTES: Long = 8L * 1024 * 1024

    fun create(context: Context, bytesSource: PhotoBytesSource): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ContactPhotoFetcher.Factory(bytesSource))
                add(ContactPhotoKeyer())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(MAX_MEMORY_CACHE_BYTES)
                    .build()
            }
            .build()
}
