package shura.media

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class ShuraApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        LogKeeper.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoThumbnailFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnail_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
