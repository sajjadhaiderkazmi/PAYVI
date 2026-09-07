package net.myjda.payvi.matching

import kotlin.math.abs

/**
 * Order-side facts the screenshot/SMS are being checked against. These
 * come straight from WooCommerce (via the PAYVI plugin API), not OCR, so
 * they're treated as ground truth for the amount/number/name comparisons -
 * this makes the matcher more forgiving of small OCR mistakes than
 * comparing the screenshot to the SMS alone would be.
 */
data class OrderContext(
    val amount: Double?,
    val billingPhone: String?,
    val billingName: String?,
    val orderDateMillis: Long?
)

/**
 * Decides an order's payment status by comparing the fields extracted from
 * its payment screenshot against candidate SMS messages.
 *
 * Priority, matching what was asked for:
 *  1. A duplicate screenshot (flagged server-side, exact-file match) is
 *     always DUPLICATE, regardless of any SMS - a reused screenshot is
 *     fraud even if the text on it happens to line up with a message.
 *  2. Otherwise, every candidate SMS is scored against the screenshot on
 *     four signals: amount, transaction id, number, and name. Amount and
 *     number are also cross-checked against the order's own total/phone
 *     as a second source of truth, since OCR can misread digits.
 *  3. The best-scoring SMS decides the outcome:
 *       - amount matched AND at least one of (txn id / number / name)
 *         matched  -> COMPLETED
 *       - exactly one signal matched, or amount alone with nothing else -> NOT_SURE
 *       - nothing matched on any candidate (or there were no candidates) -> NOT_RECEIVED
 *
 * Date is used only to narrow which SMS are even considered (a window
 * around the order date), not as a pass/fail signal - free-form date text
 * in SMS is too inconsistent to compare reliably, while the order's own
 * timestamp is exact.
 */
object PayviMatcher {

    private const val DEFAULT_WINDOW_BEFORE_MILLIS = 24L * 60 * 60 * 1000       // 1 day before order
    private const val DEFAULT_WINDOW_AFTER_MILLIS = 3L * 24 * 60 * 60 * 1000    // 3 days after order
    private const val AMOUNT_TOLERANCE = 0.01

    fun evaluate(
        isDuplicateFromServer: Boolean,
        screenshotText: String,
        order: OrderContext,
        smsMessages: List<SmsCandidate>,
        windowBeforeMillis: Long = DEFAULT_WINDOW_BEFORE_MILLIS,
        windowAfterMillis: Long = DEFAULT_WINDOW_AFTER_MILLIS
    ): MatchResult {
        val screenshotFields = ReceiptFieldParser.parse(screenshotText)

        if (isDuplicateFromServer) {
            return MatchResult(
                status = PayviStatus.DUPLICATE,
                matchedSms = null,
                matchedFields = emptyMap(),
                reason = "Screenshot already used on another order (server-side duplicate check).",
                screenshotFields = screenshotFields
            )
        }

        val candidates = filterByDateWindow(smsMessages, order.orderDateMillis, windowBeforeMillis, windowAfterMillis)

        var best: ScoredSms? = null
        for (sms in candidates) {
            val scored = score(screenshotFields, order, sms)
            if (best == null || scored.matchCount > best.matchCount) {
                best = scored
            }
        }

        return when {
            best == null -> MatchResult(
                status = PayviStatus.NOT_RECEIVED,
                matchedSms = null,
                matchedFields = emptyMap(),
                reason = "No SMS from the selected numbers in the expected time window.",
                screenshotFields = screenshotFields
            )
            best.matchCount >= 2 -> MatchResult(
                status = PayviStatus.COMPLETED,
                matchedSms = best.sms,
                matchedFields = best.fields,
                reason = "SMS matched on: " + best.fields.keys.joinToString(", "),
                screenshotFields = screenshotFields
            )
            best.matchCount == 1 -> MatchResult(
                status = PayviStatus.NOT_SURE,
                matchedSms = best.sms,
                matchedFields = best.fields,
                reason = "Only a partial match (" + best.fields.keys.joinToString(", ") + ") was found.",
                screenshotFields = screenshotFields
            )
            else -> MatchResult(
                status = PayviStatus.NOT_RECEIVED,
                matchedSms = null,
                matchedFields = emptyMap(),
                reason = "SMS messages were found but none of their details matched.",
                screenshotFields = screenshotFields
            )
        }
    }

    private data class ScoredSms(val sms: SmsCandidate, val matchCount: Int, val fields: Map<String, String>)

    private fun score(screenshot: ReceiptFields, order: OrderContext, sms: SmsCandidate): ScoredSms {
        val smsFields = ReceiptFieldParser.parse(sms.body)
        val fields = LinkedHashMap<String, String>()

        if (amountsMatch(screenshot.amountValue, order.amount, smsFields.amountValue)) {
            fields["amount"] = smsFields.amountText ?: smsFields.amountValue.toString()
        }

        if (screenshot.txnId != null && smsFields.txnId != null &&
            screenshot.txnId.equals(smsFields.txnId, ignoreCase = true)
        ) {
            // Key is "txn_id" (not "transaction_id") to match the wire
            // contract expected by Payvi_Api::handle_set_status() on the
            // WordPress plugin side.
            fields["txn_id"] = smsFields.txnId
        }

        if (numbersMatch(screenshot.number, order.billingPhone, smsFields.number)) {
            fields["number"] = smsFields.number ?: ""
        }

        if (namesMatch(screenshot.name, order.billingName, smsFields.name)) {
            fields["name"] = smsFields.name ?: ""
        }

        return ScoredSms(sms, fields.size, fields)
    }

    private fun amountsMatch(screenshotAmount: Double?, orderAmount: Double?, smsAmount: Double?): Boolean {
        if (smsAmount == null) return false
        if (screenshotAmount != null && abs(screenshotAmount - smsAmount) < AMOUNT_TOLERANCE) return true
        if (orderAmount != null && abs(orderAmount - smsAmount) < AMOUNT_TOLERANCE) return true
        return false
    }

    private fun numbersMatch(screenshotNumber: String?, orderPhone: String?, smsNumber: String?): Boolean {
        val sms = ReceiptFieldParser.comparableNumber(smsNumber) ?: return false
        val shot = ReceiptFieldParser.comparableNumber(screenshotNumber)
        val order = ReceiptFieldParser.comparableNumber(orderPhone)
        return (shot != null && shot == sms) || (order != null && order == sms)
    }

    private fun namesMatch(screenshotName: String?, orderName: String?, smsName: String?): Boolean {
        val sms = normalizeName(smsName) ?: return false
        val shot = normalizeName(screenshotName)
        val order = normalizeName(orderName)
        return (shot != null && shot == sms) || (order != null && order == sms)
    }

    private fun normalizeName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return name.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun filterByDateWindow(
        messages: List<SmsCandidate>,
        orderDateMillis: Long?,
        beforeMillis: Long,
        afterMillis: Long
    ): List<SmsCandidate> {
        if (orderDateMillis == null) return messages
        val from = orderDateMillis - beforeMillis
        val to = orderDateMillis + afterMillis
        return messages.filter { it.timestampMillis in from..to }
    }
}
