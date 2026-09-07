package net.myjda.payvi.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Marks an SMS (by its content-provider row id) as already having been
 * considered by the matching pipeline, so re-scans don't redo work and a
 * message can't accidentally get matched to two different orders. */
@Entity(tableName = "processed_sms")
data class ProcessedSmsEntity(
    @PrimaryKey val smsId: String,
    val processedAtMillis: Long,
    val matchedOrderId: Long? = null
)

@Dao
interface ProcessedSmsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProcessedSmsEntity)

    @Query("SELECT smsId FROM processed_sms WHERE matchedOrderId IS NOT NULL")
    suspend fun getMatchedSmsIds(): List<String>

    @Query("SELECT COUNT(*) FROM processed_sms WHERE smsId = :smsId")
    suspend fun isProcessed(smsId: String): Int
}
