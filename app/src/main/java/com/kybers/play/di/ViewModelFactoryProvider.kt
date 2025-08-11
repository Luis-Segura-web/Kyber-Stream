package com.kybers.play.di

import android.app.Application
import android.content.Context
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.LiveRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.ui.components.ParentalControlManager
import com.kybers.play.ui.theme.ThemeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

/**
 * Factory provider for creating complex ViewModels with dynamic dependencies.
 * This bridges the gap between Hilt DI and ViewModels that need runtime parameters.
 */
@ViewModelScoped
class ViewModelFactoryProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repositoryFactory: RepositoryFactory,
    private val detailsRepository: DetailsRepository,
    @TmdbApiService private val tmdbApiService: ExternalApiService,
    private val preferenceManager: PreferenceManager,
    private val syncManager: SyncManager,
    private val parentalControlManager: ParentalControlManager
) {

    private val application: Application = context as Application

    fun createContentViewModelFactory(
        currentUser: User,
        vodRepository: VodRepository,
        liveRepository: LiveRepository
    ): com.kybers.play.ui.ContentViewModelFactory {
        return com.kybers.play.ui.ContentViewModelFactory(
            application = application,
            vodRepository = vodRepository,
            liveRepository = liveRepository,
            detailsRepository = detailsRepository,
            externalApiService = tmdbApiService,
            currentUser = currentUser,
            preferenceManager = preferenceManager,
            syncManager = syncManager,
            parentalControlManager = parentalControlManager
        )
    }

    fun createMovieDetailsViewModelFactory(
        currentUser: User,
        vodRepository: VodRepository,
        movieId: Int
    ): com.kybers.play.ui.MovieDetailsViewModelFactory {
        return com.kybers.play.ui.MovieDetailsViewModelFactory(
            application = application,
            vodRepository = vodRepository,
            detailsRepository = detailsRepository,
            externalApiService = tmdbApiService,
            preferenceManager = preferenceManager,
            currentUser = currentUser,
            movieId = movieId
        )
    }

    fun createSeriesDetailsViewModelFactory(
        currentUser: User,
        vodRepository: VodRepository,
        seriesId: Int
    ): com.kybers.play.ui.SeriesDetailsViewModelFactory {
        return com.kybers.play.ui.SeriesDetailsViewModelFactory(
            application = application,
            preferenceManager = preferenceManager,
            vodRepository = vodRepository,
            detailsRepository = detailsRepository,
            externalApiService = tmdbApiService,
            currentUser = currentUser,
            seriesId = seriesId
        )
    }

    fun createSettingsViewModelFactory(
        currentUser: User,
        liveRepository: LiveRepository,
        vodRepository: VodRepository,
        themeManager: ThemeManager?
    ): com.kybers.play.ui.SettingsViewModelFactory {
        return com.kybers.play.ui.SettingsViewModelFactory(
            context = application,
            liveRepository = liveRepository,
            vodRepository = vodRepository,
            preferenceManager = preferenceManager,
            syncManager = syncManager,
            currentUser = currentUser,
            parentalControlManager = parentalControlManager,
            themeManager = themeManager
        )
    }
}