package com.moshbari.yaptext.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared OkHttp client for all YapText API calls. */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // matches iOS transcribe timeout
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
