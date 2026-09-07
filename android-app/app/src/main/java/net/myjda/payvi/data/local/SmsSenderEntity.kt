package net.myjda.payvi.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One distinct SMS sender/address seen on this phone, and whether the
 * user has chosen to have PAYVI watch it for payment messages. */
@Entity(tableName = "sms_senders")
data class SmsSenderEntity(
    @PrimaryKey val address: String,
    val messageCount: Int = 0,
    val lastMessageMillis: Long = 0,
    val selected: Boolean = false
)

@Dao
interface SmsSenderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(senders: List<SmsSenderEntity>)

    @Query("SELECT * FROM sms_senders ORDER BY lastMessageMillis DESC")
    fun observeAll(): Flow<List<SmsSenderEntity>>

    @Query("SELECT * FROM sms_senders WHERE selected = 1")
    suspend fun getSelected(): List<SmsSenderEntity>

    @Query("UPDATE sms_senders SET selected = :selected WHERE address = :address")
    suspend fun setSelected(address: String, selected: Boolean)

    @Query(
        "UPDATE sms_senders SET messageCount = :messageCount, lastMessageMillis = :lastMessageMillis " +
            "WHERE address = :address"
    )
    suspend fun updateStats(address: String, messageCount: Int, lastMessageMillis: Long)
}
