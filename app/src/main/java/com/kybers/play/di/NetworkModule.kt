package com.kybers.play.di

import com.kybers.play.data.remote.ExternalApiRetrofitClient
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.remote.XtreamApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbApiService

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OmdbApiService

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @TmdbApiService
    fun provideTmdbApiService(): ExternalApiService {
        return ExternalApiRetrofitClient.createTMDbService()
    }

    @Provides
    @Singleton
    @OmdbApiService
    fun provideOmdbApiService(): ExternalApiService {
        return ExternalApiRetrofitClient.createOMDbService()
    }

    // Note: XtreamApiService is created dynamically with different base URLs
    // This will be handled in the repository layer or through a factory pattern
}