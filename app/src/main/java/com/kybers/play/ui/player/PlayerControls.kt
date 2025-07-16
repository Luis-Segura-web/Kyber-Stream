package com.kybers.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.TimeUnit

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    mediaPlayer: MediaPlayer,
    streamTitle: String,
    isVisible: Boolean,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onReplay: () -> Unit
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f))) {
            // Barra superior con el título
            Text(
                text = streamTitle,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            // Controles centrales
            CenterControls(
                modifier = Modifier.align(Alignment.Center),
                mediaPlayer = mediaPlayer,
                onReplay = onReplay
            )

            // Barra inferior
            BottomControlBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                mediaPlayer = mediaPlayer,
                isFullScreen = isFullScreen,
                onToggleFullScreen = onToggleFullScreen
            )
        }
    }
}

@Composable
private fun CenterControls(
    modifier: Modifier,
    mediaPlayer: MediaPlayer,
    onReplay: () -> Unit
) {
    val isPlaying by remember(mediaPlayer.isPlaying) { mutableStateOf(mediaPlayer.isPlaying) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rebobinar 10 segundos
        IconButton(onClick = { mediaPlayer.time -= 10000 }) {
            Icon(Icons.Default.Replay10, "Rebobinar 10s", tint = Color.White, modifier = Modifier.size(48.dp))
        }

        // Play/Pausa
        IconButton(onClick = {
            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pausa",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        // Adelantar 10 segundos
        IconButton(onClick = { mediaPlayer.time += 10000 }) {
            Icon(Icons.Default.Forward10, "Adelantar 10s", tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun BottomControlBar(
    modifier: Modifier,
    mediaPlayer: MediaPlayer,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit
) {
    var currentTime by remember { mutableStateOf(0L) }
    var totalTime by remember { mutableStateOf(0L) }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            currentTime = mediaPlayer.time
            totalTime = mediaPlayer.length
            delay(1000)
        }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration(currentTime), color = Color.White)
            Text(formatDuration(totalTime), color = Color.White)
        }
        Slider(
            value = if (totalTime > 0) currentTime.toFloat() / totalTime else 0f,
            onValueChange = { mediaPlayer.position = it }
        )
        IconButton(
            onClick = onToggleFullScreen,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = "Pantalla Completa",
                tint = Color.White
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
