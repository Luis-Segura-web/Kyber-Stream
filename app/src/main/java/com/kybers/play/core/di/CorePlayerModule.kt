package com.kybers.play.core.di

import com.kybers.play.core.player.PlayerCoordinator
import com.kybers.play.core.player.PlayerSelector
import com.kybers.play.core.player.StreamingLeaseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Módulo de dependencias para los componentes centrales del reproductor
 */
@Module
@InstallIn(SingletonComponent::class)
object CorePlayerModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideStreamingLeaseManager(clock: Clock): StreamingLeaseManager {
        return StreamingLeaseManager(clock)
    }

    @Provides
    @Singleton
    fun providePlayerCoordinator(
        selector: PlayerSelector,
        lease: StreamingLeaseManager
    ): PlayerCoordinator {
        return PlayerCoordinator(selector, lease)
    }
}