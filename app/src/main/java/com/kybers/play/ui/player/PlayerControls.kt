package com.kybers.play.ui.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kybers.play.ui.channels.TrackInfo
import java.util.concurrent.TimeUnit

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
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
    onSetVolume: (Int) -> Unit,
    onSetBrightness: (Float) -> Unit,
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
    onAnyInteraction: () -> Unit
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = isVisible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200))
    ) {
        Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f))) {

            TopControls(
                modifier = Modifier.align(Alignment.TopCenter),
                streamTitle = streamTitle,
                isFavorite = isFavorite,
                isFullScreen = isFullScreen,
                onClose = onClose,
                onToggleFavorite = onToggleFavorite,
                onPictureInPicture = onPictureInPicture,
                onAnyInteraction = onAnyInteraction
            )

            CenterControls(
                modifier = Modifier.align(Alignment.Center),
                isPlaying = isPlaying,
                isFullScreen = isFullScreen, // Pasa isFullScreen para ajustar tamaño
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onAnyInteraction = onAnyInteraction
            )

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
                onToggleMute = onToggleMute,
                onToggleAudioMenu = onToggleAudioMenu,
                onToggleSubtitleMenu = onToggleSubtitleMenu,
                onToggleVideoMenu = onToggleVideoMenu,
                onSelectAudioTrack = onSelectAudioTrack,
                onSelectSubtitleTrack = onSelectSubtitleTrack,
                onSelectVideoTrack = onSelectVideoTrack,
                currentPosition = currentPosition,
                duration = duration,
                onSeek = onSeek,
                onAnyInteraction = onAnyInteraction,
                onToggleFullScreen = onToggleFullScreen,
                onToggleAspectRatio = onToggleAspectRatio
            )

            if (isFullScreen) {
                SideSliders(
                    modifier = Modifier.fillMaxSize(),
                    volume = systemVolume,
                    maxVolume = maxSystemVolume,
                    brightness = screenBrightness,
                    isMuted = isMuted,
                    onSetVolume = onSetVolume,
                    onSetBrightness = onSetBrightness,
                    onAnyInteraction = onAnyInteraction
                )
            }
        }
    }
}

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    showText: Boolean,
    tint: Color = Color.White
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
        if (showText) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, color = tint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TopControls(
    modifier: Modifier,
    streamTitle: String,
    isFavorite: Boolean,
    isFullScreen: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPictureInPicture: () -> Unit,
    onAnyInteraction: () -> Unit
) {
    val iconSize = if (isFullScreen) 36.dp else 24.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onClose(); onAnyInteraction() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White, modifier = Modifier.size(iconSize))
        }
        Text(
            text = streamTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(onClick = { onPictureInPicture() }) { // No reiniciar temporizador
                    Icon(Icons.Default.PictureInPictureAlt, "Modo Picture-in-Picture", tint = Color.White, modifier = Modifier.size(iconSize))
                }
            }
            IconButton(onClick = { onToggleFavorite(); onAnyInteraction() }) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorito",
                    tint = if (isFavorite) Color.Red else Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun CenterControls(
    modifier: Modifier,
    isPlaying: Boolean,
    isFullScreen: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAnyInteraction: () -> Unit
) {
    // Tamaños de iconos y espaciado duplicados para fullscreen
    val iconSize = if (isFullScreen) 96.dp else 40.dp // Doble de 48dp
    val centerIconSize = if (isFullScreen) 128.dp else 56.dp // Doble de 64dp
    val spacerWidth = if (isFullScreen) 64.dp else 32.dp // Doble de 32dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFullScreen) Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { onPrevious(); onAnyInteraction() }) {
            Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(iconSize))
        }

        Spacer(modifier = Modifier.width(spacerWidth))

        IconButton(onClick = { onPlayPause(); onAnyInteraction() }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pausa", tint = Color.White, modifier = Modifier.size(centerIconSize))
        }

        Spacer(modifier = Modifier.width(spacerWidth))

        IconButton(onClick = { onNext(); onAnyInteraction() }) {
            Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(iconSize))
        }
        if (isFullScreen) Spacer(modifier = Modifier.weight(1f))
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
    onAnyInteraction: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleAspectRatio: () -> Unit
) {
    val iconSize = if (isFullScreen) 36.dp else 24.dp
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
            IconButton(onClick = { onToggleMute(); onAnyInteraction() }) {
                Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Silenciar", tint = Color.White, modifier = Modifier.size(iconSize))
            }

            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { progress ->
                    onAnyInteraction()
                    onSeek((progress * duration).toLong())
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
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

            if (!isFullScreen) { // Controles en Portrait
                IconButton(onClick = { onToggleAspectRatio(); onAnyInteraction() }) {
                    Icon(Icons.Default.AspectRatio, "Relación de Aspecto", tint = Color.White, modifier = Modifier.size(iconSize))
                }
                IconButton(onClick = { onToggleFullScreen(); onAnyInteraction() }) {
                    Icon(Icons.Default.Fullscreen, "Pantalla Completa", tint = Color.White, modifier = Modifier.size(iconSize))
                }
            } else { // Controles adicionales en Landscape/Fullscreen
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp), // Espaciado entre iconos
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Orden de izquierda a derecha para la visualización
                    // Subtítulos
                    if (subtitleTracks.size > 1) { // Muestra si hay más de 1 pista (incluyendo "none" o "deshabilitada")
                        TrackMenu(showMenu = showSubtitleMenu, onToggleMenu = { show -> onToggleSubtitleMenu(show); onAnyInteraction() }, tracks = subtitleTracks, onSelectTrack = { trackId -> onSelectSubtitleTrack(trackId); onAnyInteraction() }) {
                            ControlIconButton(icon = Icons.Default.ClosedCaption, text = "Subtítulos", onClick = { onToggleSubtitleMenu(true); onAnyInteraction() }, showText = false, iconSize = iconSize)
                        }
                    }

                    // Audio Tracks
                    if (audioTracks.size > 1) { // Muestra si hay más de 1 pista (incluyendo "none" o "deshabilitada")
                        TrackMenu(showMenu = showAudioMenu, onToggleMenu = { show -> onToggleAudioMenu(show); onAnyInteraction() }, tracks = audioTracks, onSelectTrack = { trackId -> onSelectAudioTrack(trackId); onAnyInteraction() }) {
                            ControlIconButton(icon = Icons.Default.Audiotrack, text = "Audio", onClick = { onToggleAudioMenu(true); onAnyInteraction() }, showText = false, iconSize = iconSize)
                        }
                    }

                    // Calidad (Video Tracks)
                    if (videoTracks.size > 1) { // Muestra si hay más de 1 pista (incluyendo "none" o "deshabilitada")
                        TrackMenu(showMenu = showVideoMenu, onToggleMenu = { show -> onToggleVideoMenu(show); onAnyInteraction() }, tracks = videoTracks, onSelectTrack = { trackId -> onSelectVideoTrack(trackId); onAnyInteraction() }) {
                            ControlIconButton(icon = Icons.Default.Hd, text = "Calidad", onClick = { onToggleVideoMenu(true); onAnyInteraction() }, showText = false, iconSize = iconSize)
                        }
                    }

                    // Relación de Aspecto
                    IconButton(onClick = { onToggleAspectRatio(); onAnyInteraction() }) {
                        Icon(Icons.Default.AspectRatio, "Relación de Aspecto", tint = Color.White, modifier = Modifier.size(iconSize))
                    }

                    // Salir de Pantalla Completa (Extremo derecho)
                    IconButton(onClick = { onToggleFullScreen(); onAnyInteraction() }) {
                        Icon(Icons.Default.FullscreenExit, "Salir de Pantalla Completa", tint = Color.White, modifier = Modifier.size(iconSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun SideSliders(
    modifier: Modifier,
    volume: Int,
    maxVolume: Int,
    brightness: Float,
    isMuted: Boolean,
    onSetVolume: (Int) -> Unit,
    onSetBrightness: (Float) -> Unit,
    onAnyInteraction: () -> Unit
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalSlider(value = brightness, onValueChange = { br -> onSetBrightness(br); onAnyInteraction() }, icon = { Icon(Icons.Default.WbSunny, null, tint = Color.White) })
        VerticalSlider(value = volume.toFloat() / maxVolume.toFloat(), onValueChange = { vol -> onSetVolume((vol * maxVolume).toInt()); onAnyInteraction() }, icon = { Icon(if (isMuted || volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White) })
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.height(180.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(180.dp).rotate(-90f),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.Gray.copy(alpha = 0.5f))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        icon()
    }
}

@Composable
private fun TrackMenu(
    showMenu: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    tracks: List<TrackInfo>,
    onSelectTrack: (Int) -> Unit,
    icon: @Composable () -> Unit
) {
    Box {
        Box(modifier = Modifier.clickable { onToggleMenu(true) }) {
            icon()
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onToggleMenu(false) },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.name, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { onSelectTrack(track.id) },
                    trailingIcon = { if (track.isSelected) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } }
                )
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
