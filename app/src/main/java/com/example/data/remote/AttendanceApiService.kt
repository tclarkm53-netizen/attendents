package com.example.data.remote

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
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

class ServerResponseSanitizerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val responseBody = response.body ?: return response

        val rawBody = responseBody.string()
        val trimmed = rawBody.trim().removePrefix("\uFEFF").trim()

        // 1. Check if the body is already valid JSON
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            val mediaType = responseBody.contentType() ?: "application/json; charset=utf-8".toMediaType()
            return response.newBuilder()
                .body(trimmed.toResponseBody(mediaType))
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
        }

        // 2. Check if a valid JSON object is embedded after PHP warnings/notices
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val potentialJson = trimmed.substring(firstBrace, lastBrace + 1)
            try {
                JSONObject(potentialJson)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                return response.newBuilder()
                    .body(potentialJson.toResponseBody(mediaType))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build()
            } catch (_: Exception) {
                // Proceed to HTML error extraction
            }
        }

        // 3. Extract meaningful error message from HTML / PHP errors
        val cleanMessage = parseServerHtmlError(trimmed, response.code)

        val escapedMsg = cleanMessage.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")
        val syntheticJson = "{\"success\":false,\"message\":\"$escapedMsg\",\"timestamp\":${System.currentTimeMillis()}}"

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        return response.newBuilder()
            .code(if (response.code in 200..299) 200 else response.code)
            .body(syntheticJson.toResponseBody(jsonMediaType))
            .header("Content-Type", "application/json; charset=utf-8")
            .build()
    }

    private fun parseServerHtmlError(raw: String, httpCode: Int): String {
        val unescaped = raw
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#039;", "'")

        if (unescaped.contains("Parse error", ignoreCase = true) || unescaped.contains("syntax error", ignoreCase = true)) {
            val clean = unescaped.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            val shortDesc = if (clean.length > 200) clean.take(200) + "..." else clean
            return "সার্ভার পিএইচপি সিনট্যাক্স এরর ($shortDesc)। অনুগ্রহ করে সার্ভারের db.php ফাইলের ২২-২৩ নম্বর লাইনে কোটেশন ও সেমিকোলন (;) ঠিক করুন।"
        }

        if (unescaped.contains("Fatal error", ignoreCase = true)) {
            val clean = unescaped.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            val shortDesc = if (clean.length > 180) clean.take(180) + "..." else clean
            return "সার্ভার পিএইচপি ক্র্যাশ ($shortDesc)।"
        }

        if (unescaped.contains("Database connection failed", ignoreCase = true) || unescaped.contains("PDOException", ignoreCase = true)) {
            return "সার্ভার ডাটাবেজ কানেকশন ব্যর্থ হয়েছে। db.php ফাইলের ডাটাবেজ হোস্ট, নাম, ইউজারনেম ও পাসওয়ার্ড সঠিক আছে কিনা পরীক্ষা করুন।"
        }

        if (httpCode == 404 || unescaped.contains("404 Not Found", ignoreCase = true)) {
            return "সার্ভার ফাইল পাওয়া যায়নি (404 Not Found)। সার্ভার URL ও api ফোল্ডারের পাথ চেক করুন।"
        }

        if (httpCode == 500 || unescaped.contains("500 Internal Server Error", ignoreCase = true)) {
            return "সার্ভার ইন্টারনাল এরর (HTTP 500)। সার্ভার কনফিগারেশন চেক করুন।"
        }

        val clean = unescaped.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        val snippet = if (clean.length > 120) clean.take(120) + "..." else clean
        return if (snippet.isNotBlank()) {
            "সার্ভার এরর রেসপন্স: $snippet"
        } else {
            "সার্ভার থেকে সঠিক রেসপন্স পাওয়া যায়নি (HTTP $httpCode)।"
        }
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
        .addInterceptor(ServerResponseSanitizerInterceptor())
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
                .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
                .build()
                .create(AttendanceApiService::class.java)
        }
        return cachedService!!
    }
}
