package com.dukaan.core.network.di

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
        // NOTE: Replace with actual API key or fetch fromBuildConfig/SafeArgs
        return GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = "AIzaSyCRTsm2bzegLxHTGP3Ul3wX6KtTMjPwxVQ"
        )
    }
}
