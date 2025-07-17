package com.kybers.play.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kybers.play.ui.channels.TrackInfo

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
    onSelectVideoTrack: (Int) -> Unit
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f))) {

            TopControls(
                modifier = Modifier.align(Alignment.TopCenter),
                streamTitle = streamTitle,
                isFavorite = isFavorite,
                isFullScreen = isFullScreen,
                onClose = onClose,
                onToggleFavorite = onToggleFavorite,
                onToggleFullScreen = onToggleFullScreen
            )

            CenterControls(
                modifier = Modifier.align(Alignment.Center),
                isPlaying = isPlaying,
                isFullScreen = isFullScreen,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
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
                onSelectVideoTrack = onSelectVideoTrack
            )

            if (isFullScreen) {
                SideSliders(
                    modifier = Modifier.fillMaxSize(),
                    volume = systemVolume,
                    maxVolume = maxSystemVolume,
                    brightness = screenBrightness,
                    isMuted = isMuted,
                    onSetVolume = onSetVolume,
                    onSetBrightness = onSetBrightness
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
    onToggleFullScreen: () -> Unit
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
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorito",
                    tint = if (isFavorite) Color.Red else Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onToggleFullScreen) {
                Icon(
                    if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    "Pantalla Completa",
                    tint = Color.White,
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
    onPrevious: () -> Unit
) {
    val iconSize = if (isFullScreen) 48.dp else 40.dp
    val centerIconSize = if (isFullScreen) 64.dp else 56.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(iconSize)) }
        IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pausa", tint = Color.White, modifier = Modifier.size(centerIconSize)) }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(iconSize)) }
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
    onSelectVideoTrack: (Int) -> Unit
) {
    val iconSize = if (isFullScreen) 36.dp else 24.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = if (isFullScreen) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFullScreen) {
            ControlIconButton(icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, text = "Silencio", onClick = onToggleMute, showText = true, iconSize = iconSize)
            Spacer(modifier = Modifier.width(24.dp))
            if (audioTracks.size > 1) {
                TrackMenu(showMenu = showAudioMenu, onToggleMenu = onToggleAudioMenu, tracks = audioTracks, onSelectTrack = onSelectAudioTrack) {
                    ControlIconButton(icon = Icons.Default.Audiotrack, text = "Audio", onClick = { onToggleAudioMenu(true) }, showText = true, iconSize = iconSize)
                }
                Spacer(modifier = Modifier.width(24.dp))
            }
            if (subtitleTracks.isNotEmpty()) {
                TrackMenu(showMenu = showSubtitleMenu, onToggleMenu = onToggleSubtitleMenu, tracks = subtitleTracks, onSelectTrack = onSelectSubtitleTrack) {
                    ControlIconButton(icon = Icons.Default.ClosedCaption, text = "Subtítulos", onClick = { onToggleSubtitleMenu(true) }, showText = true, iconSize = iconSize)
                }
                Spacer(modifier = Modifier.width(24.dp))
            }
            if (videoTracks.size > 1) {
                TrackMenu(showMenu = showVideoMenu, onToggleMenu = onToggleVideoMenu, tracks = videoTracks, onSelectTrack = onSelectVideoTrack) {
                    ControlIconButton(icon = Icons.Default.Hd, text = "Calidad", onClick = { onToggleVideoMenu(true) }, showText = true, iconSize = iconSize)
                }
            }
        } else {
            IconButton(onClick = onToggleMute) {
                Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Silenciar", tint = Color.White, modifier = Modifier.size(iconSize))
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
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalSlider(value = brightness, onValueChange = onSetBrightness, icon = { Icon(Icons.Default.WbSunny, null, tint = Color.White) })
        VerticalSlider(value = volume.toFloat() / maxVolume.toFloat(), onValueChange = { onSetVolume((it * maxVolume).toInt()) }, icon = { Icon(if (isMuted || volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White) })
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
