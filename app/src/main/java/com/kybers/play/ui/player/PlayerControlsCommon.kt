package com.kybers.play.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import java.util.concurrent.TimeUnit

/**
 * --- ¡ARCHIVO CON AJUSTES DE UI! ---
 * Contiene los componentes de UI comunes y reutilizables para todos los reproductores.
 * Se ha aumentado el tamaño de los deslizadores verticales.
 */

@Composable
internal fun TopControls(
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White, modifier = Modifier.size(iconSize))
        }
        
        // Title takes all available space until the first button
        Text(
            text = streamTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
internal fun TrackMenu(
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
internal fun ControlIconButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    tint: Color = Color.White
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

internal fun formatTime(millis: Long): String {
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
