package com.kybers.play.ui.main

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kybers.play.MainApplication
import com.kybers.play.data.local.model.User
import com.kybers.play.ui.ContentViewModelFactory
import com.kybers.play.ui.MovieDetailsViewModelFactory
import com.kybers.play.ui.SeriesDetailsViewModelFactory
import com.kybers.play.ui.SettingsViewModelFactory
import com.kybers.play.ui.theme.IPTVAppTheme
import com.kybers.play.ui.theme.rememberThemeManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getIntExtra("USER_ID", -1)
        val appContainer = (application as MainApplication).container

        setContent {
            val themeManager = rememberThemeManager(this@MainActivity)
            IPTVAppTheme(themeManager = themeManager) {
                if (userId == -1) {
                    // No user selected, start from splash
                    var selectedUserId by remember { mutableStateOf<Int?>(null) }
                    
                    selectedUserId?.let { currentUserId ->
                        UserBasedMainScreen(
                            appContainer = appContainer,
                            userId = currentUserId,
                            application = application,
                            themeManager = themeManager
                        )
                    } ?: run {
                        // Show splash -> login flow
                        val loginViewModelFactory = remember {
                            com.kybers.play.ui.LoginViewModelFactory(appContainer.userRepository)
                        }
                        
                        SplashToLoginNavigation(
                            loginViewModelFactory = loginViewModelFactory,
                            onUserSelected = { newUserId ->
                                selectedUserId = newUserId
                            }
                        )
                    }
                } else {
                    // User already selected, show main content directly
                    UserBasedMainScreen(
                        appContainer = appContainer,
                        userId = userId,
                        application = application,
                        themeManager = themeManager
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashToLoginNavigation(
    loginViewModelFactory: com.kybers.play.ui.LoginViewModelFactory,
    onUserSelected: (Int) -> Unit
) {
    val navController = androidx.navigation.compose.rememberNavController()
    
    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        androidx.navigation.compose.composable("splash") {
            com.kybers.play.ui.splash.SplashScreen(navController = navController)
        }
        androidx.navigation.compose.composable("login") {
            val loginViewModel: com.kybers.play.ui.login.LoginViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = loginViewModelFactory)
            com.kybers.play.ui.login.LoginScreen(
                viewModel = loginViewModel,
                onUserSelected = { user ->
                    onUserSelected(user.id)
                },
                onNavigateToSyncAfterUserAdded = { user ->
                    onUserSelected(user.id)
                }
            )
        }
    }
}

@Composable
private fun UserBasedMainScreen(
    appContainer: com.kybers.play.AppContainer,
    userId: Int,
    application: android.app.Application,
    themeManager: com.kybers.play.ui.theme.ThemeManager
) {
    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        val loadedUser = appContainer.userRepository.getUserById(userId)
        if (loadedUser == null) {
            error = "No se pudo encontrar el perfil del usuario."
        } else {
            user = loadedUser
        }
        isLoading = false
    }

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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

            MainScreen(
                contentViewModelFactory = contentViewModelFactory,
                movieDetailsViewModelFactoryProvider = movieDetailsViewModelFactoryProvider,
                seriesDetailsViewModelFactoryProvider = seriesDetailsViewModelFactoryProvider,
                settingsViewModelFactoryProvider = settingsViewModelFactoryProvider,
                preloadingManager = preloadingManager,
                currentUserId = user!!.id
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Ha ocurrido un error inesperado.")
            }
        }
    }
