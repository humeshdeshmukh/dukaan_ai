package com.dukaan.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.entity.CustomerEntity
import com.dukaan.core.db.entity.TransactionEntity

@Database(
    entities = [CustomerEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun khataDao(): KhataDao
}
