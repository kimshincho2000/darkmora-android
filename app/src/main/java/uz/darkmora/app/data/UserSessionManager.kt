package uz.darkmora.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UserSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("darkmora_user_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSession(token: String, user: UserProfile) {
        prefs.edit()
            .putString("auth_token", token)
            .putString("user_json", gson.toJson(user))
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun updateProfile(user: UserProfile) {
        prefs.edit()
            .putString("user_json", gson.toJson(user))
            .apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun getUser(): UserProfile? {
        val json = prefs.getString("user_json", null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("is_logged_in", false) && !getToken().isNullOrBlank()
    }

    fun clearSession() {
        prefs.edit()
            .remove("auth_token")
            .remove("user_json")
            .putBoolean("is_logged_in", false)
            .apply()
    }

    // --- FAVORITES (Stores both IDs and full Anime objects for instant display) ---

    fun toggleFavoriteAnime(anime: Anime): Boolean {
        val currentList = getFavoriteAnimes().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == anime.id }
        val isAdded = if (existingIndex >= 0) {
            currentList.removeAt(existingIndex)
            false
        } else {
            currentList.add(0, anime)
            true
        }
        saveFavoriteAnimesList(currentList)
        return isAdded
    }

    fun toggleFavorite(animeId: Int): Boolean {
        val favs = getFavorites().toMutableSet()
        val isAdded = if (favs.contains(animeId)) {
            favs.remove(animeId)
            val list = getFavoriteAnimes().filter { it.id != animeId }
            prefs.edit().putString("favorites_anime_json", gson.toJson(list)).apply()
            false
        } else {
            favs.add(animeId)
            true
        }
        prefs.edit().putStringSet("favorites", favs.map { it.toString() }.toSet()).apply()
        return isAdded
    }

    private fun saveFavoriteAnimesList(list: List<Anime>) {
        val ids = list.map { it.id.toString() }.toSet()
        prefs.edit()
            .putString("favorites_anime_json", gson.toJson(list))
            .putStringSet("favorites", ids)
            .apply()
    }

    fun getFavoriteAnimes(): List<Anime> {
        val json = prefs.getString("favorites_anime_json", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Anime>>() {}.type
            gson.fromJson<List<Anime>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFavorite(animeId: Int): Boolean {
        return getFavorites().contains(animeId) || getFavoriteAnimes().any { it.id == animeId }
    }

    fun getFavorites(): Set<Int> {
        val set = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        val fromObjects = getFavoriteAnimes().map { it.id }.toSet()
        return set.mapNotNull { it.toIntOrNull() }.toSet() + fromObjects
    }

    // --- WATCH HISTORY ---

    fun saveLastWatchedEpisode(slug: String, episodeNum: Int) {
        prefs.edit().putInt("last_ep_$slug", episodeNum).apply()
    }

    fun getLastWatchedEpisode(slug: String): Int {
        return prefs.getInt("last_ep_$slug", 0)
    }

    // --- THEME & PREFERENCES ---

    fun isDarkMode(): Boolean {
        return prefs.getBoolean("pref_dark_mode", true)
    }

    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean("pref_dark_mode", isDark).apply()
    }

    fun getLanguage(): String {
        return prefs.getString("pref_language", "O'zbekcha") ?: "O'zbekcha"
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("pref_language", lang).apply()
    }

    // --- NOTIFICATIONS ---

    fun isNotificationsEnabled(): Boolean {
        return prefs.getBoolean("pref_notifications_enabled", true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_notifications_enabled", enabled).apply()
    }

    fun getReadNotificationIds(): Set<Int> {
        val raw = prefs.getStringSet("read_notif_ids", emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun markNotificationsRead(ids: Collection<Int>) {
        val current = getReadNotificationIds().toMutableSet()
        current.addAll(ids)
        prefs.edit().putStringSet("read_notif_ids", current.map { it.toString() }.toSet()).apply()
    }

    fun getNotifiedSystemKeys(): Set<String> {
        return prefs.getStringSet("system_notified_keys", emptySet()) ?: emptySet()
    }

    fun addNotifiedSystemKey(key: String) {
        val current = getNotifiedSystemKeys().toMutableSet()
        current.add(key)
        prefs.edit().putStringSet("system_notified_keys", current).apply()
    }
}
