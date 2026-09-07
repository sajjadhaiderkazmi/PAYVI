package net.myjda.payvi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(order: OrderEntity)

    @Query("SELECT * FROM orders ORDER BY dateCreated DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OrderEntity?

    /** Orders that have a screenshot but haven't produced a local match
     * result yet - the queue the sync pipeline works through. */
    @Query(
        "SELECT * FROM orders WHERE hasScreenshot = 1 AND (localStatus IS NULL OR localStatus = 'pending') " +
            "ORDER BY dateCreated ASC"
    )
    suspend fun getPendingScreenshotOrders(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE statusSyncedToServer = 0")
    suspend fun getUnsyncedStatusOrders(): List<OrderEntity>

    @Query(
        "UPDATE orders SET localStatus = :status, matchedSmsBody = :matchedSmsBody, " +
            "matchedFieldsJson = :matchedFieldsJson, ocrName = :ocrName, ocrAmount = :ocrAmount, " +
            "ocrDate = :ocrDate, ocrTxnId = :ocrTxnId, ocrNumber = :ocrNumber, " +
            "lastCheckedAtMillis = :checkedAtMillis, statusSyncedToServer = :synced WHERE id = :id"
    )
    suspend fun updateLocalResult(
        id: Long,
        status: String,
        matchedSmsBody: String?,
        matchedFieldsJson: String?,
        ocrName: String?,
        ocrAmount: String?,
        ocrDate: String?,
        ocrTxnId: String?,
        ocrNumber: String?,
        checkedAtMillis: Long,
        synced: Boolean
    )

    @Query("UPDATE orders SET statusSyncedToServer = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("UPDATE orders SET payviStatus = :status, localStatus = :status WHERE id = :id")
    suspend fun setManualStatus(id: Long, status: String)
}
