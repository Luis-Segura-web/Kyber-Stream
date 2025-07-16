package com.kybers.play.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.kybers.play.ui.channels.ChannelsUiState
import com.kybers.play.ui.channels.TrackInfo
import kotlinx.coroutines.delay

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    player: Player,
    controlsVisible: Boolean,
    isFullScreen: Boolean,
    isTvChannel: Boolean,
    channelName: String?,
    isFavorite: Boolean,
    hasSubtitles: Boolean,
    uiState: ChannelsUiState,
    onToggleFullScreen: () -> Unit,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetResizeMode: (Int, String) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitle: (String) -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        SettingsDialog(
            uiState = uiState,
            onDismiss = { showSettingsDialog = false },
            onSetResizeMode = onSetResizeMode,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectSubtitle = onSelectSubtitle
        )
    }

    // La capa de gestos siempre está activa en pantalla completa, pero es invisible.
    // Se dibuja debajo de los controles visibles.
    if (isFullScreen) {
        GestureControlLayer(
            modifier = Modifier.fillMaxSize()
        )
    }

    // Capa de controles visibles (botones, etc.)
    AnimatedVisibility(
        modifier = modifier,
        visible = controlsVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f))) {
            TopControlBar(
                modifier = Modifier.align(Alignment.TopCenter),
                channelName = channelName,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite
            )

            CenterControls(
                modifier = Modifier.align(Alignment.Center),
                player = player,
                isTvChannel = isTvChannel,
                onPreviousChannel = onPreviousChannel,
                onNextChannel = onNextChannel
            )

            BottomControlBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                isFullScreen = isFullScreen,
                onToggleFullScreen = onToggleFullScreen,
                onShowSettings = { showSettingsDialog = true }
            )
        }
    }
}

// --- Componentes de Controles ---

@Composable
private fun TopControlBar(
    modifier: Modifier, channelName: String?, isFavorite: Boolean, onToggleFavorite: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (channelName != null) {
            Text(
                text = channelName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (isFavorite) Color.Red else Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun CenterControls(
    modifier: Modifier, player: Player, isTvChannel: Boolean, onPreviousChannel: () -> Unit, onNextChannel: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousChannel) {
            Icon(Icons.Default.SkipPrevious, "Canal Anterior", tint = Color.White, modifier = Modifier.size(48.dp))
        }

        if (!isTvChannel) {
            val isPlaying by remember { derivedStateOf { player.isPlaying } }
            IconButton(onClick = { player.seekBack() }) {
                Icon(Icons.Default.FastRewind, "Retroceder", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play/Pausa", tint = Color.White, modifier = Modifier.size(64.dp)
                )
            }
            IconButton(onClick = { player.seekForward() }) {
                Icon(Icons.Default.FastForward, "Adelantar", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        IconButton(onClick = onNextChannel) {
            Icon(Icons.Default.SkipNext, "Canal Siguiente", tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun BottomControlBar(
    modifier: Modifier, isFullScreen: Boolean, onToggleFullScreen: () -> Unit, onShowSettings: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var isMuted by remember { mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) }

        LaunchedEffect(Unit) {
            while (true) {
                isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                delay(500)
            }
        }

        if (!isFullScreen) {
            IconButton(onClick = { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }) {
                Icon(
                    imageVector = if(isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    "Mute", tint = Color.White
                )
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onShowSettings) {
            Icon(Icons.Default.Settings, "Ajustes", tint = Color.White)
        }

        IconButton(onClick = onToggleFullScreen) {
            Icon(
                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                "Pantalla Completa", tint = Color.White
            )
        }
    }
}

// --- Lógica y UI para Gestos Verticales ---

@Composable
private fun GestureControlLayer(modifier: Modifier) {
    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var currentBrightness by remember { mutableFloatStateOf(context.getCurrentBrightness()) }

    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Zona de Brillo (Izquierda)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showBrightnessIndicator = true },
                            onDragEnd = { showBrightnessIndicator = false },
                            onVerticalDrag = { _, dragAmount ->
                                val newBrightness = (currentBrightness - dragAmount / size.height).coerceIn(0.01f, 1f)
                                context.setWindowBrightness(newBrightness)
                                currentBrightness = newBrightness
                            }
                        )
                    }
            )

            // Zona central para otros gestos (doble toque, etc.)
            Box(modifier = Modifier
                .fillMaxHeight()
                .weight(1.5f))

            // Zona de Volumen (Derecha)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showVolumeIndicator = true },
                            onDragEnd = { showVolumeIndicator = false },
                            onVerticalDrag = { _, dragAmount ->
                                val delta = -dragAmount / (size.height / maxVolume)
                                val newVolume = (currentVolume + delta)
                                    .toInt()
                                    .coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                currentVolume = newVolume
                            }
                        )
                    }
            )
        }

        // Indicadores visuales que aparecen al deslizar
        VerticalSliderIndicator(
            modifier = Modifier.align(Alignment.CenterStart),
            value = currentBrightness,
            isVisible = showBrightnessIndicator,
            icon = Icons.Default.Brightness7
        )
        VerticalSliderIndicator(
            modifier = Modifier.align(Alignment.CenterEnd),
            value = currentVolume.toFloat() / maxVolume,
            isVisible = showVolumeIndicator,
            icon = Icons.Default.VolumeUp
        )
    }
}

@Composable
private fun VerticalSliderIndicator(
    modifier: Modifier, value: Float, isVisible: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.padding(horizontal = 24.dp),
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(durationMillis = 500, delayMillis = 1000))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { value },
                modifier = Modifier
                    .graphicsLayer { rotationZ = -90f }
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

// --- Funciones de Ayuda para Brillo ---

private fun Context.getCurrentBrightness(): Float {
    val activity = this as? Activity
    return activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 }
        ?: Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
}

private fun Context.setWindowBrightness(brightness: Float) {
    (this as? Activity)?.window?.let { window ->
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }
}


// --- Diálogo de Ajustes ---

@Composable
private fun SettingsDialog(
    uiState: ChannelsUiState,
    onDismiss: () -> Unit,
    onSetResizeMode: (Int, String) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitle: (String) -> Unit
) {
    val aspectRatios = listOf(
        "Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
        "Estirar" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                item {
                    Text("Ajustes del Reproductor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                }

                item { SettingsSection("Ajuste de Pantalla") }
                items(aspectRatios) { (name, mode) ->
                    SettingsItem(text = name, isSelected = false) {
                        onSetResizeMode(mode, name)
                        onDismiss()
                    }
                }

                item { SettingsSection("Velocidad") }
                items(listOf(0.5f, 1f, 1.5f, 2f)) { speed ->
                    SettingsItem(text = "${speed}x", isSelected = uiState.playbackSpeed == speed) { onSetPlaybackSpeed(speed) }
                }

                if (uiState.availableAudioTracks.size > 1) {
                    item { SettingsSection("Audio") }
                    items(uiState.availableAudioTracks) { track ->
                        SettingsItem(text = track.label, isSelected = track.isSelected) { onSelectAudioTrack(track.id) }
                    }
                }

                if (uiState.availableSubtitleTracks.isNotEmpty()) {
                    item { SettingsSection("Subtítulos") }
                    items(uiState.availableSubtitleTracks) { track ->
                        SettingsItem(text = track.label, isSelected = track.isSelected) { onSelectSubtitle(track.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
    HorizontalDivider()
}

@Composable
private fun SettingsItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f))
        if (isSelected) {
            Icon(Icons.Default.Check, "Seleccionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
