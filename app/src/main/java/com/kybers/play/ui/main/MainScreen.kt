package com.kybers.play.ui.main

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
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
import com.kybers.play.data.preferences.SyncManager
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.MovieDetailsViewModelFactory
import com.kybers.play.ui.channels.ChannelsScreen
import com.kybers.play.ui.details.MovieDetailsScreen
import com.kybers.play.ui.details.MovieDetailsViewModel
import com.kybers.play.ui.home.HomeScreen
import com.kybers.play.ui.home.HomeViewModel
import com.kybers.play.ui.movies.MoviesScreen
import com.kybers.play.ui.movies.MoviesViewModel
import com.kybers.play.ui.series.SeriesScreen

sealed class Screen(val route: String, val label: String? = null, val icon: ImageVector? = null) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Channels : Screen("channels", "Canales", Icons.Default.Tv)
    object Movies : Screen("movies", "Películas", Icons.Default.Movie)
    object Series : Screen("series", "Series", Icons.Default.Movie)
    object MovieDetails : Screen("movie_details/{movieId}")

    fun withArgs(vararg args: Any): String {
        return buildString {
            append(route.split("/")[0])
            args.forEach { arg ->
                append("/$arg")
            }
        }
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
    contentRepository: ContentRepository,
    preferenceManager: PreferenceManager,
    currentUser: User,
    syncManager: SyncManager
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // ¡CAMBIO CLAVE! Estados para controlar la visibilidad de la barra de navegación.
    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var isPlayerInPipMode by remember { mutableStateOf(false) }

    // La barra solo es visible si estamos en una ruta principal Y el reproductor no está en pantalla completa o PiP.
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
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
                // ¡CAMBIO CLAVE! Pasamos el nuevo callback a ChannelsScreen.
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
                        navController.navigate(Screen.MovieDetails.withArgs(movieId))
                    }
                )
            }
            composable(Screen.Series.route) {
                SeriesScreen()
            }
            composable(
                route = Screen.MovieDetails.route,
                arguments = listOf(navArgument("movieId") { type = NavType.IntType })
            ) { backStackEntry ->
                val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
                val detailsViewModel: MovieDetailsViewModel = viewModel(
                    factory = MovieDetailsViewModelFactory(
                        application = application,
                        contentRepository = contentRepository,
                        preferenceManager = preferenceManager,
                        currentUser = currentUser,
                        movieId = movieId
                    )
                )
                MovieDetailsScreen(
                    viewModel = detailsViewModel,
                    onNavigateUp = { navController.navigateUp() }
                )
            }
        }
    }
}
