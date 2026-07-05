package com.nexradwx.app.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Single shared OkHttp client for the whole app; connection pooling matters here since both
 * the radar archive fetch and the station polling happen frequently. */
object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
