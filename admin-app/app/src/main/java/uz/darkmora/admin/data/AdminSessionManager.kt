package uz.darkmora.admin.data

import android.content.Context

class AdminSessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("darkmora_admin_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String, email: String = "", label: String = "Super Admin") {
        prefs.edit()
            .putString("admin_token", token)
            .putString("admin_email", email)
            .putString("admin_label", label)
            .apply()
    }

    fun getToken(): String? = prefs.getString("admin_token", null)?.takeIf { it.isNotBlank() }

    fun getEmail(): String = prefs.getString("admin_email", "") ?: ""

    fun getLabel(): String = prefs.getString("admin_label", "Super Admin") ?: "Super Admin"

    fun clear() {
        prefs.edit().clear().apply()
    }
}
