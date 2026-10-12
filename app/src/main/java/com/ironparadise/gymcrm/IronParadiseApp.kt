package com.ironparadise.gymcrm

import android.app.Application
import com.ironparadise.gymcrm.data.db.AppDatabase
import com.ironparadise.gymcrm.data.repository.GymRepository
import com.ironparadise.gymcrm.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class IronParadiseApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { GymRepository(database) }
    val sessionManager by lazy { SessionManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: IronParadiseApp
            private set
    }
}
