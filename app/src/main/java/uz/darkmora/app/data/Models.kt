package uz.darkmora.app.data

import com.google.gson.annotations.SerializedName

data class Anime(
    val id: Int,
    val slug: String? = null,
    val url: String? = null,
    val title: String? = null,
    val nom: String? = null,
    val poster: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val banner: String? = null,
    val year: String? = null,
    val yili: String? = null,
    val rating: String? = null,
    val reyting: String? = null,
    val genres: String? = null,
    val janri: String? = null,
    val country: String? = null,
    val lang: String? = null,
    val status: String? = null,
    val description: String? = null,
    val tavsif: String? = null,
    @SerializedName("episodes_count") val episodesCount: Any? = null,
    @SerializedName("qismlar_soni") val qismlarSoni: Any? = null
) {
    fun displayTitle(): String = title ?: nom ?: "Nomsiz anime"
    fun displayPoster(): String = poster ?: imageUrl ?: ""
    fun displayRating(): String = rating ?: reyting ?: "8.5"
    fun displayYear(): String = year ?: yili ?: "2024"
    fun displayGenres(): String = genres ?: janri ?: ""
    fun displayEpisodes(): String = (episodesCount ?: qismlarSoni ?: "").toString()
    fun displayDescription(): String = description ?: tavsif ?: "Tavsif mavjud emas."
    fun getEffectiveSlug(): String = slug ?: id.toString()
}

data class Episode(
    val qism: Int,
    val title: String? = null,
    @SerializedName("video_url") val videoUrl: String? = null,
    @SerializedName("embed_url") val embedUrl: String? = null,
    val duration: String? = null
)

data class HomeResponse(
    val ok: Boolean,
    val data: HomeData?
)

data class HomeData(
    val featured: List<Anime>? = emptyList(),
    val trending: List<Anime>? = emptyList(),
    val latest: List<Anime>? = emptyList(),
    @SerializedName("latest_episodes") val latestEpisodes: List<Anime>? = emptyList(),
    val top: List<Anime>? = emptyList(),
    @SerializedName("top_rated") val topRated: List<Anime>? = emptyList(),
    val genres: List<String>? = emptyList()
)

data class CatalogResponse(
    val ok: Boolean,
    val data: CatalogData?
)

data class CatalogData(
    val items: List<Anime>? = emptyList(),
    val total: Int = 0,
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class AnimeDetailResponse(
    val ok: Boolean,
    val data: AnimeDetailData?
)

data class AnimeDetailData(
    val anime: Anime?,
    val episodes: List<Episode>? = emptyList(),
    val similar: List<Anime>? = emptyList()
)

data class UserProfile(
    val id: Any? = null,
    @SerializedName("db_id") val dbId: Int? = null,
    @SerializedName("user_code") val userCode: String? = null,
    val username: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_owner") val isOwner: Boolean = false
) {
    fun getEffectiveName(): String = displayName?.takeIf { it.isNotBlank() } ?: username ?: "DarkMora Foydalanuvchisi"
}

data class AuthData(
    val user: UserProfile? = null,
    val token: String? = null,
    val session: String? = null,
    val msg: String? = null
)

data class AuthResponse(
    val ok: Boolean,
    val status: String? = null,
    val error: String? = null,
    val data: AuthData? = null
)

data class TgAuthInitData(
    val token: String,
    @SerializedName("bot_url") val botUrl: String,
    @SerializedName("expires_in") val expiresIn: Int = 600
)

data class TgAuthInitResponse(
    val ok: Boolean,
    val error: String? = null,
    val data: TgAuthInitData? = null
)

data class TgAuthPollResponse(
    val ok: Boolean,
    val status: String? = null,
    val error: String? = null,
    val data: AuthData? = null
)

data class UpdateInfo(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("apk_url") val apkUrl: String,
    val changelog: String? = null,
    @SerializedName("is_force") val isForce: Boolean = false
)

data class CheckUpdateResponse(
    val ok: Boolean,
    val error: String? = null,
    val data: UpdateInfo? = null
)

data class BaseResponse(
    val ok: Boolean,
    val error: String? = null,
    val message: String? = null
)
