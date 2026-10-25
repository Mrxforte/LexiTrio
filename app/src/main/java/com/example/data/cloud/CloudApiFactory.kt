package com.example.data.cloud

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

internal object CloudApiFactory {
    fun create(): CloudApi {
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
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CloudApi::class.java)
    }
}
