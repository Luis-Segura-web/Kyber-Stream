package com.kybers.play.ui.components.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Enhanced smart category header with accordion behavior and visual indicators
 */
@Composable
fun SmartCategoryHeader(
    categoryState: CategoryState,
    screenType: ScreenType,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryManager = GlobalCategoryStateManager.instance
    
    // Animation for expand/collapse
    val rotationAngle by animateFloatAsState(
        targetValue = if (categoryState.isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "rotation"
    )
    
    // Color animation for active state
    val headerColor by animateColorAsState(
        targetValue = if (categoryState.hasActiveContent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "headerColor"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (categoryState.hasActiveContent) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "textColor"
    )
    
    // Enhanced sticky header with improved elevation and visual feedback
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = headerColor,
        tonalElevation = if (categoryState.isExpanded) 8.dp else 4.dp,
        shadowElevation = if (categoryState.isExpanded) 4.dp else 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onHeaderClick)
                .padding(vertical = 16.dp, horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dynamic icon based on category type
            CategoryIcon(
                iconType = categoryState.iconType,
                hasActiveContent = categoryState.hasActiveContent,
                isExpanded = categoryState.isExpanded
            )
            
            Spacer(modifier = Modifier.width(14.dp))
            
            // Category name and info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = categoryState.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (categoryState.hasActiveContent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Active content indicator
                    if (categoryState.hasActiveContent) {
                        Spacer(modifier = Modifier.width(8.dp))
                        ActiveContentIndicator()
                    }
                }
                
                // Item count display
                if (categoryState.itemCount > 0) {
                    Text(
                        text = "${categoryState.itemCount} elementos",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Expand/collapse indicator
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (categoryState.isExpanded) "Contraer" else "Expandir",
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotationAngle)
            )
        }
    }
}

/**
 * Dynamic category icon that changes based on type and state
 */
@Composable
private fun CategoryIcon(
    iconType: CategoryIconType,
    hasActiveContent: Boolean,
    isExpanded: Boolean
) {
    val iconVector = when (iconType) {
        CategoryIconType.FAVORITES -> Icons.Default.Star
        CategoryIconType.RECENT -> Icons.Default.History
        CategoryIconType.LIVE -> Icons.Default.LiveTv
        CategoryIconType.MOVIES -> Icons.Default.Movie
        CategoryIconType.SERIES -> Icons.Default.Slideshow
        CategoryIconType.FOLDER -> Icons.Default.Folder
    }
    
    val iconColor by animateColorAsState(
        targetValue = if (hasActiveContent) {
            MaterialTheme.colorScheme.primary
        } else if (isExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "iconColor"
    )
    
    Icon(
        imageVector = iconVector,
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.size(22.dp)
    )
}

/**
 * Animated indicator for active content
 */
@Composable
private fun ActiveContentIndicator() {
    val pulseAnimation by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

/**
 * Backward compatibility wrapper for existing CategoryHeader usage
 */
@Composable
fun CategoryHeader(
    categoryName: String,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    itemCount: Int? = null,
    modifier: Modifier = Modifier,
    hasActiveContent: Boolean = false,
    screenType: ScreenType = ScreenType.CHANNELS
) {
    val categoryState = CategoryState(
        id = categoryName.lowercase().replace(" ", "_"),
        name = categoryName,
        isExpanded = isExpanded,
        itemCount = itemCount ?: 0,
        hasActiveContent = hasActiveContent,
        iconType = when {
            categoryName.contains("favoritos", ignoreCase = true) -> CategoryIconType.FAVORITES
            categoryName.contains("recientes", ignoreCase = true) -> CategoryIconType.RECENT
            categoryName.contains("vivo", ignoreCase = true) -> CategoryIconType.LIVE
            categoryName.contains("películas", ignoreCase = true) -> CategoryIconType.MOVIES
            categoryName.contains("series", ignoreCase = true) -> CategoryIconType.SERIES
            else -> CategoryIconType.FOLDER
        }
    )
    
    SmartCategoryHeader(
        categoryState = categoryState,
        screenType = screenType,
        onHeaderClick = onHeaderClick,
        modifier = modifier
    )
}