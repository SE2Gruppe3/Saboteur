package com.aau.saboteur.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClient {
    val baseUrl: String
        get() = NetworkConstants.baseUrl

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}
