package com.kybers.play.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.MovieDetailsViewModelFactory
import com.kybers.play.ui.SeriesDetailsViewModelFactory
import com.kybers.play.ui.SettingsViewModelFactory
import com.kybers.play.ui.channels.ChannelsScreen
import com.kybers.play.ui.channels.ChannelsViewModel
import com.kybers.play.ui.details.MovieDetailsScreen
import com.kybers.play.ui.details.MovieDetailsViewModel
import com.kybers.play.ui.home.HomeScreen
import com.kybers.play.ui.home.HomeViewModel
import com.kybers.play.ui.movies.MoviesScreen
import com.kybers.play.ui.movies.MoviesViewModel
import com.kybers.play.ui.series.SeriesDetailsScreen
import com.kybers.play.ui.series.SeriesDetailsViewModel
import com.kybers.play.ui.series.SeriesScreen
import com.kybers.play.ui.series.SeriesViewModel
import com.kybers.play.ui.settings.SettingsScreen
import com.kybers.play.ui.settings.SettingsViewModel
import com.kybers.play.cache.PreloadingManager

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    contentViewModelFactory: ContentViewModelFactory,
    movieDetailsViewModelFactoryProvider: @Composable (Int) -> MovieDetailsViewModelFactory,
    seriesDetailsViewModelFactoryProvider: @Composable (Int) -> SeriesDetailsViewModelFactory,
    settingsViewModelFactoryProvider: @Composable () -> SettingsViewModelFactory,
    preloadingManager: PreloadingManager,
    currentUserId: Int,
    onPlayerUiStateChanged: (isFullScreen: Boolean, isPipMode: Boolean) -> Unit
) {
    NavHost(navController, startDestination = Screen.Home.route, modifier) {
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(factory = contentViewModelFactory)
            HomeScreen(
                homeViewModel = homeViewModel,
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetails.createRoute(movieId)) },
                onSeriesClick = { seriesId -> navController.navigate(Screen.SeriesDetails.createRoute(seriesId)) },
                onChannelClick = { navController.navigate(Screen.Channels.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Channels.route) {
            val channelsViewModel: ChannelsViewModel = viewModel(factory = contentViewModelFactory)
            ChannelsScreen(
                viewModel = channelsViewModel,
                onPlayerUiStateChanged = onPlayerUiStateChanged
            )
        }
        composable(Screen.Movies.route) {
            val moviesViewModel: MoviesViewModel = viewModel(factory = contentViewModelFactory)
            MoviesScreen(
                viewModel = moviesViewModel,
                onNavigateToDetails = { movieId -> navController.navigate(Screen.MovieDetails.createRoute(movieId)) }
            )
        }
        composable(Screen.Series.route) {
            val seriesViewModel: SeriesViewModel = viewModel(factory = contentViewModelFactory)
            SeriesScreen(
                viewModel = seriesViewModel,
                onNavigateToDetails = { seriesId -> navController.navigate(Screen.SeriesDetails.createRoute(seriesId)) }
            )
        }
        composable(
            route = Screen.MovieDetails.route,
            arguments = listOf(navArgument("movieId") { type = NavType.IntType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
            val factory = movieDetailsViewModelFactoryProvider(movieId)
            val viewModel: MovieDetailsViewModel = viewModel(factory = factory)
            MovieDetailsScreen(
                viewModel = viewModel,
                onNavigateUp = { navController.popBackStack() },
                onNavigateToMovie = { newMovieId ->
                    navController.navigate(Screen.MovieDetails.createRoute(newMovieId)) {
                        popUpTo(navController.currentDestination!!.id) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.SeriesDetails.route,
            arguments = listOf(navArgument("seriesId") { type = NavType.IntType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getInt("seriesId") ?: 0
            val factory = seriesDetailsViewModelFactoryProvider(seriesId)
            val viewModel: SeriesDetailsViewModel = viewModel(factory = factory)
            SeriesDetailsScreen(
                viewModel = viewModel,
                onNavigateUp = { navController.popBackStack() },
                onNavigateToSeries = { newSeriesId ->
                    navController.navigate(Screen.SeriesDetails.createRoute(newSeriesId)) {
                        popUpTo(navController.currentDestination!!.id) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            val factory = settingsViewModelFactoryProvider()
            val viewModel: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = viewModel,
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}