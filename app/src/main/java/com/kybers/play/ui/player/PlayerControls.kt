package com.kybers.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * --- ¡ARCHIVO REFACTORIZADO! ---
 * Este es el composable de controles genérico, ahora simplificado.
 * También reutiliza los componentes de PlayerControlsCommon.kt.
 */
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    isFavorite: Boolean,
    isFullScreen: Boolean,
    streamTitle: String,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    videoTracks: List<TrackInfo>,
    showAudioMenu: Boolean,
    showSubtitleMenu: Boolean,
    showVideoMenu: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleAudioMenu: (Boolean) -> Unit,
    onToggleSubtitleMenu: (Boolean) -> Unit,
    onToggleVideoMenu: (Boolean) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    onPictureInPicture: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onAnyInteraction: () -> Unit,
    // New retry parameters
    playerStatus: PlayerStatus = PlayerStatus.IDLE,
    retryAttempt: Int = 0,
    maxRetryAttempts: Int = 3,
    retryMessage: String? = null,
    onRetry: () -> Unit = {}
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 200)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200))
    ) {
        Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f))) {

            // Componente común
            TopControls(
                modifier = Modifier.align(Alignment.TopCenter),
                streamTitle = streamTitle,
                isFavorite = isFavorite,
                isFullScreen = isFullScreen,
                onClose = { onClose(); onAnyInteraction() },
                onToggleFavorite = { onToggleFavorite(); onAnyInteraction() },
                onRequestPipMode = { onPictureInPicture(); onAnyInteraction() }
            )

            // Componente específico de este archivo
            CenterControls(
                modifier = Modifier.align(Alignment.Center),
                isPlaying = isPlaying,
                isFullScreen = isFullScreen,
                playerStatus = playerStatus,
                retryAttempt = retryAttempt,
                maxRetryAttempts = maxRetryAttempts,
                retryMessage = retryMessage,
                onPlayPause = { onPlayPause(); onAnyInteraction() },
                onNext = { onNext(); onAnyInteraction() },
                onPrevious = { onPrevious(); onAnyInteraction() },
                onRetry = { onRetry(); onAnyInteraction() }
            )

            // Componente específico de este archivo
            BottomControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                isMuted = isMuted,
                isFullScreen = isFullScreen,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                videoTracks = videoTracks,
                showAudioMenu = showAudioMenu,
                showSubtitleMenu = showSubtitleMenu,
                showVideoMenu = showVideoMenu,
                onToggleMute = { onToggleMute(); onAnyInteraction() },
                onToggleAudioMenu = { onToggleAudioMenu(it); onAnyInteraction() },
                onToggleSubtitleMenu = { onToggleSubtitleMenu(it); onAnyInteraction() },
                onToggleVideoMenu = { onToggleVideoMenu(it); onAnyInteraction() },
                onSelectAudioTrack = { onSelectAudioTrack(it); onAnyInteraction() },
                onSelectSubtitleTrack = { onSelectSubtitleTrack(it); onAnyInteraction() },
                onSelectVideoTrack = { onSelectVideoTrack(it); onAnyInteraction() },
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { onSeek(it); onAnyInteraction() },
                onToggleFullScreen = { onToggleFullScreen(); onAnyInteraction() },
                onToggleAspectRatio = { onToggleAspectRatio(); onAnyInteraction() }
            )

        }
    }
}

@Composable
private fun CenterControls(
    modifier: Modifier,
    isPlaying: Boolean,
    isFullScreen: Boolean,
    playerStatus: PlayerStatus,
    retryAttempt: Int,
    maxRetryAttempts: Int,
    retryMessage: String?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRetry: () -> Unit
) {
    val iconSize = if (isFullScreen) 48.dp else 32.dp
    val centerIconSize = if (isFullScreen) 64.dp else 48.dp
    val spacerWidth = if (isFullScreen) 32.dp else 16.dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFullScreen) Spacer(modifier = Modifier.weight(1f))

            if (playerStatus != PlayerStatus.RETRYING && playerStatus != PlayerStatus.RETRY_FAILED) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(iconSize))
                }
                Spacer(modifier = Modifier.width(spacerWidth))
            }

            when (playerStatus) {
                PlayerStatus.RETRYING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Reintentando conexión... ($retryAttempt/$maxRetryAttempts)", color = Color.White)
                    }
                }
                PlayerStatus.RETRY_FAILED, PlayerStatus.ERROR -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, "Reintentar", tint = Color.White, modifier = Modifier.size(centerIconSize))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Fallo de conexión. Comprueba tu conexión a internet.", color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                PlayerStatus.LOADING -> {
                    Box(
                        modifier = Modifier.size(centerIconSize),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(centerIconSize * 0.8f)
                        )
                    }
                }
                else -> {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pausa",
                            tint = Color.White,
                            modifier = Modifier.size(centerIconSize)
                        )
                    }
                }
            }

            if (playerStatus != PlayerStatus.RETRYING && playerStatus != PlayerStatus.RETRY_FAILED) {
                Spacer(modifier = Modifier.width(spacerWidth))
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(iconSize))
                }
            }

            if (isFullScreen) Spacer(modifier = Modifier.weight(1f))
        }

        retryMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                fontSize = if (isFullScreen) 18.sp else 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -(centerIconSize / 2 + 16.dp))
            )
        }
    }
}

@Composable
private fun BottomControls(
    modifier: Modifier,
    isMuted: Boolean,
    isFullScreen: Boolean,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    videoTracks: List<TrackInfo>,
    showAudioMenu: Boolean,
    showSubtitleMenu: Boolean,
    showVideoMenu: Boolean,
    onToggleMute: () -> Unit,
    onToggleAudioMenu: (Boolean) -> Unit,
    onToggleSubtitleMenu: (Boolean) -> Unit,
    onToggleVideoMenu: (Boolean) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleAspectRatio: () -> Unit
) {
    val iconSize = if (isFullScreen) 28.dp else 24.dp
    val formattedCurrentTime = formatTime(currentPosition)
    val formattedTotalTime = formatTime(duration)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onToggleMute) {
                Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Silenciar", tint = Color.White, modifier = Modifier.size(iconSize))
            }

            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { progress ->
                    onSeek((progress * duration).toLong())
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            Text(
                text = "$formattedCurrentTime / $formattedTotalTime",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            if (!isFullScreen) {
                IconButton(onClick = onToggleAspectRatio) {
                    Icon(Icons.Default.AspectRatio, "Relación de Aspecto", tint = Color.White, modifier = Modifier.size(iconSize))
                }
                IconButton(onClick = onToggleFullScreen) {
                    Icon(Icons.Default.Fullscreen, "Pantalla Completa", tint = Color.White, modifier = Modifier.size(iconSize))
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subtitleTracks.size > 1) {
                        TrackMenu(showMenu = showSubtitleMenu, onToggleMenu = onToggleSubtitleMenu, tracks = subtitleTracks, onSelectTrack = onSelectSubtitleTrack) {
                            ControlIconButton(icon = Icons.Default.ClosedCaption, text = "Subtítulos", onClick = { onToggleSubtitleMenu(true) }, iconSize = iconSize)
                        }
                    }
                    if (audioTracks.size > 1) {
                        TrackMenu(showMenu = showAudioMenu, onToggleMenu = onToggleAudioMenu, tracks = audioTracks, onSelectTrack = onSelectAudioTrack) {
                            ControlIconButton(icon = Icons.Default.Audiotrack, text = "Audio", onClick = { onToggleAudioMenu(true) }, iconSize = iconSize)
                        }
                    }
                    if (videoTracks.size > 1) {
                        TrackMenu(showMenu = showVideoMenu, onToggleMenu = onToggleVideoMenu, tracks = videoTracks, onSelectTrack = onSelectVideoTrack) {
                            ControlIconButton(icon = Icons.Default.Hd, text = "Calidad", onClick = { onToggleVideoMenu(true) }, iconSize = iconSize)
                        }
                    }
                    IconButton(onClick = onToggleAspectRatio) {
                        Icon(Icons.Default.AspectRatio, "Relación de Aspecto", tint = Color.White, modifier = Modifier.size(iconSize))
                    }
                    IconButton(onClick = onToggleFullScreen) {
                        Icon(Icons.Default.FullscreenExit, "Salir de Pantalla Completa", tint = Color.White, modifier = Modifier.size(iconSize))
                    }
                }
            }
        }
    }
}
