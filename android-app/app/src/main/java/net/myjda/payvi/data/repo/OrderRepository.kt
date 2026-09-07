package net.myjda.payvi.data.repo

import kotlinx.coroutines.flow.Flow
import net.myjda.payvi.data.api.ApiClientFactory
import net.myjda.payvi.data.local.OrderDao
import net.myjda.payvi.data.local.OrderEntity
import net.myjda.payvi.data.local.toEntity
import net.myjda.payvi.data.model.StatusUpdateRequest
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.matching.PayviStatus
import net.myjda.payvi.util.IsoDate
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

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_ORDERS_PER_SYNC = 5000
    }

    fun observeOrders(): Flow<List<OrderEntity>> = dao.observeAll()

    suspend fun getOrder(id: Long): OrderEntity? = dao.getById(id)

    /** Pulls any orders created/updated on the store since the last sync,
     * merging them into the local cache without disturbing any match
     * result this device has already computed for an order (see
     * OrderDto.toEntity).
     *
     * Pages forward through *every* order the cursor hasn't seen yet,
     * rather than a single page - a store with more orders than fit in
     * one page (or one that hasn't synced in a while) would otherwise
     * only ever see the oldest batch, since the cursor used to jump
     * straight to "now" after a single fetch and permanently skip
     * everything in between (that was the bug: it advanced by
     * response.server_time instead of by the newest order actually
     * fetched). */
    suspend fun syncOrders(): SyncResult {
        val api = ApiClientFactory.createFromPrefs(prefs) ?: return SyncResult.NotPaired

        var cursor = prefs.lastSyncSince
        var totalFetched = 0

        return try {
            while (true) {
                val response = api.listOrders(since = cursor, perPage = PAGE_SIZE)
                if (response.orders.isEmpty()) break

                val entities = response.orders.map { dto ->
                    dto.toEntity(existing = dao.getById(dto.id))
                }
                dao.upsert(entities)
                totalFetched += entities.size

                val newestInBatch = response.orders
                    .mapNotNull { IsoDate.toEpochSeconds(it.dateModified) }
                    .maxOrNull()

                // Advance only if we found a newer timestamp than we
                // already have - never jump ahead to "now". If dates
                // fail to parse, stop rather than risk looping forever.
                if (newestInBatch == null || newestInBatch <= cursor) break
                cursor = newestInBatch
                prefs.lastSyncSince = cursor

                if (response.orders.size < PAGE_SIZE) break // caught up to "now"
                if (totalFetched >= MAX_ORDERS_PER_SYNC) break // safety cap
            }

            prefs.lastSyncAtMillis = System.currentTimeMillis()
            SyncResult.Success(totalFetched)
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
