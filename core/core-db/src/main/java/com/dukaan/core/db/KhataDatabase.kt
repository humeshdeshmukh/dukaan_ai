package com.dukaan.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dukaan.core.db.dao.*
import com.dukaan.core.db.entity.*

@Database(
    entities = [
        CustomerEntity::class,
        TransactionEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        ShopProfileEntity::class,
        ProductEntity::class,
        TranslationCacheEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun khataDao(): KhataDao
    abstract fun billDao(): BillDao
    abstract fun orderDao(): OrderDao
    abstract fun shopProfileDao(): ShopProfileDao
    abstract fun productDao(): ProductDao
    abstract fun translationDao(): TranslationDao
}
