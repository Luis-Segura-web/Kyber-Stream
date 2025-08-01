package com.kybers.play.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.kybers.play.ui.theme.*

/**
 * Bottom Navigation mejorada con animaciones y tema azul
 */
@Composable
fun EnhancedBottomNavigation(
    navController: NavController,
    currentDestination: NavDestination?,
    items: List<NavigationItem>,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = BlueUIColors.NavigationBackground,
        tonalElevation = DesignTokens.Elevation.navigationBar,
        windowInsets = WindowInsets.navigationBars
    ) {
        items.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { 
                it.route == item.route 
            } == true
            
            NavigationBarItem(
                icon = {
                    AnimatedNavigationIcon(
                        icon = item.icon,
                        selectedIcon = item.selectedIcon ?: item.icon,
                        isSelected = isSelected,
                        contentDescription = item.title
                    )
                },
                label = { 
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BlueUIColors.NavigationSelected,
                    unselectedIconColor = BlueUIColors.NavigationUnselected,
                    selectedTextColor = BlueUIColors.NavigationSelected,
                    unselectedTextColor = BlueUIColors.NavigationUnselected,
                    indicatorColor = BlueTheme.Primary.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
private fun AnimatedNavigationIcon(
    icon: ImageVector,
    selectedIcon: ImageVector,
    isSelected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ), label = "nav_icon_scale"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Indicator background
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BlueTheme.Primary.copy(alpha = 0.15f))
            )
        }
        
        // Icon
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

/**
 * Data class para elementos de navegación
 */
data class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
    val badgeCount: Int? = null
)