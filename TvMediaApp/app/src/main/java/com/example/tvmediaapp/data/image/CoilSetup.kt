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
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
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
                        host.contains("kbd.so") || host.contains("bazon") -> builder.header("Referer", "https://bazon.cc/")
                        else -> builder.header("Referer", "https://showhub-server.onrender.com/")
                    }
                    chain.proceed(builder.build())
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
                        .directory(File(context.cacheDir, "image_cache"))
                        .maxSizeBytes(256L * 1024L * 1024L)
                        .build()
                }
                .crossfade(true)
                .respectCacheHeaders(false)
                .build()

            Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
