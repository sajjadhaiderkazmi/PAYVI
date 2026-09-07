package net.myjda.payvi.util

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Parses the ISO-8601 (DATE_ATOM) timestamps the PHP plugin sends
 * (e.g. "2026-09-07T10:00:00+05:00") - shared so every place that reads
 * order.dateCreated/dateModified parses it the same way. */
object IsoDate {

    fun toEpochMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun toEpochSeconds(iso: String?): Long? = toEpochMillis(iso)?.let { it / 1000 }
}
