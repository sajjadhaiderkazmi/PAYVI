package net.myjda.payvi.sync

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.myjda.payvi.PayviApplication
import net.myjda.payvi.R
import net.myjda.payvi.data.local.OrderEntity
import net.myjda.payvi.data.local.PayviDatabase
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.data.repo.OrderRepository
import net.myjda.payvi.matching.OrderContext
import net.myjda.payvi.matching.PayviMatcher
import net.myjda.payvi.matching.PayviStatus
import net.myjda.payvi.ocr.ScreenshotOcrProcessor
import net.myjda.payvi.sms.SmsReader
import net.myjda.payvi.util.IsoDate
import java.util.concurrent.TimeUnit

/**
 * Ties everything together: pulls orders from the store, runs OCR on each
 * pending payment screenshot, compares it against SMS from the selected
 * senders using the matching-engine module, and reports the result back
 * to the store. This is the single place that orchestrates that pipeline -
 * both the foreground service and the WorkManager backstop call into it,
 * so there's exactly one code path to get right.
 */
object SyncCoordinator {

    private const val COARSE_SMS_LOOKBACK_PADDING_DAYS = 4L

    suspend fun syncAndProcessAll(context: Context): Int = withContext(Dispatchers.IO) {
        val prefs = PayviPrefs.getInstance(context)
        if (!prefs.isPaired) return@withContext 0

        val db = PayviDatabase.getInstance(context)
        val repository = OrderRepository(prefs, db.orderDao())

        repository.syncOrders()
        repository.pushAllUnsynced()

        val pending = repository.getPendingOrders()
        var processed = 0
        for (order in pending) {
            if (processOrderInternal(context, order, prefs, repository)) {
                processed++
            }
        }
        processed
    }

    suspend fun processOrder(context: Context, orderId: Long) = withContext(Dispatchers.IO) {
        val prefs = PayviPrefs.getInstance(context)
        val db = PayviDatabase.getInstance(context)
        val repository = OrderRepository(prefs, db.orderDao())
        val order = repository.getOrder(orderId) ?: return@withContext
        processOrderInternal(context, order, prefs, repository)
    }

    private suspend fun processOrderInternal(
        context: Context,
        order: OrderEntity,
        prefs: PayviPrefs,
        repository: OrderRepository
    ): Boolean {
        if (!order.hasScreenshot) {
            return false // Not a manual-payment order - nothing for PAYVI to check.
        }

        val orderDateMillis = parseIsoToMillis(order.dateCreated)

        if (order.isDuplicateOnServer) {
            val result = PayviMatcher.evaluate(
                isDuplicateFromServer = true,
                screenshotText = "",
                order = OrderContext(null, null, null, orderDateMillis),
                smsMessages = emptyList()
            )
            persistResult(context, repository, order, result)
            return true
        }

        val ocr = ScreenshotOcrProcessor(prefs)
        val screenshotText = ocr.extractText(order.screenshotUrl, order.screenshotProxyUrl) ?: ""

        val db = PayviDatabase.getInstance(context)
        val selectedSenders = db.smsSenderDao().getSelected().map { it.address }.toSet()

        val lookbackMillis = TimeUnit.DAYS.toMillis(
            (prefs.smsLookbackDays + COARSE_SMS_LOOKBACK_PADDING_DAYS)
        )
        val sinceMillis = (orderDateMillis ?: System.currentTimeMillis()) - lookbackMillis

        val candidates = if (selectedSenders.isEmpty()) {
            emptyList()
        } else {
            SmsReader.queryMessagesFrom(context, selectedSenders, sinceMillis)
        }

        val orderTotal = order.total.toDoubleOrNull()
        val result = PayviMatcher.evaluate(
            isDuplicateFromServer = false,
            screenshotText = screenshotText,
            order = OrderContext(
                amount = orderTotal,
                billingPhone = order.billingPhone,
                billingName = order.billingName,
                orderDateMillis = orderDateMillis
            ),
            smsMessages = candidates
        )

        persistResult(context, repository, order, result)
        return true
    }

    private suspend fun persistResult(
        context: Context,
        repository: OrderRepository,
        order: OrderEntity,
        result: net.myjda.payvi.matching.MatchResult
    ) {
        val fieldsJson = if (result.matchedFields.isEmpty()) {
            null
        } else {
            com.google.gson.Gson().toJson(result.matchedFields)
        }

        repository.saveLocalResult(
            orderId = order.id,
            status = result.status,
            matchedSmsBody = result.matchedSms?.body,
            matchedFieldsJson = fieldsJson,
            ocrName = result.screenshotFields.name,
            ocrAmount = result.screenshotFields.amountText,
            ocrDate = result.screenshotFields.dateText,
            ocrTxnId = result.screenshotFields.txnId,
            ocrNumber = result.screenshotFields.number
        )

        if (result.status == PayviStatus.DUPLICATE) {
            notifyDuplicate(context, order)
        }
    }

    private fun notifyDuplicate(context: Context, order: OrderEntity) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, PayviApplication.ALERT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.notif_duplicate_title))
            .setContentText(context.getString(R.string.notif_duplicate_text, order.number))
            .setAutoCancel(true)
            .build()
        manager.notify(("dup_" + order.id).hashCode(), notification)
    }

    private fun parseIsoToMillis(iso: String?): Long? = IsoDate.toEpochMillis(iso)
}
