package net.myjda.payvi.matching

data class MatchResult(
    val status: PayviStatus,
    val matchedSms: SmsCandidate?,
    val matchedFields: Map<String, String>,
    val reason: String,
    val screenshotFields: ReceiptFields
)
