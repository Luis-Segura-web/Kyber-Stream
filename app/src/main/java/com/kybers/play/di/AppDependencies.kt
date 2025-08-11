package com.kybers.play.di

import android.app.Application
import android.content.Context
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.player.MediaManager
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.ui.settings.DynamicSettingsManager
import com.kybers.play.ui.theme.ThemeManager
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
    fun mediaManager(): MediaManager
    fun themeManager(): ThemeManager
    fun dynamicSettingsManager(): DynamicSettingsManager
    @CurrentUser fun currentUser(): User
}