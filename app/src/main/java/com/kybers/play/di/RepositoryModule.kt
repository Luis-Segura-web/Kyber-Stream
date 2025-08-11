package com.kybers.play.di

import com.kybers.play.data.local.*
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository {
        return UserRepository(userDao)
    }

    @Provides
    @Singleton
    fun provideDetailsRepository(
        @com.kybers.play.di.TmdbApiService tmdbApiService: ExternalApiService,
        movieDetailsCacheDao: MovieDetailsCacheDao,
        seriesDetailsCacheDao: SeriesDetailsCacheDao,
        actorDetailsCacheDao: ActorDetailsCacheDao,
        episodeDetailsCacheDao: EpisodeDetailsCacheDao
    ): DetailsRepository {
        return DetailsRepository(
            tmdbApiService = tmdbApiService,
            movieDetailsCacheDao = movieDetailsCacheDao,
            seriesDetailsCacheDao = seriesDetailsCacheDao,
            actorDetailsCacheDao = actorDetailsCacheDao,
            episodeDetailsCacheDao = episodeDetailsCacheDao
        )
    }

    // Note: LiveRepository and VodRepository are created dynamically with different base URLs
    // These will be provided through factory interfaces or created as needed
}