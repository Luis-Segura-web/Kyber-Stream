package com.kybers.play.di

import android.content.Context
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.player.MediaManager
import com.kybers.play.ui.settings.DynamicSettingsManager
import com.kybers.play.ui.theme.ThemeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    fun provideMediaManager(): MediaManager {
        return MediaManager()
    }

    @Provides
    @Singleton
    fun provideThemeManager(
        preferenceManager: PreferenceManager
    ): ThemeManager {
        return ThemeManager(preferenceManager)
    }

    @Provides
    fun provideDynamicSettingsManager(
        @ApplicationContext context: Context,
        preferenceManager: PreferenceManager
    ): DynamicSettingsManager {
        return DynamicSettingsManager(context, preferenceManager)
    }
}