package io.github.redon13.easyreader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Call
import okhttp3.OkHttpClient

class EasyReaderApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Covers are loaded through the signed-in connection and kept on the device for offline use. */
    override fun newImageLoader(): ImageLoader {
        val plain = OkHttpClient()
        val callFactory = Call.Factory { request ->
            (container.sessions.currentClient?.httpClient ?: plain).newCall(request)
        }
        return ImageLoader.Builder(this)
            .callFactory(callFactory)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("covers"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            // Calibre's cover addresses change when a cover changes, so a saved copy can always be reused.
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
