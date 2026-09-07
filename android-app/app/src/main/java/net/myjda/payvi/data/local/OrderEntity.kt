package net.myjda.payvi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.myjda.payvi.data.model.OrderDto

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Long,
    val number: String,
    val status: String,
    val dateCreated: String?,
    val dateModified: String?,
    val currency: String,
    val total: String,
    val billingName: String,
    val billingPhone: String,
    val billingEmail: String,
    val vendorName: String,
    val paymentMethod: String,
    val hasScreenshot: Boolean,
    val screenshotUrl: String,
    val screenshotProxyUrl: String,
    val isDuplicateOnServer: Boolean,
    val duplicateMatchOrderId: Long,
    val payviStatus: String,
    // Local-only fields, filled in once this device has actually run the
    // OCR + SMS match pipeline for this order (payviStatus above is what
    // the *server* last acknowledged, which may lag behind while offline).
    val localStatus: String? = null,
    val matchedSmsBody: String? = null,
    val matchedFieldsJson: String? = null,
    val ocrName: String? = null,
    val ocrAmount: String? = null,
    val ocrDate: String? = null,
    val ocrTxnId: String? = null,
    val ocrNumber: String? = null,
    val lastCheckedAtMillis: Long? = null,
    val statusSyncedToServer: Boolean = true
)

fun OrderDto.toEntity(existing: OrderEntity? = null): OrderEntity = OrderEntity(
    id = id,
    number = number,
    status = status,
    dateCreated = dateCreated,
    dateModified = dateModified,
    currency = currency,
    total = total,
    billingName = billingName,
    billingPhone = billingPhone,
    billingEmail = billingEmail,
    vendorName = vendorName,
    paymentMethod = paymentMethod,
    hasScreenshot = hasScreenshot,
    screenshotUrl = screenshotUrl,
    screenshotProxyUrl = screenshotProxyUrl,
    isDuplicateOnServer = isDuplicate,
    duplicateMatchOrderId = duplicateMatchOrderId,
    payviStatus = payviStatus,
    // Preserve whatever this device already figured out locally - a fresh
    // order list refresh from the server should not erase a match result
    // this phone already computed and is trying to sync up.
    localStatus = existing?.localStatus,
    matchedSmsBody = existing?.matchedSmsBody,
    matchedFieldsJson = existing?.matchedFieldsJson,
    ocrName = existing?.ocrName,
    ocrAmount = existing?.ocrAmount,
    ocrDate = existing?.ocrDate,
    ocrTxnId = existing?.ocrTxnId,
    ocrNumber = existing?.ocrNumber,
    lastCheckedAtMillis = existing?.lastCheckedAtMillis,
    statusSyncedToServer = existing?.statusSyncedToServer ?: true
)
