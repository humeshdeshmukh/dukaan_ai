package com.dukaan.core.network.di

import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.core.network.ai.GeminiBillingServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GeminiServiceModule {

    @Binds
    @Singleton
    abstract fun bindGeminiBillingService(
        impl: GeminiBillingServiceImpl
    ): GeminiBillingService
}
