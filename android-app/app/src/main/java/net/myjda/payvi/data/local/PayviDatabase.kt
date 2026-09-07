package net.myjda.payvi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OrderEntity::class, SmsSenderEntity::class, ProcessedSmsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PayviDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun smsSenderDao(): SmsSenderDao
    abstract fun processedSmsDao(): ProcessedSmsDao

    companion object {
        @Volatile
        private var instance: PayviDatabase? = null

        fun getInstance(context: Context): PayviDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PayviDatabase::class.java,
                    "payvi.db"
                ).build().also { instance = it }
            }
        }
    }
}
