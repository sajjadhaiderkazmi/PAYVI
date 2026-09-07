package net.myjda.payvi.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.service.PayviSyncService
import net.myjda.payvi.work.PayviSyncWorker

/** Resumes background monitoring after the phone restarts, if the user had
 * it enabled before the reboot (WorkManager schedules survive a reboot on
 * their own, but the foreground service does not, and needs to be
 * explicitly restarted here). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PayviPrefs.getInstance(context)
        if (prefs.isPaired && prefs.backgroundSyncEnabled) {
            PayviSyncService.start(context)
            PayviSyncWorker.schedule(context, prefs.syncIntervalMinutes)
        }
    }
}
