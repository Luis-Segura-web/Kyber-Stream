package com.kybers.play.di

import android.content.Context
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.ui.components.ParentalControlManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
        return PreferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        preferenceManager: PreferenceManager
    ): SyncManager {
        return SyncManager(context, preferenceManager)
    }

    @Provides
    @Singleton
    fun provideParentalControlManager(
        preferenceManager: PreferenceManager
    ): ParentalControlManager {
        return ParentalControlManager(preferenceManager)
    }
}