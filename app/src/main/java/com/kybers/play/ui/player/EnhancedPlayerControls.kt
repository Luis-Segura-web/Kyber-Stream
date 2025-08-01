package com.kybers.play.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kybers.play.ui.theme.*

/**
 * Controles de reproductor modernos con tema azul elegante
 */
@Composable
fun EnhancedPlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    title: String,
    subtitle: String? = null,
    isBuffering: Boolean = false,
    volume: Float = 1f,
    isMuted: Boolean = false,
    isFullscreen: Boolean = false,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onVolumeChange: (Float) -> Unit = {},
    onMuteToggle: () -> Unit = {},
    onFullscreenToggle: () -> Unit = {},
    onBackPressed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    
    // Auto-hide controls after 3 seconds
    LaunchedEffect(isVisible) {
        if (isVisible && isPlaying) {
            kotlinx.coroutines.delay(3000)
            isVisible = false
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { isVisible = !isVisible }
                )
            }
    ) {
        // Gradient overlay background
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BlueUIColors.PlayerBackground.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                BlueUIColors.PlayerBackground.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
        }
        
        // Top controls
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            EnhancedTopControls(
                title = title,
                subtitle = subtitle,
                onBackPressed = onBackPressed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing.md)
            )
        }
        
        // Center controls
        AnimatedVisibility(
            visible = isVisible || isBuffering,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            EnhancedCenterControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext
            )
        }
        
        // Bottom controls
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            EnhancedBottomControls(
                currentPosition = currentPosition,
                duration = duration,
                volume = volume,
                isMuted = isMuted,
                isFullscreen = isFullscreen,
                showVolumeSlider = showVolumeSlider,
                onSeek = onSeek,
                onVolumeChange = onVolumeChange,
                onMuteToggle = onMuteToggle,
                onVolumeSliderToggle = { showVolumeSlider = !showVolumeSlider },
                onFullscreenToggle = onFullscreenToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing.md)
            )
        }
    }
}

@Composable
private fun EnhancedTopControls(
    title: String,
    subtitle: String?,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EnhancedPlayerButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBackPressed,
            contentDescription = "Volver"
        )
        
        Spacer(Modifier.width(DesignTokens.Spacing.md))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = DesignTokens.Alpha.medium),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EnhancedCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        onPrevious?.let {
            EnhancedPlayerButton(
                icon = Icons.Default.SkipPrevious,
                onClick = it,
                contentDescription = "Anterior",
                size = DesignTokens.Size.playerControlSize
            )
        }
        
        // Play/Pause button
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(DesignTokens.Size.playerControlSize + 8.dp),
                color = BlueUIColors.PlayerControlActive,
                strokeWidth = 3.dp
            )
        } else {
            EnhancedPlayerButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                onClick = onPlayPause,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                size = DesignTokens.Size.playerControlSize + 8.dp,
                backgroundColor = BlueUIColors.PlayerControlActive.copy(alpha = 0.9f)
            )
        }
        
        // Next button
        onNext?.let {
            EnhancedPlayerButton(
                icon = Icons.Default.SkipNext,
                onClick = it,
                contentDescription = "Siguiente",
                size = DesignTokens.Size.playerControlSize
            )
        }
    }
}

@Composable
private fun EnhancedBottomControls(
    currentPosition: Long,
    duration: Long,
    volume: Float,
    isMuted: Boolean,
    isFullscreen: Boolean,
    showVolumeSlider: Boolean,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onVolumeSliderToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Progress bar
        EnhancedProgressBar(
            currentPosition = currentPosition,
            duration = duration,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(DesignTokens.Spacing.md))
        
        // Control buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time display
            Text(
                text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume controls
                Box {
                    EnhancedPlayerButton(
                        icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        onClick = onVolumeSliderToggle,
                        contentDescription = "Volumen"
                    )
                    
                    if (showVolumeSlider) {
                        EnhancedVolumeSlider(
                            volume = volume,
                            isMuted = isMuted,
                            onVolumeChange = onVolumeChange,
                            onMuteToggle = onMuteToggle,
                            modifier = Modifier
                                .offset(y = (-80).dp)
                                .width(40.dp)
                                .height(120.dp)
                        )
                    }
                }
                
                // Fullscreen toggle
                EnhancedPlayerButton(
                    icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    onClick = onFullscreenToggle,
                    contentDescription = if (isFullscreen) "Salir de pantalla completa" else "Pantalla completa"
                )
            }
        }
    }
}

@Composable
private fun EnhancedPlayerButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = DesignTokens.Size.playerControlSize,
    backgroundColor: Color = BlueUIColors.PlayerBackground.copy(alpha = 0.6f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ), label = "button_scale"
    )
    
    Surface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                isPressed = true
                onClick()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = CircleShape,
        color = backgroundColor,
        tonalElevation = DesignTokens.Elevation.button
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
private fun EnhancedProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    
    Slider(
        value = progress,
        onValueChange = { newProgress ->
            val newPosition = (newProgress * duration).toLong()
            onSeek(newPosition)
        },
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = BlueUIColors.PlayerControlActive,
            activeTrackColor = BlueUIColors.PlayerProgressTrack,
            inactiveTrackColor = BlueUIColors.PlayerProgressBackground
        )
    )
}

@Composable
private fun EnhancedVolumeSlider(
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(DesignTokens.CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = BlueUIColors.PlayerBackground.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = DesignTokens.Elevation.modal
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DesignTokens.Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onMuteToggle) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Alternar silencio",
                    tint = Color.White
                )
            }
            
            Slider(
                value = if (isMuted) 0f else volume,
                onValueChange = onVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { rotationZ = -90f },
                colors = SliderDefaults.colors(
                    thumbColor = BlueUIColors.PlayerControlActive,
                    activeTrackColor = BlueUIColors.PlayerProgressTrack,
                    inactiveTrackColor = BlueUIColors.PlayerProgressBackground
                )
            )
        }
    }
}