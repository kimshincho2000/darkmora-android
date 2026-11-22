package uz.darkmora.app.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uz.darkmora.app.data.*
import java.io.IOException
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

    private inline fun <reified T> executeJson(request: Request): T {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string()?.trim().orEmpty()
            val looksLikeJson = raw.startsWith("{") || raw.startsWith("[")
            if (!response.isSuccessful && !looksLikeJson) {
                throw IOException("Server xatosi (HTTP ${response.code})")
            }
            if (!looksLikeJson) {
                throw IOException("Server noto'g'ri javob qaytardi (HTTP ${response.code})")
            }
            return gson.fromJson(raw, T::class.java)
                ?: throw IOException("Bo'sh JSON javob (HTTP ${response.code})")
        }
    }

    suspend fun getHome(): HomeResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=home")
            .build()
        executeJson(request)
    }

    suspend fun getCatalog(page: Int = 1, genre: String = "", q: String = ""): CatalogResponse = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?action=catalog&page=$page&genre=${enc(genre)}&q=${enc(q)}"
        val request = Request.Builder()
            .url(url)
            .build()
        executeJson(request)
    }

    suspend fun getAnimeDetail(slug: String): AnimeDetailResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=anime&slug=${enc(slug)}")
            .build()
        executeJson(request)
    }

    suspend fun search(q: String): CatalogResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=search&q=${enc(q)}")
            .build()
        executeJson(request)
    }

    // --- AUTH METHODS ---

    suspend fun initTelegramAuth(): TgAuthInitResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=tg_auth_init")
            .build()
        executeJson(request)
    }

    suspend fun pollTelegramAuth(token: String): TgAuthPollResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=tg_auth_poll&token=${enc(token)}")
            .build()
        executeJson(request)
    }

    suspend fun sendEmailCode(email: String): BaseResponse = withContext(Dispatchers.IO) {
        val payload = mapOf("email" to email)
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=send_email_code")
            .post(body)
            .build()
        executeJson(request)
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
        executeJson(request)
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
        executeJson(request)
    }

    suspend fun getUserProfile(token: String): AuthResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=user")
            .addHeader("Authorization", "Bearer $token")
            .build()
        executeJson(request)
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
        executeJson(request)
    }

    suspend fun submitSupportRequest(
        token: String,
        title: String,
        details: String
    ): BaseResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "type" to "support_request",
            "title" to title,
            "details" to details
        )
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val reqBuilder = Request.Builder()
            .url("$BASE_URL?action=submit_user_request")
            .post(body)
        if (token.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $token")
        }
        executeJson(reqBuilder.build())
    }

    // --- AUTO-UPDATE METHOD ---

    suspend fun checkUpdate(): CheckUpdateResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=check_update")
            .build()
        executeJson(request)
    }
}
