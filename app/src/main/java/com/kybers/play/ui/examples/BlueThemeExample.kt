package com.kybers.play.ui.examples

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kybers.play.ui.components.*
import com.kybers.play.ui.theme.*

/**
 * Example screen showing how to integrate the new blue theme system
 * This demonstrates migration from existing components to enhanced ones
 */
@Composable
fun BlueThemeExampleScreen(
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Sample content data
    val sampleContent = remember {
        listOf(
            ContentItem(
                id = 1,
                title = "Blue Planet",
                imageUrl = "https://example.com/blue-planet.jpg",
                rating = 4.8f,
                year = "2024",
                duration = "45 min",
                isNew = true,
                isHD = true
            ),
            ContentItem(
                id = 2,
                title = "Ocean's Mystery",
                imageUrl = "https://example.com/oceans-mystery.jpg",
                rating = 4.5f,
                year = "2023",
                duration = "2h 10min",
                isNew = false,
                isHD = true
            ),
            ContentItem(
                id = 3,
                title = "Deep Blue",
                imageUrl = "https://example.com/deep-blue.jpg",
                rating = 4.2f,
                year = "2022",
                duration = "1h 30min",
                isNew = false,
                isHD = false
            )
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.screenPadding)
    ) {
        // Enhanced search bar with blue theming
        EnhancedSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { 
                isLoading = true
                // Simulate search delay
            },
            placeholder = "Buscar películas y series...",
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(DesignTokens.Spacing.sectionSpacing))
        
        // Section header using design tokens
        Text(
            text = "Contenido Destacado",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = DesignTokens.Spacing.md)
        )
        
        if (isLoading) {
            // Enhanced loading indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                EnhancedLoadingIndicator(
                    message = "Buscando contenido..."
                )
            }
        } else {
            // Content grid using enhanced cards
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
                contentPadding = PaddingValues(bottom = DesignTokens.Spacing.xl)
            ) {
                items(
                    items = sampleContent.chunked(2), // Create pairs for 2-column layout
                    key = { pair -> pair.first().id }
                ) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
                    ) {
                        pair.forEach { item ->
                            EnhancedContentCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                rating = item.rating,
                                year = item.year,
                                duration = item.duration,
                                isNew = item.isNew,
                                isHD = item.isHD,
                                onClick = {
                                    // Handle content selection
                                    println("Selected: ${item.title}")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Fill remaining space if odd number of items
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
    
    // Simulate loading completion
    LaunchedEffect(isLoading) {
        if (isLoading) {
            kotlinx.coroutines.delay(2000)
            isLoading = false
        }
    }
}

/**
 * Data class for content items
 */
data class ContentItem(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val rating: Float,
    val year: String,
    val duration: String,
    val isNew: Boolean,
    val isHD: Boolean
)

/**
 * Example of enhanced navigation integration
 */
@Composable
fun BlueThemeNavigationExample(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationItems = listOf(
        NavigationItem(
            route = "home",
            title = "Inicio",
            icon = Icons.Default.Home
        ),
        NavigationItem(
            route = "movies",
            title = "Películas",
            icon = Icons.Default.Movie
        ),
        NavigationItem(
            route = "series",
            title = "Series",
            icon = Icons.Default.Tv
        ),
        NavigationItem(
            route = "channels",
            title = "Canales",
            icon = Icons.Default.LiveTv
        ),
        NavigationItem(
            route = "settings",
            title = "Ajustes",
            icon = Icons.Default.Settings
        )
    )
    
    NavigationBar(
        modifier = modifier,
        containerColor = BlueUIColors.NavigationBackground,
        tonalElevation = DesignTokens.Elevation.navigationBar
    ) {
        navigationItems.forEach { item ->
            val isSelected = currentRoute == item.route
            
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = { 
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = isSelected,
                onClick = { onNavigate(item.route) },
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