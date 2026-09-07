package net.myjda.payvi.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.myjda.payvi.sync.SyncCoordinator
import java.util.concurrent.TimeUnit

/**
 * Battery-friendly backstop for the foreground service: WorkManager can't
 * run more often than every 15 minutes (a platform limit, not something
 * this app controls), but it keeps working even if the foreground service
 * or the whole app process gets killed. It's also what actually does the
 * work triggered by an incoming SMS (see SmsReceiver) and by "background
 * monitoring" being turned on in Settings.
 */
class PayviSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            SyncCoordinator.syncAndProcessAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "payvi_periodic_sync"
        private const val ONE_TIME_WORK_NAME = "payvi_immediate_sync"
        private const val MIN_PERIODIC_MINUTES = 15L // platform-enforced minimum

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context, intervalMinutes: Int) {
            val interval = intervalMinutes.toLong().coerceAtLeast(MIN_PERIODIC_MINUTES)
            val request = PeriodicWorkRequestBuilder<PayviSyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        /** Runs one sync pass as soon as WorkManager can schedule it -
         * used right after a new SMS arrives. */
        fun triggerNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PayviSyncWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
