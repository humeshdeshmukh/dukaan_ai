package com.dukaan.ai.ads

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdModule {

    @Provides
    @Singleton
    fun provideAdFrequencyManager(): AdFrequencyManager {
        return AdFrequencyManager()
    }

    @Provides
    @Singleton
    fun provideMicroAdController(
        frequencyManager: AdFrequencyManager
    ): MicroAdController {
        return MicroAdController(frequencyManager)
    }
}
