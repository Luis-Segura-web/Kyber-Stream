package com.kybers.play.ui.main

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.MovieDetailsViewModelFactory
import com.kybers.play.ui.SeriesDetailsViewModelFactory
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

sealed class Screen(val route: String, val label: String? = null, val icon: ImageVector? = null) {
    object Home : Screen("home", "Inicio", Icons.Outlined.Home)
    object Channels : Screen("channels", "TV en Vivo", Icons.Outlined.LiveTv)
    object Movies : Screen("movies", "Películas", Icons.Outlined.Movie)
    object Series : Screen("series", "Series", Icons.Outlined.Slideshow)
    object MovieDetails : Screen("movie_details/{movieId}") {
        fun createRoute(movieId: Int) = "movie_details/$movieId"
    }
    object SeriesDetails : Screen("series_details/{seriesId}") {
        fun createRoute(seriesId: Int) = "series_details/$seriesId"
    }
}

private val items = listOf(
    Screen.Home,
    Screen.Channels,
    Screen.Movies,
    Screen.Series,
)

@Composable
fun MainScreen(
    contentViewModelFactory: ContentViewModelFactory,
    movieDetailsViewModelFactoryProvider: @Composable (Int) -> MovieDetailsViewModelFactory,
    seriesDetailsViewModelFactoryProvider: @Composable (Int) -> SeriesDetailsViewModelFactory
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var isPlayerInPipMode by remember { mutableStateOf(false) }

    val isBottomBarVisible = items.any { it.route == currentDestination?.route } && !isPlayerFullScreen && !isPlayerInPipMode

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.label) },
                            label = { Text(screen.label!!) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(factory = contentViewModelFactory)
                HomeScreen(
                    homeViewModel = homeViewModel,
                    onMovieClick = { movieId ->
                        navController.navigate(Screen.MovieDetails.createRoute(movieId))
                    },
                    onSeriesClick = { seriesId ->
                        navController.navigate(Screen.SeriesDetails.createRoute(seriesId))
                    },
                    onChannelClick = { channel ->
                        navController.navigate(Screen.Channels.route)
                    },
                    onSettingsClick = {
                        Toast.makeText(context, "Ajustes próximamente", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            composable(Screen.Channels.route) {
                val channelsViewModel: ChannelsViewModel = viewModel(factory = contentViewModelFactory)
                ChannelsScreen(
                    viewModel = channelsViewModel,
                    onPlayerUiStateChanged = { isFull, isPip ->
                        isPlayerFullScreen = isFull
                        isPlayerInPipMode = isPip
                    }
                )
            }
            composable(Screen.Movies.route) {
                val moviesViewModel: MoviesViewModel = viewModel(factory = contentViewModelFactory)
                MoviesScreen(
                    viewModel = moviesViewModel,
                    onNavigateToDetails = { movieId ->
                        navController.navigate(Screen.MovieDetails.createRoute(movieId))
                    }
                )
            }
            composable(Screen.Series.route) {
                val seriesViewModel: SeriesViewModel = viewModel(factory = contentViewModelFactory)
                SeriesScreen(
                    viewModel = seriesViewModel,
                    onNavigateToDetails = { seriesId ->
                        navController.navigate(Screen.SeriesDetails.createRoute(seriesId))
                    }
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
        }
    }
}
