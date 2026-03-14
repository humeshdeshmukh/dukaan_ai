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

    @Provides
    @Singleton
    fun provideKhataDatabase(
        @ApplicationContext context: Context
    ): KhataDatabase {
        return Room.databaseBuilder(
            context,
            KhataDatabase::class.java,
            "dukaan_khata.db"
        ).addMigrations(MIGRATION_2_3)
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
}
