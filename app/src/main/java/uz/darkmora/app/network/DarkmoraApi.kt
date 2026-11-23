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

    suspend fun getHome(token: String? = null): HomeResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$BASE_URL?action=home")
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        executeJson(builder.build())
    }

    suspend fun getCatalog(
        page: Int = 1,
        genre: String = "",
        q: String = "",
        perPage: Int = 24,
        token: String? = null
    ): CatalogResponse = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?action=catalog&page=$page&per_page=$perPage&genre=${enc(genre)}&q=${enc(q)}"
        val builder = Request.Builder().url(url)
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        executeJson(builder.build())
    }

    suspend fun getAnimeDetail(
        slug: String,
        ep: Int? = null,
        token: String? = null
    ): AnimeDetailResponse = withContext(Dispatchers.IO) {
        val epParam = if (ep != null && ep > 0) "&ep=$ep" else ""
        val builder = Request.Builder()
            .url("$BASE_URL?action=anime&slug=${enc(slug)}$epParam")
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        executeJson(builder.build())
    }

    suspend fun search(q: String, token: String? = null): CatalogResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$BASE_URL?action=search&q=${enc(q)}")
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        executeJson(builder.build())
    }

    suspend fun getBroadcasts(limit: Int = 15): AppBroadcastsResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=app_broadcasts&limit=$limit")
            .build()
        executeJson(request)
    }

    suspend fun logPlayerError(slug: String, ep: Int, code: String, msg: String) = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL?action=log_player_error&slug=${enc(slug)}&ep=$ep&code=${enc(code)}&msg=${enc(msg)}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    // --- BOOKMARKS (CLOUD SYNC) ---

    suspend fun toggleBookmark(token: String, animeId: Int): ToggleBookmarkResponse = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf("anime_id" to animeId)).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=toggle_bookmark")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        executeJson(request)
    }

    suspend fun getBookmarks(token: String): BookmarksResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=get_bookmarks")
            .addHeader("Authorization", "Bearer $token")
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

    suspend fun sendResetCode(email: String): BaseResponse = withContext(Dispatchers.IO) {
        val payload = mapOf("email" to email)
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=send_reset_code")
            .post(body)
            .build()
        executeJson(request)
    }

    suspend fun resetPassword(email: String, code: String, newPass: String): AuthResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "email" to email,
            "code" to code,
            "password" to newPass
        )
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BASE_URL?action=reset_password")
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

    // --- COMMENTS METHODS ---

    suspend fun getComments(animeId: Int, limit: Int = 50): CommentsResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=get_comments&anime_id=$animeId&limit=$limit")
            .build()
        executeJson(req)
    }

    suspend fun addComment(token: String, animeId: Int, text: String): AddCommentResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "anime_id" to animeId,
            "text" to text
        )
        val body = gson.toJson(payload).toRequestBody(JSON_MEDIA)
        val reqBuilder = Request.Builder()
            .url("$BASE_URL?action=add_comment")
            .post(body)
        if (token.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $token")
        }
        executeJson(reqBuilder.build())
    }

    // --- AUTO-UPDATE METHOD ---

    suspend fun checkUpdate(token: String? = null): CheckUpdateResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$BASE_URL?action=check_update")
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        executeJson(builder.build())
    }
}
