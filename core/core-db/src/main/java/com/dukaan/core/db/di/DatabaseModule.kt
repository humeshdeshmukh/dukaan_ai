package com.dukaan.core.db.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideKhataDatabase(
        @ApplicationContext context: Context
    ): KhataDatabase {
        return Room.databaseBuilder(
            context,
            KhataDatabase::class.java,
            "dukaan_khata.db"
        ).fallbackToDestructiveMigration().build()
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
