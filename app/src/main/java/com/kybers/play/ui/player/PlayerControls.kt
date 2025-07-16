package com.kybers.play.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
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
                hasSubtitles = hasSubtitles,
                onToggleFullScreen = onToggleFullScreen,
                onShowSettings = { showSettingsDialog = true }
            )
        }
    }
}

@Composable
private fun TopControlBar(
    modifier: Modifier,
    channelName: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
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
    modifier: Modifier,
    player: Player,
    isTvChannel: Boolean,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit
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
                    contentDescription = "Play/Pausa",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
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
    modifier: Modifier,
    isFullScreen: Boolean,
    hasSubtitles: Boolean,
    onToggleFullScreen: () -> Unit,
    onShowSettings: () -> Unit
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isFullScreen) {
            IconButton(onClick = { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }) {
                Icon(
                    imageVector = if(isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Mute",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (hasSubtitles) {
            IconButton(onClick = onShowSettings) {
                Icon(Icons.Default.Subtitles, "Subtítulos", tint = Color.White)
            }
        }

        IconButton(onClick = onShowSettings) {
            Icon(Icons.Default.Settings, "Ajustes", tint = Color.White)
        }

        IconButton(onClick = onToggleFullScreen) {
            Icon(
                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = "Pantalla Completa",
                tint = Color.White
            )
        }
    }
}

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
        "FIT" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
        "STRETCH" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        "ZOOM" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
                    SettingsItem(text = name, isSelected = false) { onSetResizeMode(mode, name) }
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
