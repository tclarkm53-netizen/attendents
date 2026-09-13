package com.example.data.remote

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface AttendanceApiService {
    @POST("register.php")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<UserResponseContainer>>

    @POST("login.php")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<UserResponseContainer>>

    @POST("sync.php")
    suspend fun sync(@Body request: SyncRequest): Response<ApiResponse<SyncResponseData>>
}

class FlexibleDoubleAdapter {
    @com.squareup.moshi.FromJson
    fun fromJson(reader: com.squareup.moshi.JsonReader): Double {
        return when (reader.peek()) {
            com.squareup.moshi.JsonReader.Token.STRING -> {
                val str = reader.nextString().trim()
                str.toDoubleOrNull() ?: 0.0
            }
            com.squareup.moshi.JsonReader.Token.NUMBER -> reader.nextDouble()
            com.squareup.moshi.JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                0.0
            }
            else -> {
                reader.skipValue()
                0.0
            }
        }
    }

    @com.squareup.moshi.ToJson
    fun toJson(writer: com.squareup.moshi.JsonWriter, value: Double?) {
        writer.value(value ?: 0.0)
    }
}

class FlexibleLongAdapter {
    @com.squareup.moshi.FromJson
    fun fromJson(reader: com.squareup.moshi.JsonReader): Long {
        return when (reader.peek()) {
            com.squareup.moshi.JsonReader.Token.STRING -> {
                val str = reader.nextString().trim()
                str.toLongOrNull() ?: (str.toDoubleOrNull()?.toLong() ?: 0L)
            }
            com.squareup.moshi.JsonReader.Token.NUMBER -> reader.nextLong()
            com.squareup.moshi.JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                0L
            }
            else -> {
                reader.skipValue()
                0L
            }
        }
    }

    @com.squareup.moshi.ToJson
    fun toJson(writer: com.squareup.moshi.JsonWriter, value: Long?) {
        writer.value(value ?: 0L)
    }
}

class ApiClient private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BASE_URL = "https://verify-app.alwaysdata.net/st/server/api/"
        private const val KEY_BASE_URL = "custom_base_url"

        @Volatile
        private var INSTANCE: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                val instance = ApiClient(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val moshi = Moshi.Builder()
        .add(FlexibleDoubleAdapter())
        .add(FlexibleLongAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private var currentBaseUrl: String = getSavedBaseUrl()
    private var cachedService: AttendanceApiService? = null

    fun getSavedBaseUrl(): String {
        val saved = prefs.getString(KEY_BASE_URL, null)
        return if (saved.isNullOrBlank() || saved.contains("10.0.2.2")) {
            DEFAULT_BASE_URL
        } else {
            saved
        }
    }

    fun setBaseUrl(newUrl: String) {
        var formatted = newUrl.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "https://$formatted"
        }
        if (!formatted.endsWith("/")) {
            formatted += "/"
        }
        // If user provided base folder like .../st/server/ without api/
        if (formatted.endsWith("/st/server/")) {
            formatted += "api/"
        } else if (formatted.endsWith("/server/")) {
            formatted += "api/"
        }
        prefs.edit().putString(KEY_BASE_URL, formatted).apply()
        currentBaseUrl = formatted
        cachedService = null
    }

    fun getApiService(): AttendanceApiService {
        val activeUrl = getSavedBaseUrl()
        if (cachedService == null || currentBaseUrl != activeUrl) {
            currentBaseUrl = activeUrl
            cachedService = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(AttendanceApiService::class.java)
        }
        return cachedService!!
    }
}
