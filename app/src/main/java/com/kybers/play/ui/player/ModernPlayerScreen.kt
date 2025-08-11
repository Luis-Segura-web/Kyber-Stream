package com.kybers.play.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kybers.play.core.player.PlayerCoordinator
import com.kybers.play.ui.player.components.*

/**
 * Pantalla de reproductor moderna que utiliza el nuevo sistema de single-connection
 * Esta es una implementación de ejemplo de cómo integrar el PlayerCoordinator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernPlayerScreen(
    streamUrl: String,
    streamTitle: String? = null,
    onBackPressed: () -> Unit,
    viewModel: ModernPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Observar cambios en el lifecycle para el debugging
    DisposableEffect(lifecycleOwner) {
        onDispose {
            // Cleanup si es necesario
        }
    }
    
    // Reproducir automáticamente cuando se pasa una URL
    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotBlank()) {
            viewModel.playMedia(
                url = streamUrl,
                title = streamTitle
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(streamTitle ?: "Reproductor") 
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Reproductor principal
            UnifiedPlayerView(
                engine = getCurrentEngine(uiState.coordinatorState),
                modifier = Modifier.fillMaxSize()
            )
            
            // Indicadores de debug (solo en debug builds)
            if (com.kybers.play.BuildConfig.DEBUG) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    LeaseStateIndicator(
                        leaseState = uiState.leaseState
                    )
                    
                    EngineIndicator(
                        engine = getCurrentEngine(uiState.coordinatorState)
                    )
                }
            }
            
            // Indicador de cooldown
            CooldownIndicator(
                cooldownRemainingMs = uiState.cooldownRemainingMs,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // Controles de reproductor
            UnifiedPlayerControls(
                engine = getCurrentEngine(uiState.coordinatorState),
                isPlaying = isCurrentlyPlaying(uiState.coordinatorState),
                onPlayPause = {
                    if (isCurrentlyPlaying(uiState.coordinatorState)) {
                        viewModel.pause()
                    } else {
                        viewModel.resume()
                    }
                },
                onStop = {
                    viewModel.stop()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            
            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Preparando reproducción...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Error display
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::clearError
                            ) {
                                Text("Cerrar")
                            }
                            
                            Button(
                                onClick = {
                                    viewModel.clearError()
                                    viewModel.playMedia(streamUrl, streamTitle)
                                }
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Diálogo de conflicto de lease
    if (uiState.showLeaseDialog) {
        LeaseConflictDialog(
            onForcePlay = viewModel::forcePlay,
            onCancel = viewModel::cancelPendingPlay
        )
    }
}

/**
 * Extrae el motor actual del estado del coordinador
 */
private fun getCurrentEngine(coordinatorState: PlayerCoordinator.CoordinatorState) = when (coordinatorState) {
    is PlayerCoordinator.CoordinatorState.Playing -> {
        // En una implementación real, necesitaríamos acceso al motor a través del coordinador
        // Por ahora retornamos null como placeholder
        null
    }
    else -> null
}

/**
 * Determina si hay reproducción activa
 */
private fun isCurrentlyPlaying(coordinatorState: PlayerCoordinator.CoordinatorState) = 
    coordinatorState is PlayerCoordinator.CoordinatorState.Playing

/**
 * Preview para desarrollo
 */
@Composable
private fun ModernPlayerScreenPreview() {
    MaterialTheme {
        ModernPlayerScreen(
            streamUrl = "https://example.com/stream.m3u8",
            streamTitle = "Canal de Ejemplo",
            onBackPressed = {}
        )
    }
}