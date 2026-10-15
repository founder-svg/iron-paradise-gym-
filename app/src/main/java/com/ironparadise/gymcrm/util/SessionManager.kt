package com.ironparadise.gymcrm.util

import android.content.Context
import android.content.SharedPreferences
import com.ironparadise.gymcrm.data.db.entities.UserEntity
import com.ironparadise.gymcrm.data.model.UserRole

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("iron_paradise_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_STATUS = "user_status"
        private const val KEY_AUTH_PROVIDER = "auth_provider"
    }

    fun saveUserSession(user: UserEntity) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putLong(KEY_USER_ID, user.id)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_ROLE, user.role)
            putString(KEY_USER_STATUS, user.status)
            putString(KEY_AUTH_PROVIDER, user.authProvider)
            apply()
        }
    }

    fun saveUserSession(id: Long, name: String, role: String, status: String = "APPROVED", email: String = "") {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putLong(KEY_USER_ID, id)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_ROLE, role)
            putString(KEY_USER_STATUS, status)
            apply()
        }
    }

    fun updateStatus(newStatus: String) {
        prefs.edit().putString(KEY_USER_STATUS, newStatus).apply()
    }

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    val userId: Long get() = prefs.getLong(KEY_USER_ID, 0L)
    val userName: String get() = prefs.getString(KEY_USER_NAME, "User") ?: "User"
    val userEmail: String get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    val userStatus: String get() = prefs.getString(KEY_USER_STATUS, "APPROVED") ?: "APPROVED"
    val authProvider: String get() = prefs.getString(KEY_AUTH_PROVIDER, "EMAIL") ?: "EMAIL"

    val isApproved: Boolean get() = userStatus.equals("APPROVED", ignoreCase = true)

    val userRole: UserRole
        get() = try {
            UserRole.valueOf(prefs.getString(KEY_USER_ROLE, "STAFF") ?: "STAFF")
        } catch (e: Exception) {
            UserRole.STAFF
        }

    val isAdmin: Boolean get() = true
    val isStaff: Boolean get() = true

    fun logout() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_NAME)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_ROLE)
            remove(KEY_USER_STATUS)
            remove(KEY_USER_ID)
            remove(KEY_AUTH_PROVIDER)
            apply()
        }
    }

    fun clearSession() {
        logout()
    }
}
