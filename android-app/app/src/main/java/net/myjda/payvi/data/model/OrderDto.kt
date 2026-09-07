package net.myjda.payvi.data.model

import com.google.gson.annotations.SerializedName

/**
 * Wire format for one order, exactly matching Payvi_Api::order_to_array()
 * in the WordPress plugin.
 */
data class OrderDto(
    val id: Long,
    val number: String,
    val status: String,
    @SerializedName("date_created") val dateCreated: String?,
    @SerializedName("date_modified") val dateModified: String?,
    val currency: String,
    val total: String,
    @SerializedName("billing_name") val billingName: String,
    @SerializedName("billing_phone") val billingPhone: String,
    @SerializedName("billing_email") val billingEmail: String,
    @SerializedName("vendor_name") val vendorName: String,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("has_screenshot") val hasScreenshot: Boolean,
    @SerializedName("screenshot_url") val screenshotUrl: String,
    @SerializedName("screenshot_id") val screenshotId: Long,
    @SerializedName("screenshot_proxy_url") val screenshotProxyUrl: String,
    @SerializedName("is_duplicate") val isDuplicate: Boolean,
    @SerializedName("duplicate_match_order_id") val duplicateMatchOrderId: Long,
    @SerializedName("payvi_status") val payviStatus: String,
    @SerializedName("payvi_checked_at") val payviCheckedAt: String?
)

data class VerifyResponse(
    val success: Boolean,
    @SerializedName("site_name") val siteName: String?,
    @SerializedName("site_url") val siteUrl: String?,
    val currency: String?,
    @SerializedName("plugin_version") val pluginVersion: String?,
    @SerializedName("server_time") val serverTime: Long
)

data class OrdersResponse(
    val success: Boolean,
    @SerializedName("server_time") val serverTime: Long,
    val orders: List<OrderDto>
)

data class OrderResponse(
    val success: Boolean,
    val order: OrderDto
)

data class StatusUpdateRequest(
    val status: String,
    @SerializedName("matched_sms") val matchedSms: String? = null,
    @SerializedName("matched_fields") val matchedFields: Map<String, String>? = null
)

data class StatusUpdateResponse(
    val success: Boolean,
    @SerializedName("order_id") val orderId: Long,
    val status: String
)
