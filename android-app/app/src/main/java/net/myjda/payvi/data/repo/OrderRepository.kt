package net.myjda.payvi.data.repo

import kotlinx.coroutines.flow.Flow
import net.myjda.payvi.data.api.ApiClientFactory
import net.myjda.payvi.data.local.OrderDao
import net.myjda.payvi.data.local.OrderEntity
import net.myjda.payvi.data.local.toEntity
import net.myjda.payvi.data.model.StatusUpdateRequest
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.matching.PayviStatus
import retrofit2.HttpException
import java.io.IOException

sealed class SyncResult {
    data class Success(val count: Int) : SyncResult()
    object NotPaired : SyncResult()
    object NetworkError : SyncResult()
    data class ApiError(val code: Int) : SyncResult()
}

class OrderRepository(
    private val prefs: PayviPrefs,
    private val dao: OrderDao
) {

    fun observeOrders(): Flow<List<OrderEntity>> = dao.observeAll()

    suspend fun getOrder(id: Long): OrderEntity? = dao.getById(id)

    /** Pulls any orders created/updated on the store since the last sync,
     * merging them into the local cache without disturbing any match
     * result this device has already computed for an order (see
     * OrderDto.toEntity). */
    suspend fun syncOrders(): SyncResult {
        val api = ApiClientFactory.createFromPrefs(prefs) ?: return SyncResult.NotPaired

        return try {
            val since = prefs.lastSyncSince
            val response = api.listOrders(since = since, perPage = 100)

            val entities = response.orders.map { dto ->
                dto.toEntity(existing = dao.getById(dto.id))
            }
            dao.upsert(entities)

            prefs.lastSyncSince = response.serverTime
            prefs.lastSyncAtMillis = System.currentTimeMillis()

            SyncResult.Success(entities.size)
        } catch (e: HttpException) {
            SyncResult.ApiError(e.code())
        } catch (e: IOException) {
            SyncResult.NetworkError
        }
    }

    /** Orders that have a screenshot but no local match result yet. */
    suspend fun getPendingOrders(): List<OrderEntity> = dao.getPendingScreenshotOrders()

    suspend fun saveLocalResult(
        orderId: Long,
        status: PayviStatus,
        matchedSmsBody: String?,
        matchedFieldsJson: String?,
        ocrName: String?,
        ocrAmount: String?,
        ocrDate: String?,
        ocrTxnId: String?,
        ocrNumber: String?
    ) {
        dao.updateLocalResult(
            id = orderId,
            status = status.wireValue,
            matchedSmsBody = matchedSmsBody,
            matchedFieldsJson = matchedFieldsJson,
            ocrName = ocrName,
            ocrAmount = ocrAmount,
            ocrDate = ocrDate,
            ocrTxnId = ocrTxnId,
            ocrNumber = ocrNumber,
            checkedAtMillis = System.currentTimeMillis(),
            synced = false
        )
        pushStatus(orderId)
    }

    suspend fun setManualStatus(orderId: Long, status: PayviStatus) {
        dao.setManualStatus(orderId, status.wireValue)
        pushStatus(orderId, force = true)
    }

    /** Pushes this order's current local status to the store, if it hasn't
     * been acknowledged yet (or [force]d). Safe to call even when offline -
     * it just leaves statusSyncedToServer = false for [pushAllUnsynced] to
     * retry later. */
    suspend fun pushStatus(orderId: Long, force: Boolean = false): Boolean {
        val api = ApiClientFactory.createFromPrefs(prefs) ?: return false
        val order = dao.getById(orderId) ?: return false

        if (!force && order.statusSyncedToServer) return true

        val status = order.localStatus ?: return false
        val fields = parseMatchedFields(order.matchedFieldsJson)

        return try {
            api.setOrderStatus(
                orderId,
                StatusUpdateRequest(
                    status = status,
                    matchedSms = order.matchedSmsBody,
                    matchedFields = fields
                )
            )
            dao.markSynced(orderId)
            true
        } catch (e: Exception) {
            false // stays unsynced; a later sync/backstop run retries it
        }
    }

    suspend fun pushAllUnsynced() {
        dao.getUnsyncedStatusOrders().forEach { pushStatus(it.id) }
    }

    private fun parseMatchedFields(json: String?): Map<String, String>? {
        if (json.isNullOrBlank()) return null
        return try {
            com.google.gson.Gson().fromJson(
                json,
                object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            )
        } catch (e: Exception) {
            null
        }
    }
}
