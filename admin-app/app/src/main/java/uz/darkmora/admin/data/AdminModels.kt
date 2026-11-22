package uz.darkmora.admin.data

import com.google.gson.annotations.SerializedName

data class AdminLoginData(
    @SerializedName("requires_2fa") val requires2fa: Boolean = false,
    @SerializedName("temp_token") val tempToken: String? = null,
    @SerializedName("email_masked") val emailMasked: String? = null,
    val message: String? = null
)

data class AdminLoginResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminLoginData? = null
)

data class AdminInfo(
    val id: Int = 0,
    val role: String? = "owner",
    val label: String? = "Super Admin",
    val email: String? = "",
    @SerializedName("last_seen") val lastSeen: String? = ""
)

data class AdminVerifyData(
    @SerializedName("admin_token") val adminToken: String? = null,
    val admin: AdminInfo? = null
)

data class AdminVerifyResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminVerifyData? = null
)

data class AdminAuthCheckData(
    val authenticated: Boolean = false,
    val admin: AdminInfo? = null
)

data class AdminAuthCheckResponse(
    val ok: Boolean = false,
    val data: AdminAuthCheckData? = null
)

data class AdminBaseResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

data class AdminOverview(
    @SerializedName("total_anime") val totalAnime: Int = 0,
    @SerializedName("total_episodes") val totalEpisodes: Int = 0,
    @SerializedName("total_users") val totalUsers: Int = 0,
    @SerializedName("total_views") val totalViews: Long = 0,
    @SerializedName("total_sessions") val totalSessions: Int = 0,
    @SerializedName("online_count") val onlineCount: Int = 0,
    @SerializedName("total_comments") val totalComments: Int = 0,
    @SerializedName("pending_requests") val pendingRequests: Int = 0,
    @SerializedName("total_requests") val totalRequests: Int = 0,
    @SerializedName("total_banned_ips") val totalBannedIps: Int = 0,
    @SerializedName("today_unique") val todayUnique: Int = 0,
    @SerializedName("today_total") val todayTotal: Int = 0,
    @SerializedName("yesterday_unique") val yesterdayUnique: Int = 0,
    @SerializedName("yesterday_total") val yesterdayTotal: Int = 0
)

data class AdminMemoryInfo(
    @SerializedName("total_mb") val totalMb: Int = 4096,
    @SerializedName("available_mb") val availableMb: Int = 0,
    @SerializedName("used_mb") val usedMb: Int = 0,
    @SerializedName("used_pct") val usedPct: Float = 0f
)

data class AdminSystemInfo(
    val memory: AdminMemoryInfo? = null,
    val pressure: String? = "0.00",
    @SerializedName("shield_status") val shieldStatus: String? = "Faol",
    @SerializedName("live_tracked_requests") val liveTrackedRequests: Int = 0
)

data class DailyHistoryPoint(
    val date: String = "",
    @SerializedName("unique_visitors") val uniqueVisitors: Int = 0,
    @SerializedName("total_actions") val totalActions: Int = 0,
    @SerializedName("watch_count") val watchCount: Int = 0,
    @SerializedName("search_count") val searchCount: Int = 0
)

data class TopWatchedTodayItem(
    val title: String = "",
    val count: Int = 0
)

data class TopSearchTodayItem(
    val query: String = "",
    val count: Int = 0
)

data class DailyAnalyticsData(
    @SerializedName("today_unique") val todayUnique: Int = 0,
    @SerializedName("today_total") val todayTotal: Int = 0,
    @SerializedName("yesterday_unique") val yesterdayUnique: Int = 0,
    @SerializedName("yesterday_total") val yesterdayTotal: Int = 0,
    @SerializedName("bot_today_active") val botTodayActive: Int = 0,
    val history: List<DailyHistoryPoint>? = null,
    @SerializedName("top_watched_today") val topWatchedToday: List<TopWatchedTodayItem>? = null,
    @SerializedName("top_searches_today") val topSearchesToday: List<TopSearchTodayItem>? = null,
    val devices: Map<String, Int>? = null
)

data class TopAnimeItem(
    val id: Int = 0,
    val title: String? = "",
    val nom: String? = "",
    val slug: String? = "",
    val poster: String? = "",
    val views: Long = 0,
    val year: String? = "",
    val genres: String? = ""
) {
    fun displayName(): String = title?.takeIf { it.isNotBlank() } ?: nom?.takeIf { it.isNotBlank() } ?: "Anime #$id"
}

data class AdminDashboardData(
    val overview: AdminOverview? = null,
    val system: AdminSystemInfo? = null,
    @SerializedName("daily_analytics") val dailyAnalytics: DailyAnalyticsData? = null,
    @SerializedName("top_anime") val topAnime: List<TopAnimeItem>? = null,
    @SerializedName("recent_anime") val recentAnime: List<TopAnimeItem>? = null
)

data class AdminDashboardResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminDashboardData? = null
)

data class AdminActivityItem(
    val id: Int = 0,
    val ip: String? = "",
    val user: String? = "",
    val username: String? = "",
    @SerializedName("action_type") val actionType: String? = "",
    @SerializedName("action_label") val actionLabel: String? = "",
    val label: String? = "",
    val title: String? = "",
    val details: String? = "",
    val device: String? = "",
    val time: String? = "",
    @SerializedName("time_ago") val timeAgo: String? = "",
    @SerializedName("created_at") val createdAt: String? = ""
)

data class AdminActivitiesData(
    val activities: List<AdminActivityItem>? = null,
    val analytics: DailyAnalyticsData? = null
)

data class AdminActivitiesResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminActivitiesData? = null
)

data class AdminUserItem(
    val id: Int = 0,
    val username: String? = "",
    @SerializedName("display_name") val displayName: String? = "",
    val email: String? = "",
    val phone: String? = "",
    @SerializedName("tg_id") val tgId: String? = "",
    @SerializedName("created_at") val createdAt: String? = "",
    @SerializedName("last_login") val lastLogin: String? = ""
)

data class AdminUsersData(
    val users: List<AdminUserItem>? = null
)

data class AdminUsersResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminUsersData? = null
)

data class AdminBroadcastItem(
    val id: Int = 0,
    val title: String = "",
    val body: String = "",
    @SerializedName("anime_slug") val animeSlug: String? = "",
    @SerializedName("image_url") val imageUrl: String? = "",
    @SerializedName("created_at") val createdAt: String? = ""
)

data class AdminBroadcastsData(
    val broadcasts: List<AdminBroadcastItem>? = null
)

data class AdminBroadcastsResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminBroadcastsData? = null
)

data class AdminCrashItem(
    val id: Int = 0,
    @SerializedName("app_version") val appVersion: String = "",
    @SerializedName("version_code") val versionCode: Int = 0,
    @SerializedName("device_model") val deviceModel: String = "",
    @SerializedName("android_version") val androidVersion: String = "",
    @SerializedName("thread_name") val threadName: String = "",
    @SerializedName("error_title") val errorTitle: String = "",
    @SerializedName("stack_trace") val stackTrace: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class AdminCrashesData(
    val crashes: List<AdminCrashItem>? = null
)

data class AdminCrashesResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminCrashesData? = null
)

data class AdminUserRequestItem(
    val id: Int = 0,
    @SerializedName("user_id") val userId: String? = "",
    val type: String? = "support_request",
    val title: String = "",
    val details: String? = "",
    val contact: String? = "",
    val ip: String? = "",
    val status: String? = "pending",
    @SerializedName("created_at") val createdAt: String? = ""
)

data class AdminRequestsData(
    val requests: List<AdminUserRequestItem>? = null,
    val count: Int = 0
)

data class AdminRequestsResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val data: AdminRequestsData? = null
)
