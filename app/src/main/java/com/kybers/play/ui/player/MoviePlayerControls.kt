package com.kybers.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * --- ¡ARCHIVO REFACTORIZADO Y MEJORADO! ---
 * Este composable ahora gestiona los controles para contenido VOD (Películas y Series).
 * Es más inteligente: puede mostrar botones de "siguiente/anterior" o "avanzar/retroceder".
 * Utiliza los componentes comunes de PlayerControlsCommon.kt para evitar la duplicación de código.
 */
@Composable
fun MoviePlayerControls(
    isVisible: Boolean,
    onAnyInteraction: () -> Unit,
    onRequestPipMode: () -> Unit,
    isPlaying: Boolean,
    isMuted: Boolean,
    isFavorite: Boolean,
    isFullScreen: Boolean,
    streamTitle: String,
    systemVolume: Int,
    maxSystemVolume: Int,
    screenBrightness: Float,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    showAudioMenu: Boolean,
    showSubtitleMenu: Boolean,
    currentPosition: Long,
    duration: Long,
    // --- ¡NUEVO PARÁMETRO CLAVE! ---
    // Determina qué conjunto de botones centrales mostrar.
    showNextPreviousButtons: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit, // Usado para el siguiente episodio/canal
    onPrevious: () -> Unit, // Usado para el episodio/canal anterior
    onSeekForward: () -> Unit, // Usado para avanzar 10s
    onSeekBackward: () -> Unit, // Usado para retroceder 10s
    onToggleMute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onSetBrightness: (Float) -> Unit,
    onToggleAudioMenu: (Boolean) -> Unit,
    onToggleSubtitleMenu: (Boolean) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onToggleAspectRatio: () -> Unit,
    onSeek: (Long) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            // Usamos el componente común para la barra superior
            TopControls(
                modifier = Modifier.align(Alignment.TopCenter),
                streamTitle = streamTitle,
                isFavorite = isFavorite,
                isFullScreen = isFullScreen,
                onClose = { onClose(); onAnyInteraction() },
                onToggleFavorite = { onToggleFavorite(); onAnyInteraction() },
                onRequestPipMode = { onRequestPipMode(); onAnyInteraction() }
            )

            // Controles centrales específicos para VOD
            CenterVODControls(
                modifier = Modifier.align(Alignment.Center),
                isPlaying = isPlaying,
                isFullScreen = isFullScreen,
                showNextPrevious = showNextPreviousButtons,
                onPlayPause = { onPlayPause(); onAnyInteraction() },
                onNext = { onNext(); onAnyInteraction() },
                onPrevious = { onPrevious(); onAnyInteraction() },
                onSeekForward = { onSeekForward(); onAnyInteraction() },
                onSeekBackward = { onSeekBackward(); onAnyInteraction() }
            )

            // Controles inferiores específicos para VOD
            BottomVODControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                isMuted = isMuted,
                isFullScreen = isFullScreen,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                showAudioMenu = showAudioMenu,
                showSubtitleMenu = showSubtitleMenu,
                onToggleMute = { onToggleMute(); onAnyInteraction() },
                onToggleAudioMenu = { onToggleAudioMenu(it); onAnyInteraction() },
                onToggleSubtitleMenu = { onToggleSubtitleMenu(it); onAnyInteraction() },
                onSelectAudioTrack = { onSelectAudioTrack(it); onAnyInteraction() },
                onSelectSubtitleTrack = { onSelectSubtitleTrack(it); onAnyInteraction() },
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { onSeek(it); onAnyInteraction() },
                onToggleFullScreen = { onToggleFullScreen(); onAnyInteraction() },
                onToggleAspectRatio = { onToggleAspectRatio(); onAnyInteraction() }
            )

            // Usamos el componente común para los deslizadores laterales
            if (isFullScreen) {
                SideSliders(
                    modifier = Modifier.fillMaxSize(),
                    volume = systemVolume,
                    maxVolume = maxSystemVolume,
                    brightness = screenBrightness,
                    isMuted = isMuted,
                    onSetVolume = { onSetVolume(it); onAnyInteraction() },
                    onSetBrightness = { onSetBrightness(it); onAnyInteraction() }
                )
            }
        }
    }
}

@Composable
private fun CenterVODControls(
    modifier: Modifier,
    isPlaying: Boolean,
    isFullScreen: Boolean,
    showNextPrevious: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit
) {
    val centerIconSize = if (isFullScreen) 80.dp else 56.dp
    val sideIconSize = if (isFullScreen) 64.dp else 40.dp
    val spacerWidth = if (isFullScreen) 48.dp else 24.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- ¡LÓGICA CONDICIONAL! ---
        if (showNextPrevious) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(sideIconSize))
            }
        } else {
            IconButton(onClick = onSeekBackward) {
                Icon(Icons.Default.Replay10, "Retroceder 10s", tint = Color.White, modifier = Modifier.size(sideIconSize))
            }
        }

        Spacer(modifier = Modifier.width(spacerWidth))
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pausa", tint = Color.White, modifier = Modifier.size(centerIconSize))
        }
        Spacer(modifier = Modifier.width(spacerWidth))

        // --- ¡LÓGICA CONDICIONAL! ---
        if (showNextPrevious) {
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(sideIconSize))
            }
        } else {
            IconButton(onClick = onSeekForward) {
                Icon(Icons.Default.Forward10, "Adelantar 10s", tint = Color.White, modifier = Modifier.size(sideIconSize))
            }
        }
    }
}

@Composable
private fun BottomVODControls(
    modifier: Modifier,
    isMuted: Boolean,
    isFullScreen: Boolean,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    showAudioMenu: Boolean,
    showSubtitleMenu: Boolean,
    onToggleMute: () -> Unit,
    onToggleAudioMenu: (Boolean) -> Unit,
    onToggleSubtitleMenu: (Boolean) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleAspectRatio: () -> Unit
) {
    val iconSize = if (isFullScreen) 32.dp else 24.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { newPosition -> onSeek(newPosition.toLong()) },
                valueRange = 0f..(if (duration > 0) duration.toFloat() else 0f),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Text(text = formatTime(duration), color = Color.White, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onToggleMute) {
                Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Silenciar", tint = Color.White, modifier = Modifier.size(iconSize))
            }
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
                IconButton(onClick = onToggleAspectRatio) {
                    Icon(Icons.Default.AspectRatio, "Relación de Aspecto", tint = Color.White, modifier = Modifier.size(iconSize))
                }
                IconButton(onClick = onToggleFullScreen) {
                    Icon(if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Pantalla Completa", tint = Color.White, modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}
