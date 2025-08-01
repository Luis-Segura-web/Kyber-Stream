package com.kybers.play.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kybers.play.ui.theme.*

/**
 * Card de contenido mejorada con diseño azul elegante
 */
@Composable
fun EnhancedContentCard(
    title: String,
    imageUrl: String,
    rating: Float? = null,
    year: String? = null,
    duration: String? = null,
    isNew: Boolean = false,
    isHD: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "card_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .scale(scale)
            .clickable {
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
        shape = RoundedCornerShape(DesignTokens.CornerRadius.card),
        elevation = CardDefaults.cardElevation(
            defaultElevation = DesignTokens.Elevation.card,
            pressedElevation = DesignTokens.Elevation.lg
        ),
        colors = CardDefaults.cardColors(
            containerColor = BlueUIColors.CardBackground
        )
    ) {
        Box {
            // Imagen principal
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                BlueUIColors.CardOverlay
                            ),
                            startY = 0.4f
                        )
                    )
            )
            
            // Badges superiores
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DesignTokens.Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)
            ) {
                if (isNew) {
                    Badge(
                        containerColor = BlueUIColors.BadgeNew,
                        contentColor = Color.White
                    ) {
                        Text(
                            "NUEVO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (isHD) {
                    Badge(
                        containerColor = BlueUIColors.BadgeHD,
                        contentColor = Color.White
                    ) {
                        Text(
                            "HD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Contenido inferior
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing.md)
            ) {
                // Rating
                rating?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = DesignTokens.Spacing.xs)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = BlueUIColors.RatingGold,
                            modifier = Modifier.size(DesignTokens.Size.iconXs)
                        )
                        Spacer(Modifier.width(DesignTokens.Spacing.xs))
                        Text(
                            text = String.format("%.1f", it),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Título
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Información adicional
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
                    modifier = Modifier.padding(top = DesignTokens.Spacing.xs)
                ) {
                    year?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = DesignTokens.Alpha.medium)
                        )
                    }
                    duration?.let {
                        Text(
                            text = "• $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = DesignTokens.Alpha.medium)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Barra de búsqueda mejorada con tema azul
 */
@Composable
fun EnhancedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String = "Buscar contenido...",
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { 
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = DesignTokens.Alpha.medium)
            ) 
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Buscar",
                tint = if (isActive) BlueTheme.Primary else MaterialTheme.colorScheme.onSurface
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Limpiar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(DesignTokens.CornerRadius.xl),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BlueTheme.Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = BlueTheme.Primary
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

/**
 * Indicador de carga personalizado
 */
@Composable
fun EnhancedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = DesignTokens.Size.iconLg,
    message: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = BlueTheme.Primary,
            strokeWidth = 3.dp
        )
        
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = DesignTokens.Alpha.medium),
                fontWeight = FontWeight.Medium
            )
        }
    }
}