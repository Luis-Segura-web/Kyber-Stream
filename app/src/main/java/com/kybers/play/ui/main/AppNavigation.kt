package com.kybers.play.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kybers.play.ui.main.MainViewModel
import com.kybers.play.ui.channels.ChannelsScreen
import com.kybers.play.ui.channels.ChannelsViewModel
import com.kybers.play.ui.movies.MovieDetailsScreen
import com.kybers.play.ui.movies.MovieDetailsViewModel
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
import com.kybers.play.ui.login.LoginScreen
import com.kybers.play.ui.login.LoginViewModel
import com.kybers.play.ui.splash.SplashScreen
import com.kybers.play.ui.sync.SyncScreen
import com.kybers.play.ui.sync.SyncViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import dagger.hilt.android.EntryPointAccessors

// Bottom navigation items for the main app
private val bottomBarItems = listOf(
    Screen.Home,
    Screen.Channels,
    Screen.Movies,
    Screen.Series,
)

@Composable
private fun MainScreenWithBottomNav(
    currentUserId: Int,
    user: com.kybers.play.data.local.model.User,
    vodRepository: com.kybers.play.data.repository.VodRepository,
    liveRepository: com.kybers.play.data.repository.LiveRepository,
    themeManager: com.kybers.play.ui.theme.ThemeManager,
    onPlayerUiStateChanged: (isFullScreen: Boolean, isPipMode: Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var isPlayerInPipMode by remember { mutableStateOf(false) }

    // Get dependencies from Hilt using EntryPoint
    val context = LocalContext.current
    val hiltEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.kybers.play.di.AppDependencies::class.java
    )

    // Create factories for each ViewModel type
    val contentViewModelFactory = remember(user.id) {
        ContentViewModelFactory(
            application = hiltEntryPoint.application(),
            vodRepository = vodRepository,
            liveRepository = liveRepository,
            detailsRepository = hiltEntryPoint.detailsRepository(),
            externalApiService = hiltEntryPoint.tmdbApiService(),
            currentUser = user,
            preferenceManager = hiltEntryPoint.preferenceManager(),
            syncManager = hiltEntryPoint.syncManager(),
            parentalControlManager = hiltEntryPoint.parentalControlManager()
        )
    }

    val movieDetailsViewModelFactoryProvider = @Composable { movieId: Int ->
        remember(movieId) {
            MovieDetailsViewModelFactory(
                application = hiltEntryPoint.application(),
                vodRepository = vodRepository,
                detailsRepository = hiltEntryPoint.detailsRepository(),
                externalApiService = hiltEntryPoint.tmdbApiService(),
                preferenceManager = hiltEntryPoint.preferenceManager(),
                currentUser = user,
                movieId = movieId
            )
        }
    }

    val seriesDetailsViewModelFactoryProvider = @Composable { seriesId: Int ->
        remember(seriesId) {
            SeriesDetailsViewModelFactory(
                application = hiltEntryPoint.application(),
                preferenceManager = hiltEntryPoint.preferenceManager(),
                vodRepository = vodRepository,
                detailsRepository = hiltEntryPoint.detailsRepository(),
                externalApiService = hiltEntryPoint.tmdbApiService(),
                currentUser = user,
                seriesId = seriesId
            )
        }
    }

    val settingsViewModelFactoryProvider = @Composable {
        remember {
            com.kybers.play.ui.SettingsViewModelFactory(
                context = hiltEntryPoint.applicationContext(),
                liveRepository = liveRepository,
                vodRepository = vodRepository,
                preferenceManager = hiltEntryPoint.preferenceManager(),
                syncManager = hiltEntryPoint.syncManager(),
                currentUser = user,
                parentalControlManager = hiltEntryPoint.parentalControlManager(),
                themeManager = themeManager
            )
        }
    }

    val isBottomBarVisible = bottomBarItems.any { it.route == currentDestination?.route } && !isPlayerFullScreen && !isPlayerInPipMode

    com.kybers.play.ui.theme.ResponsiveScaffold(
        topBar = {
            // Top bar can be empty for now, screens will handle their own top bars
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                com.kybers.play.ui.main.ResponsiveNavigation(
                    navController = navController,
                    currentDestination = currentDestination,
                    isVisible = true
                )
            }
        },
        navigationRail = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                com.kybers.play.ui.main.ResponsiveNavigationRail(
                    navController = navController,
                    currentDestination = currentDestination
                )
            }
        }
    ) { innerPadding ->
        // Nested navigation for main app screens
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
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
                    onPlayerUiStateChanged = { isFull, isPip ->
                        isPlayerFullScreen = isFull
                        isPlayerInPipMode = isPip
                        onPlayerUiStateChanged(isFull, isPip)
                    }
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
                        navController.currentDestination?.id?.let { destinationId ->
                            navController.navigate(Screen.MovieDetails.createRoute(newMovieId)) {
                                popUpTo(destinationId) { inclusive = true }
                            }
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
                        navController.currentDestination?.id?.let { destinationId ->
                            navController.navigate(Screen.SeriesDetails.createRoute(newSeriesId)) {
                                popUpTo(destinationId) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                val factory = settingsViewModelFactoryProvider()
                val viewModel: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateUp = { navController.popBackStack() },
                    onNavigateToLogin = onLogout
                )
            }
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    themeManager: com.kybers.play.ui.theme.ThemeManager
) {
    NavHost(navController, startDestination = Screen.Splash.route, modifier) {
        composable(Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        composable(Screen.Login.route) {
            val loginViewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                navController = navController,
                viewModel = loginViewModel
            )
        }
        composable(
            route = Screen.Sync.route,
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            
            val viewModel: SyncViewModel = hiltViewModel()
            SyncScreen(
                navController = navController,
                viewModel = viewModel,
                userId = userId
            )
        }
        composable(
            route = Screen.Main.route,
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0

            val mainViewModel: MainViewModel = hiltViewModel()
            val uiState by mainViewModel.uiState.collectAsState()

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                uiState.user != null -> {
                    val user = requireNotNull(uiState.user)
                    val vodRepository = uiState.vodRepository!!
                    val liveRepository = uiState.liveRepository!!

                    MainScreenWithBottomNav(
                        currentUserId = userId,
                        user = user,
                        vodRepository = vodRepository,
                        liveRepository = liveRepository,
                        themeManager = themeManager,
                        onPlayerUiStateChanged = { _, _ -> /* Handle player UI state changes if needed */ },
                        onLogout = {
                            navController.navigate(Screen.Splash.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
                    )
                }
                else -> {
                    LaunchedEffect(Unit) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            }
        }
    }
}