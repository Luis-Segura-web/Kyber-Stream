package com.kybers.play.ui.sync

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * A full-screen composable that displays a loading indicator and a message
 * that updates according to the current step of the data synchronization process.
 *
 * @param navController The NavController for navigation between screens.
 * @param viewModel The [SyncViewModel] that controls the state of this screen.
 * @param userId The user ID to navigate to main screen after successful sync.
 */
@Composable
fun SyncScreen(
    navController: NavController,
    viewModel: SyncViewModel,
    userId: Int
) {
    // Observe the sync state from the ViewModel.
    val syncState by viewModel.syncState.collectAsState()

    // Start sync automatically when the screen appears
    LaunchedEffect(Unit) {
        viewModel.startSync(userId)
    }

    // This effect will trigger navigation when the sync state changes to Success or Error.
    LaunchedEffect(syncState) {
        when (syncState) {
            is SyncState.Success -> {
                navController.navigate("main/$userId") {
                    popUpTo("login") { inclusive = true }
                }
            }
            is SyncState.Error -> {
                navController.navigate("login") {
                    popUpTo("sync/$userId") { inclusive = true }
                }
            }
            else -> { /* Do nothing while syncing */
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A circular progress indicator to show that work is being done.
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // AnimatedContent will provide a smooth transition between text messages.
            AnimatedContent(
                targetState = syncState,
                label = "SyncStatusText"
            ) { state ->
                Text(
                    text = when (state) {
                        is SyncState.SyncingChannels -> "Sincronizando canales..."
                        is SyncState.SyncingMovies -> "Sincronizando películas..."
                        is SyncState.SyncingSeries -> "Sincronizando series..."
                        else -> "Preparando..."
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Por favor, espera un momento.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
