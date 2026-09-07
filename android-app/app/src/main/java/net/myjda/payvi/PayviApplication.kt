package net.myjda.payvi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PayviApplication : Application() {

    companion object {
        const val SYNC_NOTIFICATION_CHANNEL_ID = "payvi_sync"
        const val ALERT_NOTIFICATION_CHANNEL_ID = "payvi_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                SYNC_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_sync),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notif_channel_sync_desc)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notif_channel_alerts_desc)
            }
        )
    }
}
