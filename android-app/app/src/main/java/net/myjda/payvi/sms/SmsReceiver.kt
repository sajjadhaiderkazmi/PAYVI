package net.myjda.payvi.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import net.myjda.payvi.work.PayviSyncWorker

/**
 * Fires whenever a new SMS arrives. Deliberately does no DB/network work
 * here directly - a BroadcastReceiver only gets a few seconds before the
 * system may kill it, and ML Kit OCR / a network call can take longer than
 * that. Instead it just enqueues a WorkManager job (which the OS will run
 * promptly, and retry if the app process is killed in the meantime) and
 * returns immediately. The worker itself re-checks which senders are
 * selected, so this receiver doesn't need SMS-provider or Room access.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // We don't need the message content here - just the fact that
        // *something* arrived is enough to trigger a re-check. The worker
        // re-reads the SMS provider itself (with a small time buffer) so
        // it never misses a message that arrived a moment before this
        // broadcast is dispatched.
        PayviSyncWorker.triggerNow(context)
    }
}
