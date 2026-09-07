package net.myjda.payvi.sms

import android.content.Context
import android.provider.Telephony
import net.myjda.payvi.matching.SmsCandidate

/**
 * Reads the device's SMS content provider. Every query here requires
 * READ_SMS to already be granted - callers are expected to have checked
 * that first (see SmsSenderSelectionActivity).
 */
object SmsReader {

    data class SenderSummary(val address: String, val messageCount: Int, val lastMessageMillis: Long)

    /** Scans the most recent [limit] messages across the whole inbox to
     * build a list of distinct senders/addresses, most recently active
     * first. This is done client-side (rather than a provider-level
     * DISTINCT query) since not every OEM's SMS provider implementation
     * supports arbitrary SQL projections reliably. */
    fun querySenders(context: Context, limit: Int = 3000): List<SenderSummary> {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT $limit"
        ) ?: return emptyList()

        val bySender = LinkedHashMap<String, SenderSummary>()
        cursor.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            if (addressIdx < 0 || dateIdx < 0) return@use

            while (it.moveToNext()) {
                val address = it.getString(addressIdx)?.takeIf { a -> a.isNotBlank() } ?: continue
                val date = it.getLong(dateIdx)
                val existing = bySender[address]
                bySender[address] = if (existing == null) {
                    SenderSummary(address, 1, date)
                } else {
                    existing.copy(
                        messageCount = existing.messageCount + 1,
                        lastMessageMillis = maxOf(existing.lastMessageMillis, date)
                    )
                }
            }
        }

        return bySender.values.sortedByDescending { it.lastMessageMillis }
    }

    /** Messages from any of [addresses], received at or after [sinceMillis],
     * newest first. Used both for the initial historical lookback and for
     * re-checking pending orders. */
    fun queryMessagesFrom(
        context: Context,
        addresses: Set<String>,
        sinceMillis: Long
    ): List<SmsCandidate> {
        if (addresses.isEmpty()) return emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val placeholders = addresses.joinToString(",") { "?" }
        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.ADDRESS} IN ($placeholders)"
        val args = (listOf(sinceMillis.toString()) + addresses).toTypedArray()

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            args,
            "${Telephony.Sms.DATE} DESC"
        ) ?: return emptyList()

        val results = mutableListOf<SmsCandidate>()
        cursor.use {
            val idIdx = it.getColumnIndex(Telephony.Sms._ID)
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            if (idIdx < 0 || addressIdx < 0 || bodyIdx < 0 || dateIdx < 0) return@use

            while (it.moveToNext()) {
                val id = it.getString(idIdx) ?: continue
                results.add(
                    SmsCandidate(
                        id = id,
                        sender = it.getString(addressIdx).orEmpty(),
                        body = it.getString(bodyIdx).orEmpty(),
                        timestampMillis = it.getLong(dateIdx)
                    )
                )
            }
        }
        return results
    }
}
