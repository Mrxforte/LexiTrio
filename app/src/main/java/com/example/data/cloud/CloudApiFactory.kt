package com.example.data.cloud

import android.content.Context
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

internal object CloudApiFactory {
    private const val HTTP_CACHE_BYTES = 20L * 1024 * 1024

    @Volatile
    private var client: OkHttpClient? = null

    // The disk cache honours the server's ETag/Cache-Control, so repeated
    // searches are answered with a body-less 304 or straight from disk.
    private fun okHttpClient(context: Context?): OkHttpClient =
        client ?: synchronized(this) {
            client ?: OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .apply {
                    context?.let {
                        cache(Cache(File(it.applicationContext.cacheDir, "http"), HTTP_CACHE_BYTES))
                    }
                }
                .build()
                .also { client = it }
        }

    fun create(context: Context? = null): CloudApi {
        val baseUrl = BuildConfig.API_BASE_URL.ifBlank {
            if (BuildConfig.DEBUG) {
                "http://10.0.2.2:8000/api/v1/"
            } else {
                throw IllegalStateException(
                    "Set LEXITRIO_API_BASE_URL to the HTTPS Django API URL for release builds."
                )
            }
        }
        require(baseUrl.startsWith("https://") || BuildConfig.DEBUG) {
            "The release API URL must use HTTPS."
        }
        require(baseUrl.endsWith("/")) { "The API base URL must end with '/'." }

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient(context))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CloudApi::class.java)
    }
}
