package com.wikimedia.wikiwatch

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.maplibre.android.MapLibre
import java.io.IOException

class WikiWatchApp : Application(), ImageLoaderFactory {
    
    // Interceptor to add required headers for maps.wikimedia.org requests
    // Based on: https://github.com/wikimedia/apps-android-wikipedia/blob/cd917e7fae9d53e1fddf2f6e0ed5295216efd4df/app/src/main/java/org/wikipedia/dataclient/okhttp/CommonHeaderRequestInterceptor.kt
    class MapsHeaderInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url.toString()
            val builder = chain.request().newBuilder()
                .header("User-Agent", "WikiWatch/1.0 (WearOS App; https://github.com/josephseddon/WikiWatch)")
            
            // Add Referer header for maps.wikimedia.org requests (required to avoid 403 errors)
            if (url.contains("maps.wikimedia.org")) {
                builder.header("Referer", "https://maps.wikimedia.org/")
            }
            
            return chain.proceed(builder.build())
        }
    }
    
    // Shared OkHttpClient with maps.wikimedia.org header interceptor
    // Note: MapLibre uses its own HTTP client, so this may not affect MapLibre tile requests directly
    // However, we can use this for other HTTP requests that need these headers
    val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(MapsHeaderInterceptor())
            .build()
    }
    
    override fun onCreate() {
        super.onCreate()
        // MapLibre uses its own HTTP client internally and doesn't expose configuration
        // The 403 errors on MapLibre tile requests may persist until we find a way to configure MapLibre's HTTP client
    }
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(sharedOkHttpClient)
            .build()
    }
}



