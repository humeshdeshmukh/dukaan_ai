package com.dukaan.core.db.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dukaan.core.db.KhataDatabase
import com.dukaan.core.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bills ADD COLUMN sellerName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE bills ADD COLUMN billNumber TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE bills ADD COLUMN imagePath TEXT")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS translation_cache (
                    languageCode TEXT NOT NULL PRIMARY KEY,
                    translationsJson TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bills ADD COLUMN customerName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE bills ADD COLUMN customerId INTEGER")
            db.execSQL("ALTER TABLE bills ADD COLUMN discountPercent REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE bills ADD COLUMN discountAmount REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE bills ADD COLUMN taxPercent REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE bills ADD COLUMN taxAmount REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE bills ADD COLUMN subtotal REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE bills ADD COLUMN paymentMode TEXT NOT NULL DEFAULT 'CASH'")
            db.execSQL("ALTER TABLE bills ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE bills ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bills ADD COLUMN customerPhone TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE customers ADD COLUMN khataType TEXT NOT NULL DEFAULT 'SMALL'")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE orders ADD COLUMN notes TEXT")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE orders ADD COLUMN supplierPhone TEXT")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE order_items ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        }
    }

    @Provides
    @Singleton
    fun provideKhataDatabase(
        @ApplicationContext context: Context
    ): KhataDatabase {
        return Room.databaseBuilder(
            context,
            KhataDatabase::class.java,
            "dukaan_khata.db"
        ).addMigrations(MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
         .fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideKhataDao(database: KhataDatabase): KhataDao {
        return database.khataDao()
    }

    @Provides
    fun provideBillDao(database: KhataDatabase): BillDao {
        return database.billDao()
    }

    @Provides
    fun provideOrderDao(database: KhataDatabase): OrderDao {
        return database.orderDao()
    }

    @Provides
    fun provideShopProfileDao(database: KhataDatabase): ShopProfileDao {
        return database.shopProfileDao()
    }

    @Provides
    fun provideProductDao(database: KhataDatabase): ProductDao {
        return database.productDao()
    }

    @Provides
    fun provideTranslationDao(database: KhataDatabase): TranslationDao {
        return database.translationDao()
    }
}
