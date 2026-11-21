package uz.darkmora.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

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

    fun toggleFavorite(animeId: Int): Boolean {
        val favs = getFavorites().toMutableSet()
        val isAdded = if (favs.contains(animeId)) {
            favs.remove(animeId)
            false
        } else {
            favs.add(animeId)
            true
        }
        prefs.edit().putStringSet("favorites", favs.map { it.toString() }.toSet()).apply()
        return isAdded
    }

    fun isFavorite(animeId: Int): Boolean {
        return getFavorites().contains(animeId)
    }

    fun getFavorites(): Set<Int> {
        val set = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toSet()
    }
}
