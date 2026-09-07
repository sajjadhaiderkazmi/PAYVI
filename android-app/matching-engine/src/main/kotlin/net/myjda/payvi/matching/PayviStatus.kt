package net.myjda.payvi.matching

/**
 * The four outcomes shown in the PAYVI app (plus PENDING before a check has
 * run). Names here match the wire values sent to the WordPress plugin's
 * POST /orders/{id}/status endpoint - see Payvi_Api::STATUSES.
 */
enum class PayviStatus(val wireValue: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    NOT_SURE("not_sure"),
    NOT_RECEIVED("not_received"),
    DUPLICATE("duplicate");

    companion object {
        fun fromWireValue(value: String?): PayviStatus =
            values().firstOrNull { it.wireValue == value } ?: PENDING
    }
}
