package com.dukaan.feature.khata.di

import com.dukaan.feature.khata.data.repository.KhataRepositoryImpl
import com.dukaan.feature.khata.domain.repository.KhataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KhataModule {

    @Binds
    @Singleton
    abstract fun bindKhataRepository(
        khataRepositoryImpl: KhataRepositoryImpl
    ): KhataRepository
}
