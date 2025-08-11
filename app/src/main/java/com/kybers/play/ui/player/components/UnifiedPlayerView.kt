package com.kybers.play.ui.player.components

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.kybers.play.core.player.Media3Engine
import com.kybers.play.core.player.PlayerEngine
import com.kybers.play.core.player.VlcEngine
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Reproductor unificado que puede mostrar tanto Media3 como VLC
 * según el motor activo del PlayerCoordinator
 */
@Composable
fun UnifiedPlayerView(
    engine: PlayerEngine?,
    modifier: Modifier = Modifier,
    onPlayerReady: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (engine) {
            is Media3Engine -> {
                Media3PlayerView(
                    engine = engine,
                    modifier = Modifier.fillMaxSize(),
                    onPlayerReady = onPlayerReady
                )
            }
            is VlcEngine -> {
                VlcPlayerView(
                    engine = engine,
                    modifier = Modifier.fillMaxSize(),
                    onPlayerReady = onPlayerReady
                )
            }
            null -> {
                PlayerPlaceholder(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Vista de Media3 (ExoPlayer)
 */
@Composable
private fun Media3PlayerView(
    engine: Media3Engine,
    modifier: Modifier = Modifier,
    onPlayerReady: () -> Unit
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false // Usaremos controles personalizados
                player = engine.getExoPlayer()
                onPlayerReady()
            }
        },
        update = { playerView ->
            playerView.player = engine.getExoPlayer()
        },
        modifier = modifier
    )
}

/**
 * Vista de VLC
 */
@Composable
private fun VlcPlayerView(
    engine: VlcEngine,
    modifier: Modifier = Modifier,
    onPlayerReady: () -> Unit
) {
    AndroidView(
        factory = { context ->
            VLCVideoLayout(context).apply {
                // Configurar VLC video layout
                // Esta implementación puede necesitar ajustes según la versión de VLC
                onPlayerReady()
            }
        },
        modifier = modifier
    )
}

/**
 * Placeholder cuando no hay motor activo
 */
@Composable
private fun PlayerPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Reproductor",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Selecciona un contenido para reproducir",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Controles de reproductor que funcionan con ambos motores
 */
@Composable
fun UnifiedPlayerControls(
    engine: PlayerEngine?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onStop,
            enabled = engine != null
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Detener"
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        IconButton(
            onClick = onPlayPause,
            enabled = engine != null
        ) {
            Icon(
                imageVector = if (isPlaying) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (isPlaying) "Pausar" else "Reproducir"
            )
        }
    }
}

/**
 * Indicador del motor activo (solo en debug)
 */
@Composable
fun EngineIndicator(
    engine: PlayerEngine?,
    modifier: Modifier = Modifier
) {
    if (com.kybers.play.BuildConfig.DEBUG && engine != null) {
        val engineName = when (engine) {
            is Media3Engine -> "Media3"
            is VlcEngine -> "VLC"
            else -> "Unknown"
        }
        
        Card(
            modifier = modifier.padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = "Engine: $engineName",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}