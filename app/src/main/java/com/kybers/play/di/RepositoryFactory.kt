package com.kybers.play.di

import com.kybers.play.core.epg.EpgCacheManager
import com.kybers.play.data.local.*
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.VodRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory to create repositories that require dynamic base URLs
 */
@Singleton
class RepositoryFactory @Inject constructor(
    private val liveStreamDao: LiveStreamDao,
    private val epgEventDao: EpgEventDao,
    private val categoryCacheDao: CategoryCacheDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val epgCacheManager: EpgCacheManager
) {
    
    fun createLiveRepository(baseUrl: String): LiveRepository {
        val xtreamApiService = RetrofitClient.create(baseUrl)
        return LiveRepository(
            xtreamApiService = xtreamApiService,
            liveStreamDao = liveStreamDao,
            epgEventDao = epgEventDao,
            categoryCacheDao = categoryCacheDao,
            epgCacheManager = epgCacheManager
        )
    }

    fun createVodRepository(baseUrl: String): VodRepository {
        val xtreamApiService = RetrofitClient.create(baseUrl)
        return VodRepository(
            xtreamApiService = xtreamApiService,
            movieDao = movieDao,
            seriesDao = seriesDao,
            episodeDao = episodeDao,
            categoryCacheDao = categoryCacheDao
        )
    }
}