package com.kybers.play.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute // Ya importado, solo para referencia
import androidx.compose.material.icons.automirrored.filled.VolumeUp // Ya importado, solo para referencia
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Un Composable que muestra los controles de reproducción sobre el video.
 *
 * @param modifier Modificador para aplicar a la Box contenedora.
 * @param player La instancia de ExoPlayer.
 * @param controlsVisible Estado que indica si los controles deben ser visibles.
 * @param onControlsVisibilityChanged Callback para notificar cambios en la visibilidad de los controles.
 * @param isFullScreen Booleano que indica si el reproductor está en modo pantalla completa.
 * @param onToggleFullScreen Callback para alternar el modo pantalla completa.
 * @param channelName El nombre del canal que se está reproduciendo actualmente.
 * @param onPreviousChannel Callback para ir al canal anterior en la lista.
 * @param onNextChannel Callback para ir al canal siguiente en la lista.
 * @param isFavorite Booleano que indica si el canal actual es favorito.
 * @param onToggleFavorite Callback para alternar el estado de favorito del canal.
 * @param isTvChannel Booleano que indica si el stream actual es un canal de TV (para ocultar progreso).
 * @param onToggleResizeMode Callback para alternar el modo de ajuste de pantalla.
 */
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    player: Player,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    channelName: String?,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    isTvChannel: Boolean = false,
    onToggleResizeMode: () -> Unit // Callback para el ajuste de pantalla
) {
    val isPlaying by remember { derivedStateOf { player.isPlaying } }
    val context = LocalContext.current

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    var currentDeviceVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var currentBrightness by remember { mutableFloatStateOf(getSystemBrightness(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            val newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (newVolume != currentDeviceVolume) {
                currentDeviceVolume = newVolume
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            onControlsVisibilityChanged(false)
        }
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = controlsVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            // Parte superior: Nombre del canal y botón de favorito
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (channelName != null) {
                    Text(
                        text = channelName,
                        color = Color.White,
                        fontSize = 20.sp,
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

            // Controles centrales (Play/Pausa, Siguiente/Anterior, FastRewind/Forward)
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousChannel) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Canal Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
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
                IconButton(onClick = onNextChannel) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Canal Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Controles inferiores (Barra de progreso, tiempos, pantalla completa, brillo, volumen)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra de progreso y tiempos (solo si NO es un canal de TV)
                if (!isTvChannel) {
                    BottomProgressBar(
                        modifier = Modifier.fillMaxWidth(),
                        player = player
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Controles de volumen y brillo (solo en pantalla completa)
                    if (isFullScreen) {
                        // Botón Mute
                        IconButton(onClick = {
                            if (currentDeviceVolume > 0) {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                            } else {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume / 2, 0)
                            }
                            currentDeviceVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }) {
                            // ¡CORREGIDO! Usando AutoMirrored para VolumeUp/VolumeMute
                            Icon(
                                imageVector = if (currentDeviceVolume > 0) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                                contentDescription = "Mute/Unmute",
                                tint = Color.White
                            )
                        }
                        // Slider de Volumen
                        Slider(
                            value = currentDeviceVolume.toFloat(),
                            onValueChange = { newVolumeFloat ->
                                val newVolumeInt = newVolumeFloat.toInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolumeInt, 0)
                                currentDeviceVolume = newVolumeInt
                            },
                            valueRange = 0f..maxVolume.toFloat(),
                            steps = maxVolume,
                            modifier = Modifier.weight(0.4f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))

                        // Slider de Brillo
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = "Brillo",
                            tint = Color.White
                        )
                        Slider(
                            value = currentBrightness,
                            onValueChange = { newBrightness ->
                                setWindowBrightness(context, newBrightness)
                                currentBrightness = newBrightness
                            },
                            valueRange = 0.01f..1f,
                            modifier = Modifier.weight(0.4f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // Botón de ajustes de audio/video (Placeholder)
                    IconButton(onClick = { /* TODO: Implementar ajustes de audio/video */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes de Audio/Video",
                            tint = Color.White
                        )
                    }

                    // Botón de subtítulos (Placeholder)
                    IconButton(onClick = { /* TODO: Implementar selección de subtítulos */ }) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Subtítulos",
                            tint = Color.White
                        )
                    }

                    // Icono de ajuste de pantalla (Aspect Ratio)
                    IconButton(onClick = onToggleResizeMode) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Ajuste de Pantalla",
                            tint = Color.White
                        )
                    }

                    // Botón de pantalla completa
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
    }
}

/**
 * Controles de la barra de progreso y tiempos.
 */
@Composable
private fun BottomProgressBar(
    modifier: Modifier = Modifier,
    player: Player
) {
    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var totalDuration by remember { mutableLongStateOf(player.duration) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player.isPlaying, player.currentPosition, player.duration) {
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

/**
 * Obtiene el brillo actual del sistema (0.0 a 1.0).
 * NOTA: Esto lee el brillo del sistema. Para cambiarlo, se necesita el permiso WRITE_SETTINGS
 * y la aprobación del usuario. La función setWindowBrightness cambia solo el brillo de la ventana.
 */
private fun getSystemBrightness(context: Context): Float {
    return try {
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        brightness / 255f // Escala de 0-255 a 0.0-1.0
    } catch (e: Settings.SettingNotFoundException) {
        e.printStackTrace()
        0.5f // Valor por defecto si no se puede obtener
    }
}

/**
 * Establece el brillo de la ventana de la aplicación (0.0 a 1.0).
 * Esto no cambia el brillo global del sistema, solo el de la ventana actual.
 */
private fun setWindowBrightness(context: Context, brightness: Float) {
    val activity = context as? Activity ?: return
    val layoutParams = activity.window.attributes
    layoutParams.screenBrightness = brightness.coerceIn(0.01f, 1f) // Asegura que esté entre 0.01 y 1.0
    activity.window.attributes = layoutParams
}
