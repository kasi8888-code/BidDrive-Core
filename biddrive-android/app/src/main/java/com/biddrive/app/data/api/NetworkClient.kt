package com.biddrive.app.data.api

import com.biddrive.app.BidDriveApp
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val KEY_BASE_URL = "custom_base_url"
    const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:8080/"
    const val DEFAULT_LOCALHOST_URL = "http://localhost:8080/"

    fun getBaseUrl(): String {
        return BidDriveApp.prefs.getString(KEY_BASE_URL, DEFAULT_EMULATOR_URL) ?: DEFAULT_EMULATOR_URL
    }

    fun setBaseUrl(newUrl: String) {
        val sanitized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        BidDriveApp.prefs.edit().putString(KEY_BASE_URL, sanitized).apply()
        _api = null
    }

    private var _api: BidDriveApi? = null

    val api: BidDriveApi
        get() {
            if (_api == null) {
                _api = createRetrofit().create(BidDriveApi::class.java)
            }
            return _api!!
        }

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
