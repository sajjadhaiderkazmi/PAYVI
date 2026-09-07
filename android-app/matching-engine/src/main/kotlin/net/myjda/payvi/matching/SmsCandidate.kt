package net.myjda.payvi.matching

/**
 * One SMS message being considered as a possible payment receipt for an
 * order. [id] is the local SMS provider row id (used to avoid re-scanning
 * the same message twice), [sender] is the address/number the SMS came
 * from, and [timestampMillis] is when it was received.
 */
data class SmsCandidate(
    val id: String,
    val sender: String,
    val body: String,
    val timestampMillis: Long
)
