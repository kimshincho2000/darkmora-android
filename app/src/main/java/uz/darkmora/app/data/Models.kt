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
