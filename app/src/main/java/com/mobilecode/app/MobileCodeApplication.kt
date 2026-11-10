package com.mobilecode.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mobilecode.app.core.filesystem.WorkspaceManager

class MobileCodeApplication : Application() {

    lateinit var workspaceManager: WorkspaceManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Workspace Manager without loading default sample projects
        workspaceManager = WorkspaceManager(this)

        // Create notification channel for background terminal/server processes
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val terminalChannel = NotificationChannel(
                CHANNEL_ID_TERMINAL,
                "Mobile Code Background Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps developer terminal sessions and background servers alive"
            }
            notificationManager?.createNotificationChannel(terminalChannel)

            val envChannel = NotificationChannel(
                CHANNEL_ID_ENVIRONMENT,
                "Docker Market & Environment Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background download and install progress for Docker and runtime environments"
            }
            notificationManager?.createNotificationChannel(envChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_TERMINAL = "mobile_code_terminal_channel"
        const val CHANNEL_ID_ENVIRONMENT = "mobile_code_environment_channel"
        lateinit var instance: MobileCodeApplication
            private set
    }
}

