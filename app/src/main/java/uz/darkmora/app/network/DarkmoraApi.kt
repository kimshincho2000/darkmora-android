package uz.darkmora.app.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uz.darkmora.app.data.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object DarkmoraApi {
    private const val BASE_URL = "https://darkmora.uz/api"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=home")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, HomeResponse::class.java)
        }
    }

    suspend fun getCatalog(page: Int = 1, genre: String = "", q: String = ""): CatalogResponse = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?action=catalog&page=$page&genre=${enc(genre)}&q=${enc(q)}"
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, CatalogResponse::class.java)
        }
    }

    suspend fun getAnimeDetail(slug: String): AnimeDetailResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=anime&slug=${enc(slug)}")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, AnimeDetailResponse::class.java)
        }
    }

    suspend fun search(q: String): CatalogResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=search&q=${enc(q)}")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, CatalogResponse::class.java)
        }
    }

    // --- AUTH METHODS ---

    suspend fun initTelegramAuth(): TgAuthInitResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=tg_auth_init")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, TgAuthInitResponse::class.java)
        }
    }

    suspend fun pollTelegramAuth(token: String): TgAuthPollResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=tg_auth_poll&token=${enc(token)}")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, TgAuthPollResponse::class.java)
        }
    }

    suspend fun sendEmailCode(email: String): BaseResponse = withContext(Dispatchers.IO) {
        val payload = mapOf("email" to email)
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=send_email_code")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, BaseResponse::class.java)
        }
    }

    suspend fun register(username: String, pass: String, email: String, code: String): AuthResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "username" to username,
            "password" to pass,
            "email" to email,
            "code" to code,
            "display_name" to username
        )
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=register")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, AuthResponse::class.java)
        }
    }

    suspend fun login(login: String, pass: String): AuthResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "username" to login,
            "password" to pass
        )
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=login")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, AuthResponse::class.java)
        }
    }

    suspend fun getUserProfile(token: String): AuthResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=user")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, AuthResponse::class.java)
        }
    }

    suspend fun updateProfile(
        token: String,
        displayName: String? = null,
        photoBase64: String? = null,
        newPass: String? = null
    ): BaseResponse = withContext(Dispatchers.IO) {
        val payload = mutableMapOf<String, String>()
        if (!displayName.isNullOrBlank()) payload["display_name"] = displayName
        if (!photoBase64.isNullOrBlank()) payload["photo_base64"] = photoBase64
        if (!newPass.isNullOrBlank()) payload["new_password"] = newPass

        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=update_profile")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, BaseResponse::class.java)
        }
    }

    // --- AUTO-UPDATE METHOD ---

    suspend fun checkUpdate(): CheckUpdateResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=check_update")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, CheckUpdateResponse::class.java)
        }
    }
}
