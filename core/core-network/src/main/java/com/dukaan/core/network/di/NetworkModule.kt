package com.dukaan.core.network.di

import com.dukaan.core.network.BuildConfig
import com.dukaan.core.network.ai.GeminiKhataService
import com.dukaan.core.network.ai.GeminiKhataServiceImpl
import com.google.ai.client.generativeai.GenerativeModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    @Provides
    @Singleton
    fun provideGeminiKhataService(generativeModel: GenerativeModel): GeminiKhataService {
        return GeminiKhataServiceImpl(generativeModel)
    }
}
