package com.kybers.play.di

import android.content.Context
import com.kybers.play.data.local.AppDatabase
import com.kybers.play.data.local.UserDao
import com.kybers.play.data.local.LiveStreamDao
import com.kybers.play.data.local.MovieDao
import com.kybers.play.data.local.SeriesDao
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.local.MovieDetailsCacheDao
import com.kybers.play.data.local.EpisodeDao
import com.kybers.play.data.local.SeriesDetailsCacheDao
import com.kybers.play.data.local.ActorDetailsCacheDao
import com.kybers.play.data.local.EpisodeDetailsCacheDao
import com.kybers.play.data.local.CategoryCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao {
        return database.movieDao()
    }

    @Provides
    fun provideSeriesDao(database: AppDatabase): SeriesDao {
        return database.seriesDao()
    }

    @Provides
    fun provideLiveStreamDao(database: AppDatabase): LiveStreamDao {
        return database.liveStreamDao()
    }

    @Provides
    fun provideEpgEventDao(database: AppDatabase): EpgEventDao {
        return database.epgEventDao()
    }

    @Provides
    fun provideMovieDetailsCacheDao(database: AppDatabase): MovieDetailsCacheDao {
        return database.movieDetailsCacheDao()
    }

    @Provides
    fun provideEpisodeDao(database: AppDatabase): EpisodeDao {
        return database.episodeDao()
    }

    @Provides
    fun provideSeriesDetailsCacheDao(database: AppDatabase): SeriesDetailsCacheDao {
        return database.seriesDetailsCacheDao()
    }

    @Provides
    fun provideActorDetailsCacheDao(database: AppDatabase): ActorDetailsCacheDao {
        return database.actorDetailsCacheDao()
    }

    @Provides
    fun provideEpisodeDetailsCacheDao(database: AppDatabase): EpisodeDetailsCacheDao {
        return database.episodeDetailsCacheDao()
    }

    @Provides
    fun provideCategoryCacheDao(database: AppDatabase): CategoryCacheDao {
        return database.categoryCacheDao()
    }
}