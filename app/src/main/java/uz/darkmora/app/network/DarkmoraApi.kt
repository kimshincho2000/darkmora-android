package uz.darkmora.app.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import uz.darkmora.app.data.AnimeDetailResponse
import uz.darkmora.app.data.CatalogResponse
import uz.darkmora.app.data.HomeResponse
import java.util.concurrent.TimeUnit

object DarkmoraApi {
    private const val BASE_URL = "https://darkmora.uz/api"
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
        val url = "$BASE_URL?action=catalog&page=$page&genre=$genre&q=$q"
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
            .url("$BASE_URL?action=anime&slug=$slug")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, AnimeDetailResponse::class.java)
        }
    }

    suspend fun search(q: String): CatalogResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL?action=search&q=$q")
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: "{}"
            gson.fromJson(json, CatalogResponse::class.java)
        }
    }
}
