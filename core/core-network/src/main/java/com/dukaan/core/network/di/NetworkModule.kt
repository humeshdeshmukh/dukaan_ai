package com.dukaan.core.network.di

import com.dukaan.core.network.BuildConfig
import com.dukaan.core.network.ai.GeminiKhataService
import com.dukaan.core.network.ai.GeminiKhataServiceImpl
import com.dukaan.core.network.ai.GeminiTranslationService
import com.dukaan.core.network.ai.GeminiTranslationServiceImpl
import com.google.ai.client.generativeai.GenerativeModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiLite

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiFlash

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @GeminiLite
    fun provideGenerativeModel(): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    @Provides
    @Singleton
    @GeminiFlash
    fun provideFlashGenerativeModel(): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    @Provides
    @Singleton
    fun provideGeminiKhataService(@GeminiLite generativeModel: GenerativeModel): GeminiKhataService {
        return GeminiKhataServiceImpl(generativeModel)
    }

    @Provides
    @Singleton
    fun provideGeminiTranslationService(@GeminiLite generativeModel: GenerativeModel): GeminiTranslationService {
        return GeminiTranslationServiceImpl(generativeModel)
    }
}
