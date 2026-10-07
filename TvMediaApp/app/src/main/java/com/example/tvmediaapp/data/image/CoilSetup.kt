package com.example.tvmediaapp.data.image

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object CoilSetup {
    fun init(context: Context) {
        try {
            val okhttpCacheDir = File(context.filesDir, "okhttp_image_cache").apply { mkdirs() }
            val okhttpCache = okhttp3.Cache(okhttpCacheDir, 512L * 1024L * 1024L)

            val okHttpClient = OkHttpClient.Builder()
                .cache(okhttpCache)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host.lowercase()
                    val builder = request.newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )

                    when {
                        host.contains("hdrezka") -> builder.header("Referer", "https://hdrezka-home.tv/")
                        host.contains("yandex") || host.contains("kinopoisk") -> builder.header("Referer", "https://www.kinopoisk.ru/")
                        host.contains("filmix") || host.contains("werkecdn") || host.contains("cdnsqu") -> builder.header("Referer", "https://filmix.biz/")
                        host.contains("kbd.so") || host.contains("bazon") -> builder.header("Referer", "https://bazon.cc/")
                        host.contains("kodik") || host.contains("kodikres") -> builder.header("Referer", "https://kodik.info/")
                        host.contains("tmdb") || host.contains("themoviedb") -> builder.header("Referer", "https://www.themoviedb.org/")
                        host.contains("onrender.com") -> builder.header("Referer", "https://showhub-server.onrender.com/")
                    }
                    chain.proceed(builder.build())
                }
                .addNetworkInterceptor { chain ->
                    // Force infinite cache for all image responses so TV loads instantaneously without revalidating
                    val original = chain.proceed(chain.request())
                    original.newBuilder()
                        .header("Cache-Control", "public, max-age=31536000, immutable")
                        .removeHeader("Pragma")
                        .build()
                }
                .build()

            val imageLoader = ImageLoader.Builder(context)
                .okHttpClient(okHttpClient)
                .memoryCache {
                    MemoryCache.Builder(context)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.filesDir, "image_cache"))
                        .maxSizeBytes(512L * 1024L * 1024L)
                        .build()
                }
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                .allowRgb565(true)
                .crossfade(false)
                .respectCacheHeaders(false)
                .build()

            Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
