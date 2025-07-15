package com.kybers.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Un Composable que muestra los controles de reproducción sobre el video.
 */
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    player: Player,
    isVisible: () -> Boolean,
    isFullScreen: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
    onToggleFullScreen: () -> Unit
) {
    val isPlaying by remember { derivedStateOf { player.isPlaying } }
    val isLive by remember { derivedStateOf { player.isCurrentMediaItemLive } }

    LaunchedEffect(isVisible()) {
        if (isVisible()) {
            delay(5000)
            onVisibilityChanged(false)
        }
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = isVisible(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onVisibilityChanged(false) }
                )
        ) {
            // Controles centrales (Play/Pausa, etc.)
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.seekBack() }) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Retroceder 10s",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pausa",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
                IconButton(onClick = { player.seekForward() }) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Adelantar 10s",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Controles inferiores (Barra de progreso, tiempos, etc.)
            if (!isLive) {
                BottomControls(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                    player = player,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = onToggleFullScreen
                )
            }
        }
    }
}

/**
 * Controles de la parte inferior, incluyendo la barra de progreso y los tiempos.
 */
@Composable
private fun BottomControls(
    modifier: Modifier = Modifier,
    player: Player,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var totalDuration by remember { mutableLongStateOf(player.duration) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player.isPlaying) {
        while (true) {
            currentPosition = player.currentPosition
            totalDuration = player.duration
            delay(1000)
        }
    }

    Column(modifier = modifier) {
        Slider(
            value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                val newPosition = (sliderPosition * totalDuration).toLong()
                player.seekTo(newPosition)
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = formatDuration(currentPosition), color = Color.White)
            Text(text = formatDuration(totalDuration), color = Color.White)
            Spacer(modifier = Modifier.weight(1f)) // Empuja el botón a la derecha
            IconButton(onClick = onToggleFullScreen) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Pantalla Completa",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Formatea una duración en milisegundos a un string legible (HH:MM:SS o MM:SS).
 */
private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % TimeUnit.MINUTES.toSeconds(1)

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
