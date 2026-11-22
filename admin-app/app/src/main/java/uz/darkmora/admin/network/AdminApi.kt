package uz.darkmora.admin.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uz.darkmora.admin.data.*
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object AdminApi {
    private const val BASE_URL = "https://darkmora.uz/api"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private inline fun <reified T> executeJson(request: Request): T {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string()?.trim().orEmpty()
            val looksJson = raw.startsWith("{") || raw.startsWith("[")
            if (!looksJson) {
                throw IOException("Server xatosi (HTTP ${response.code})")
            }
            return gson.fromJson(raw, T::class.java)
                ?: throw IOException("Bo'sh JSON javob (HTTP ${response.code})")
        }
    }

    suspend fun login(username: String, password: String): AdminLoginResponse = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf("username" to username, "password" to password)).toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("$BASE_URL?action=login")
            .post(body)
            .build()
        executeJson(req)
    }

    suspend fun verify2fa(tempToken: String, code: String): AdminVerifyResponse = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf("temp_token" to tempToken, "code" to code)).toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_verify_2fa")
            .post(body)
            .build()
        executeJson(req)
    }

    suspend fun resend2fa(tempToken: String): AdminBaseResponse = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf("temp_token" to tempToken)).toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_resend_2fa")
            .post(body)
            .build()
        executeJson(req)
    }

    suspend fun checkAuth(token: String): AdminAuthCheckResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_check_auth")
            .addHeader("X-Admin-Token", token)
            .build()
        executeJson(req)
    }

    suspend fun logout(token: String): AdminBaseResponse = withContext(Dispatchers.IO) {
        val body = gson.toJson(mapOf("admin_token" to token)).toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_logout")
            .addHeader("X-Admin-Token", token)
            .post(body)
            .build()
        executeJson(req)
    }

    suspend fun getDashboardStats(token: String): AdminDashboardResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_dashboard_stats")
            .addHeader("X-Admin-Token", token)
            .build()
        executeJson(req)
    }

    suspend fun getActivities(token: String, limit: Int = 60): AdminActivitiesResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_get_activities&limit=$limit")
            .addHeader("X-Admin-Token", token)
            .build()
        executeJson(req)
    }

    suspend fun getUsers(token: String, search: String = "", limit: Int = 60): AdminUsersResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_get_users&limit=$limit&search=${enc(search)}")
            .addHeader("X-Admin-Token", token)
            .build()
        executeJson(req)
    }

    suspend fun sendBroadcast(
        token: String,
        title: String,
        bodyText: String,
        animeSlug: String = "",
        imageUrl: String = ""
    ): AdminBaseResponse = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "title" to title,
            "body" to bodyText,
            "anime_slug" to animeSlug,
            "image_url" to imageUrl
        )
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_send_broadcast")
            .addHeader("X-Admin-Token", token)
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA))
            .build()
        executeJson(req)
    }

    suspend fun getBroadcasts(token: String, limit: Int = 25): AdminBroadcastsResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_get_broadcasts&limit=$limit")
            .addHeader("X-Admin-Token", token)
            .build()
        executeJson(req)
    }

    suspend fun getCrashes(token: String, limit: Int = 20): AdminCrashesResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_get_crashes&limit=$limit")
            .addHeader("X-Admin-Token", token)
            .build()
        executeJson(req)
    }

    suspend fun getRequests(token: String, status: String = "all"): AdminRequestsResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_get_requests&status=${enc(status)}")
            .addHeader("X-Admin-Token", token)
            .build()
        executeJson(req)
    }

    suspend fun updateRequestStatus(token: String, id: Int, status: String): AdminBaseResponse = withContext(Dispatchers.IO) {
        val payload = mapOf("id" to id, "status" to status)
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_update_request")
            .addHeader("X-Admin-Token", token)
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA))
            .build()
        executeJson(req)
    }

    suspend fun deleteRequest(token: String, id: Int): AdminBaseResponse = withContext(Dispatchers.IO) {
        val payload = mapOf("id" to id)
        val req = Request.Builder()
            .url("$BASE_URL?action=admin_delete_request")
            .addHeader("X-Admin-Token", token)
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA))
            .build()
        executeJson(req)
    }
}
