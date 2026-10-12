package com.ironparadise.gymcrm.util

import com.ironparadise.gymcrm.BuildConfig

enum class AppRoleType {
    ADMIN,
    STAFF
}

object AppConfig {
    const val MASTER_ADMIN_APPROVAL_CODE = "1234"

    val currentAppRole: AppRoleType
        get() = try {
            if (BuildConfig.FLAVOR.equals("staff", ignoreCase = true)) {
                AppRoleType.STAFF
            } else {
                AppRoleType.ADMIN
            }
        } catch (e: Exception) {
            AppRoleType.ADMIN
        }

    val isStaffApp: Boolean get() = currentAppRole == AppRoleType.STAFF
    val isAdminApp: Boolean get() = currentAppRole == AppRoleType.ADMIN
}
