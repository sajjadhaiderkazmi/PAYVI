package net.myjda.payvi.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.myjda.payvi.PayviApplication
import net.myjda.payvi.R
import net.myjda.payvi.data.local.PayviDatabase
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.sync.SyncCoordinator
import net.myjda.payvi.ui.main.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Keeps PAYVI's order/SMS matching running close to real-time while the
 * user has "background monitoring" turned on, without waiting on
 * WorkManager's 15-minute floor. Runs its own loop calling
 * [SyncCoordinator.syncAndProcessAll] on the interval configured in
 * Settings (default 5 minutes). A visible notification is required by
 * Android for any foreground service - see notif_channel_sync_desc.
 */
class PayviSyncService : LifecycleService() {

    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (loopJob == null) {
            loopJob = lifecycleScope.launch {
                val prefs = PayviPrefs.getInstance(applicationContext)
                while (isActive) {
                    updateNotification()
                    try {
                        SyncCoordinator.syncAndProcessAll(applicationContext)
                    } catch (e: Exception) {
                        // A single failed pass (e.g. network blip) shouldn't
                        // kill the loop - it just tries again next interval.
                    }
                    val minutes = prefs.syncIntervalMinutes.coerceAtLeast(1)
                    delay(TimeUnit.MINUTES.toMillis(minutes.toLong()))
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildNotification(selectedSenderCount = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        lifecycleScope.launch {
            val count = try {
                PayviDatabase.getInstance(applicationContext).smsSenderDao().getSelected().size
            } catch (e: Exception) {
                0
            }
            val manager = ContextCompat.getSystemService(applicationContext, android.app.NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(count))
        }
    }

    private fun buildNotification(selectedSenderCount: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PayviApplication.SYNC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notif_sync_title))
            .setContentText(getString(R.string.notif_sync_text, selectedSenderCount))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, PayviSyncService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PayviSyncService::class.java))
        }
    }
}
