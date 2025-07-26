package com.kybers.play.ui.main

import android.app.Application
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kybers.play.data.local.model.User
import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.repository.DetailsRepository
import com.kybers.play.data.repository.VodRepository
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.MovieDetailsViewModelFactory
import com.kybers.play.ui.SeriesDetailsViewModelFactory
import com.kybers.play.ui.channels.ChannelsScreen
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

// Definición de las rutas de navegación
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

// Lista de items para la barra de navegación inferior
private val items = listOf(
    Screen.Home,
    Screen.Channels,
    Screen.Movies,
    Screen.Series,
)

@Composable
fun MainScreen(
    contentViewModelFactory: ContentViewModelFactory,
    vodRepository: VodRepository,
    detailsRepository: DetailsRepository,
    preferenceManager: PreferenceManager,
    currentUser: User
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var isPlayerInPipMode by remember { mutableStateOf(false) }

    val isBottomBarVisible = items.any { it.route == currentDestination?.route } && !isPlayerFullScreen && !isPlayerInPipMode

    val context = LocalContext.current
    val application = context.applicationContext as Application

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
                HomeScreen(homeViewModel)
            }
            composable(Screen.Channels.route) {
                ChannelsScreen(
                    viewModel = viewModel(factory = contentViewModelFactory),
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

                val detailsViewModel: MovieDetailsViewModel = viewModel(
                    factory = MovieDetailsViewModelFactory(
                        application = application,
                        vodRepository = vodRepository,
                        detailsRepository = detailsRepository,
                        preferenceManager = preferenceManager,
                        currentUser = currentUser,
                        movieId = movieId
                    )
                )
                MovieDetailsScreen(
                    viewModel = detailsViewModel,
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

                val detailsViewModel: SeriesDetailsViewModel = viewModel(
                    factory = SeriesDetailsViewModelFactory(
                        application = application,
                        preferenceManager = preferenceManager,
                        vodRepository = vodRepository,
                        currentUser = currentUser,
                        seriesId = seriesId
                    )
                )
                // --- ¡CORRECCIÓN FINAL! ---
                // Se elimina el parámetro 'onPlayEpisode' que ya no existe.
                SeriesDetailsScreen(
                    viewModel = detailsViewModel,
                    onNavigateUp = { navController.popBackStack() }
                )
            }
        }
    }
}
