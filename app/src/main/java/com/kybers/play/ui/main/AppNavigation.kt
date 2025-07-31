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
import com.kybers.play.ui.LoginViewModelFactory
import com.kybers.play.ui.SyncViewModelFactory
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
import com.kybers.play.ui.login.LoginScreen
import com.kybers.play.ui.login.LoginViewModel
import com.kybers.play.ui.splash.SplashScreen
import com.kybers.play.ui.sync.SyncScreen
import com.kybers.play.ui.sync.SyncViewModel
import com.kybers.play.cache.PreloadingManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.kybers.play.cache.CacheVerification
import com.kybers.play.ui.theme.rememberThemeManager

// Bottom navigation items for the main app
private val bottomBarItems = listOf(
    Screen.Home,
    Screen.Channels,
    Screen.Movies,
    Screen.Series,
)

@Composable
private fun MainScreenWithBottomNav(
    contentViewModelFactory: ContentViewModelFactory,
    movieDetailsViewModelFactoryProvider: @Composable (Int) -> MovieDetailsViewModelFactory,
    seriesDetailsViewModelFactoryProvider: @Composable (Int) -> SeriesDetailsViewModelFactory,
    settingsViewModelFactoryProvider: @Composable () -> SettingsViewModelFactory,
    preloadingManager: PreloadingManager,
    currentUserId: Int,
    onPlayerUiStateChanged: (isFullScreen: Boolean, isPipMode: Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    var isPlayerFullScreen by remember { mutableStateOf(false) }
    var isPlayerInPipMode by remember { mutableStateOf(false) }

    // Initialize preloading and cache verification
    LaunchedEffect(Unit) {
        try {
            Log.d("MainScreen", "Iniciando verificación y precarga del sistema")
            
            // Verify cache system works
            val cacheVerification = CacheVerification(context)
            cacheVerification.runBasicTests()
            
            preloadingManager.preloadPopularContent()
            
            // If user is logged in, preload their preferences
            preloadingManager.preloadUserPreferences(currentUserId)
            Log.d("MainScreen", "Sistema de precarga inicializado correctamente")
        } catch (e: Exception) {
            Log.e("MainScreen", "Error inicializando sistema de precarga", e)
        }
    }

    val isBottomBarVisible = bottomBarItems.any { it.route == currentDestination?.route } && !isPlayerFullScreen && !isPlayerInPipMode

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NavigationBar {
                    bottomBarItems.forEach { screen ->
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
    appContainer: com.kybers.play.AppContainer,
    application: android.app.Application
) {
    NavHost(navController, startDestination = Screen.Splash.route, modifier) {
        composable(Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        composable(Screen.Login.route) {
            val loginViewModelFactory = remember {
                LoginViewModelFactory(appContainer.userRepository)
            }
            val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
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
            
            // Load user to get URL for creating repositories
            var user by remember { mutableStateOf<com.kybers.play.data.local.model.User?>(null) }
            LaunchedEffect(userId) {
                user = appContainer.userRepository.getUserById(userId)
            }
            
            user?.let { currentUser ->
                val vodRepository = remember(currentUser.url) { appContainer.createVodRepository(currentUser.url) }
                val liveRepository = remember(currentUser.url) { appContainer.createLiveRepository(currentUser.url) }
                
                val syncViewModelFactory = remember(userId) {
                    SyncViewModelFactory(
                        liveRepository = liveRepository,
                        vodRepository = vodRepository,
                        syncManager = appContainer.syncManager,
                        preferenceManager = appContainer.preferenceManager
                    )
                }
                val viewModel: SyncViewModel = viewModel(factory = syncViewModelFactory)
                SyncScreen(
                    navController = navController,
                    viewModel = viewModel,
                    userId = userId
                )
            } ?: run {
                // Show loading while user is being loaded
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        }
        composable(
            route = Screen.Main.route,
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            
            // Load user and create all necessary factories
            var user by remember { mutableStateOf<com.kybers.play.data.local.model.User?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            
            LaunchedEffect(userId) {
                user = appContainer.userRepository.getUserById(userId)
                isLoading = false
            }
            
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                user != null -> {
                    val vodRepository = remember(user!!.url) { appContainer.createVodRepository(user!!.url) }
                    val liveRepository = remember(user!!.url) { appContainer.createLiveRepository(user!!.url) }
                    val preloadingManager = remember(user!!.id) { 
                        appContainer.createPreloadingManager(vodRepository, liveRepository, user!!) 
                    }

                    val contentViewModelFactory = remember(user!!.id) {
                        ContentViewModelFactory(
                            application = application,
                            vodRepository = vodRepository,
                            liveRepository = liveRepository,
                            detailsRepository = appContainer.detailsRepository,
                            externalApiService = appContainer.tmdbApiService,
                            currentUser = user!!,
                            preferenceManager = appContainer.preferenceManager,
                            syncManager = appContainer.syncManager,
                            parentalControlManager = appContainer.parentalControlManager
                        )
                    }

                    val movieDetailsViewModelFactoryProvider = @Composable { movieId: Int ->
                        remember(movieId) {
                            MovieDetailsViewModelFactory(
                                application = application,
                                vodRepository = vodRepository,
                                detailsRepository = appContainer.detailsRepository,
                                externalApiService = appContainer.tmdbApiService,
                                preferenceManager = appContainer.preferenceManager,
                                currentUser = user!!,
                                movieId = movieId
                            )
                        }
                    }

                    val seriesDetailsViewModelFactoryProvider = @Composable { seriesId: Int ->
                        remember(seriesId) {
                            SeriesDetailsViewModelFactory(
                                application = application,
                                preferenceManager = appContainer.preferenceManager,
                                vodRepository = vodRepository,
                                detailsRepository = appContainer.detailsRepository,
                                externalApiService = appContainer.tmdbApiService,
                                currentUser = user!!,
                                seriesId = seriesId
                            )
                        }
                    }

                    val themeManager = rememberThemeManager(application as android.content.Context)

                    val settingsViewModelFactoryProvider = @Composable {
                        remember {
                            SettingsViewModelFactory(
                                context = application,
                                liveRepository = liveRepository,
                                vodRepository = vodRepository,
                                preferenceManager = appContainer.preferenceManager,
                                syncManager = appContainer.syncManager,
                                currentUser = user!!,
                                parentalControlManager = appContainer.parentalControlManager,
                                themeManager = themeManager
                            )
                        }
                    }

                    // Show the MainScreen with bottom navigation
                    MainScreenWithBottomNav(
                        contentViewModelFactory = contentViewModelFactory,
                        movieDetailsViewModelFactoryProvider = movieDetailsViewModelFactoryProvider,
                        seriesDetailsViewModelFactoryProvider = seriesDetailsViewModelFactoryProvider,
                        settingsViewModelFactoryProvider = settingsViewModelFactoryProvider,
                        preloadingManager = preloadingManager,
                        currentUserId = userId,
                        onPlayerUiStateChanged = { _, _ -> /* Handle player UI state changes if needed */ },
                        onLogout = {
                            // Navigate to splash to restart the flow
                            navController.navigate(Screen.Splash.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
                    )
                } else -> {
                    // User not found, navigate back to login
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