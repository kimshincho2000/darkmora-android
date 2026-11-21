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
}
