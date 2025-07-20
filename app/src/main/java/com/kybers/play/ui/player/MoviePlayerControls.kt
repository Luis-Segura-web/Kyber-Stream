package com.kybers.play.ui.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import java.util.concurrent.TimeUnit

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
    videoTracks: List<TrackInfo>,
    showAudioMenu: Boolean,
    showSubtitleMenu: Boolean,
    showVideoMenu: Boolean,
    currentPosition: Long,
    duration: Long,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
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
            TopMovieControls(
                modifier = Modifier.align(Alignment.TopCenter),
                streamTitle = streamTitle,
                isFavorite = isFavorite,
                isFullScreen = isFullScreen,
                onClose = { onClose(); onAnyInteraction() },
                onToggleFavorite = { onToggleFavorite(); onAnyInteraction() },
                onRequestPipMode = { onRequestPipMode(); onAnyInteraction() }
            )

            CenterMovieControls(
                modifier = Modifier.align(Alignment.Center),
                isPlaying = isPlaying,
                isFullScreen = isFullScreen,
                onPlayPause = { onPlayPause(); onAnyInteraction() }
            )

            BottomMovieControls(
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
private fun TopMovieControls(
    modifier: Modifier,
    streamTitle: String,
    isFavorite: Boolean,
    isFullScreen: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestPipMode: () -> Unit
) {
    val iconSize = if (isFullScreen) 36.dp else 24.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White, modifier = Modifier.size(iconSize))
        }
        Text(
            text = streamTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(onClick = onRequestPipMode) {
                    Icon(Icons.Default.PictureInPictureAlt, "Modo Picture-in-Picture", tint = Color.White, modifier = Modifier.size(iconSize))
                }
            }
            IconButton(onClick = onToggleFavorite) {
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
private fun CenterMovieControls(
    modifier: Modifier,
    isPlaying: Boolean,
    isFullScreen: Boolean,
    onPlayPause: () -> Unit
) {
    val centerIconSize = if (isFullScreen) 96.dp else 56.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pausa", tint = Color.White, modifier = Modifier.size(centerIconSize))
        }
    }
}

@Composable
private fun BottomMovieControls(
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
            IconButton(onClick = onToggleMute) {
                Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Silenciar", tint = Color.White, modifier = Modifier.size(iconSize))
            }

            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { newPosition -> onSeek((newPosition * duration).toLong()) },
                valueRange = 0f..if (duration > 0) duration.toFloat() else 1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subtitleTracks.size > 1) {
                    TrackMenu(showMenu = showSubtitleMenu, onToggleMenu = onToggleSubtitleMenu, tracks = subtitleTracks, onSelectTrack = onSelectSubtitleTrack) {
                        ControlIconButton(icon = Icons.Default.ClosedCaption, text = "Subtítulos", onClick = { onToggleSubtitleMenu(true) }, showText = false, iconSize = iconSize)
                    }
                }
                if (audioTracks.size > 1) {
                    TrackMenu(showMenu = showAudioMenu, onToggleMenu = onToggleAudioMenu, tracks = audioTracks, onSelectTrack = onSelectAudioTrack) {
                        ControlIconButton(icon = Icons.Default.Audiotrack, text = "Audio", onClick = { onToggleAudioMenu(true) }, showText = false, iconSize = iconSize)
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

@Composable
private fun SideSliders(
    modifier: Modifier,
    volume: Int,
    maxVolume: Int,
    brightness: Float,
    isMuted: Boolean,
    onSetVolume: (Int) -> Unit,
    onSetBrightness: (Float) -> Unit
) {
    Row(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalSlider(value = brightness, onValueChange = onSetBrightness, icon = { Icon(Icons.Default.WbSunny, null, tint = Color.White) })
        VerticalSlider(value = volume.toFloat() / maxVolume.toFloat(), onValueChange = { vol -> onSetVolume((vol * maxVolume).toInt()) }, icon = { Icon(if (isMuted || volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White) })
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight(0.7f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.height(200.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .width(200.dp)
                    .rotate(-90f),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.Gray.copy(alpha = 0.5f))
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        icon()
    }
}

// ¡MEJORA VISUAL!
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
            properties = PopupProperties(focusable = true),
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.85f))
                .clip(RoundedCornerShape(8.dp))
        ) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.name) },
                    onClick = {
                        onSelectTrack(track.id)
                        onToggleMenu(false)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = Color.White,
                        trailingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    trailingIcon = { if (track.isSelected) { Icon(Icons.Default.Check, null) } }
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

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
