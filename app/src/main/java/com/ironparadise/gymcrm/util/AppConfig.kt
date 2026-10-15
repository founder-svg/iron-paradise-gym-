package com.ironparadise.gymcrm.util

import com.ironparadise.gymcrm.BuildConfig

enum class AppRoleType {
    ADMIN,
    STAFF
}

object AppConfig {
    const val MASTER_ADMIN_APPROVAL_CODE = "1234"
    const val DEFAULT_SERVER_URL = "https://iron-paradise-gym-api.onrender.com"

    val currentAppRole: AppRoleType get() = AppRoleType.ADMIN
    val isStaffApp: Boolean get() = false
    val isAdminApp: Boolean get() = true
}
