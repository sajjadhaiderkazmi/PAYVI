package net.myjda.payvi.data.model

import com.google.gson.annotations.SerializedName

/**
 * The JSON payload encoded in the pairing QR code shown on the
 * WooCommerce → PAYVI Connector admin page (see Payvi_Admin::get_pairing_payload()).
 */
data class PairingPayload(
    val site: String,
    val key: String,
    val secret: String,
    val v: Int = 1
) {
    fun isValid(): Boolean = site.isNotBlank() && key.isNotBlank() && secret.isNotBlank()
}
