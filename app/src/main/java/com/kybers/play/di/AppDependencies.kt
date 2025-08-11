package com.kybers.play.di

import android.app.Application
import android.content.Context
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.ui.components.ParentalControlManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint to access Hilt dependencies from non-Hilt components like Composables
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppDependencies {
    
    @ApplicationContext fun applicationContext(): Context
    fun application(): Application
    fun repositoryFactory(): RepositoryFactory
    fun detailsRepository(): DetailsRepository
    @TmdbApiService fun tmdbApiService(): ExternalApiService
    fun preferenceManager(): PreferenceManager
    fun syncManager(): SyncManager
    fun parentalControlManager(): ParentalControlManager
    fun userRepository(): com.kybers.play.data.repository.UserRepository
    fun userSession(): UserSession
}