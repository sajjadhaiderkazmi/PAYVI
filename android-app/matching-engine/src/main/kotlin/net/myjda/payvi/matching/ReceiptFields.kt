package net.myjda.payvi.matching

/**
 * Fields extracted (best-effort) from either a payment screenshot's OCR
 * text or an incoming SMS body. The same parser produces this shape for
 * both sources, so screenshot fields and SMS fields can be compared
 * directly.
 *
 * Any field can be null when it simply wasn't found in the text - that is
 * expected and normal (receipts and bank SMS formats vary a lot), and is
 * exactly what makes the "Not Sure" / "Not Received" outcomes meaningful
 * rather than the parser guessing.
 */
data class ReceiptFields(
    val name: String?,
    val amountText: String?,
    val amountValue: Double?,
    val dateText: String?,
    val dateIso: String?,
    val txnId: String?,
    val number: String?,
    val rawText: String
) {
    companion object {
        val EMPTY = ReceiptFields(
            name = null,
            amountText = null,
            amountValue = null,
            dateText = null,
            dateIso = null,
            txnId = null,
            number = null,
            rawText = ""
        )
    }
}
