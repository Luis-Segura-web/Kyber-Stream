package com.kybers.play.ui.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kybers.play.ui.components.categories.*
import com.kybers.play.ui.components.DisplayMode
import kotlinx.coroutines.delay

/**
 * Modern, unified smart category list component with performance optimizations that provides:
 * - Accordion behavior (one category open at a time)
 * - Sticky positioning with intelligent scrolling and collapse detection
 * - Enhanced animations with optimized durations
 * - Support for different content types (TV, Movies, Series)
 * - Real-time playback indicators
 * - Grid and List display modes
 * - Optimized recomposition and memory management
 * - Intelligent repositioning after category collapses
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> SmartCategoryList(
    categories: List<CategoryData<T>>,
    screenType: ScreenType,
    lazyListState: LazyListState = rememberLazyListState(),
    onCategoryToggled: (String) -> Unit,
    onItemClick: (T) -> Unit,
    onItemFavoriteToggle: (T) -> Unit,
    isItemFavorite: (T) -> Boolean,
    isItemSelected: (T) -> Boolean = { false },
    itemContent: @Composable (T) -> Unit,
    searchQuery: String = "",
    activeContentId: String? = null,
    displayMode: DisplayMode = DisplayMode.LIST,
    gridColumns: Int = 3,
    modifier: Modifier = Modifier
) {
    // Debug constants
    val TAG = "SmartCategoryList"
    val DEBUG = true // Enhanced debugging for expansion states and repositioning
    
    val categoryManager = GlobalCategoryStateManager.instance
    val categoryState by categoryManager.state.collectAsState()

    // Performance optimization: memoize expensive calculations
    val categoriesWithState = remember(categories, categoryState, screenType, activeContentId) {
        if (DEBUG) Log.d(TAG, "Recalculating categories with state for $screenType")
        
        categories.map { categoryData ->
            // Use the expansion state from the CategoryData (which comes from the ViewModel)
            // but enrich it with additional state from the global manager
            val globalCategoryState = categoryState.getCategoriesForScreen(screenType)[categoryData.categoryId]
            val categoryStateData = CategoryState(
                id = categoryData.categoryId,
                name = categoryData.categoryName,
                isExpanded = categoryData.isExpanded, // Use the actual expansion state from ViewModel
                itemCount = categoryData.items.size,
                hasActiveContent = categoryData.items.any { item ->
                    activeContentId != null && getItemId(item).toString() == activeContentId
                },
                iconType = globalCategoryState?.iconType ?: CategoryIconType.FOLDER
            )
            categoryData to categoryStateData
        }
    }

    // Enhanced auto-scroll with intelligent repositioning for collapsed categories
    LaunchedEffect(categories.map { "${it.categoryId}:${it.isExpanded}" }.joinToString()) {
        val expandedCategory = categories.firstOrNull { it.isExpanded }
        if (expandedCategory != null) {
            val categoryIndex = categories.indexOfFirst { it.categoryId == expandedCategory.categoryId }
            if (categoryIndex != -1) {
                if (DEBUG) Log.d(TAG, "Auto-scrolling to expanded category index: $categoryIndex (${expandedCategory.categoryName})")
                lazyListState.animateScrollToItem(categoryIndex)
            }
        }
    }
    
    // Intelligent repositioning for collapsed sticky headers
    LaunchedEffect(categoryState) {
        val collapsedCategories = categoryState.getCollapsedCategoriesForScreen(screenType)
        
        if (collapsedCategories.isNotEmpty()) {
            if (DEBUG) {
                Log.d(TAG, "Detected ${collapsedCategories.size} collapsed categories for repositioning: $collapsedCategories")
            }
            
            // Wait for collapse animation to complete before repositioning
            delay(150) // 150ms delay as specified in requirements
            
            // Find the first collapsed category that exists in our current categories list
            val repositionCategoryId = collapsedCategories.firstOrNull { collapsedId ->
                categories.any { it.categoryId == collapsedId }
            }
            
            if (repositionCategoryId != null) {
                val repositionIndex = categories.indexOfFirst { it.categoryId == repositionCategoryId }
                if (repositionIndex != -1) {
                    if (DEBUG) {
                        Log.d(TAG, "Intelligent repositioning to collapsed category index: $repositionIndex ($repositionCategoryId)")
                    }
                    
                    // Animate to the original position of the collapsed category
                    lazyListState.animateScrollToItem(repositionIndex)
                    
                    // Clear the collapsed categories list to prevent repeated repositioning
                    categoryManager.clearCollapsedCategories(screenType)
                }
            }
        }
    }

    // Enhanced performance monitoring and logging
    LaunchedEffect(categories.size) {
        if (DEBUG) {
            Log.d(TAG, "Rendering ${categories.size} categories for $screenType")
            Log.d(TAG, "Expanded categories: ${categories.filter { it.isExpanded }.map { "${it.categoryName}(${it.categoryId})" }}")
        }
    }
    
    // Log state changes for debugging
    LaunchedEffect(categoryState) {
        if (DEBUG) {
            val expandedCategory = categoryState.getExpandedCategoryId(screenType)
            val collapsedCategories = categoryState.getCollapsedCategoriesForScreen(screenType)
            
            Log.d(TAG, "State update for $screenType - Expanded: $expandedCategory, Collapsed for repositioning: $collapsedCategories")
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        categoriesWithState.forEachIndexed { index, (categoryData, categoryStateData) ->
            // Sticky header for category with optimized key
            stickyHeader(key = "header_${categoryData.categoryId}") {
                // Performance optimization: only recompose header when state changes
                key(categoryStateData.isExpanded, categoryStateData.hasActiveContent, categoryStateData.itemCount) {
                    SmartCategoryHeader(
                        categoryState = categoryStateData,
                        screenType = screenType,
                        onHeaderClick = { 
                            if (DEBUG) {
                                Log.d(TAG, "Category header clicked: ${categoryData.categoryName}(${categoryData.categoryId}) - Current state: ${if (categoryStateData.isExpanded) "expanded" else "collapsed"}")
                                Log.d(TAG, "Will ${if (categoryStateData.isExpanded) "collapse" else "expand"} category")
                            }
                            onCategoryToggled(categoryData.categoryId)
                        }
                    )
                }
            }

            // Category content with smooth animation and performance optimization
            if (categoryStateData.isExpanded) {
                // Check if category is empty and show appropriate message
                if (categoryData.items.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            categoryId = categoryData.categoryId,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                } else if (displayMode == DisplayMode.GRID) {
                    // Grid mode - support for all screen types
                    val itemRows = categoryData.items.chunked(gridColumns)
                    
                    itemsIndexed(
                        items = itemRows,
                        key = { rowIndex, _ -> "grid_${categoryData.categoryId}_row_$rowIndex" }
                    ) { rowIndex, rowItems ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(250)) + 
                                   expandVertically(animationSpec = tween(250)) +
                                   slideInVertically(animationSpec = tween(250)) { -it / 2 },
                            exit = fadeOut(animationSpec = tween(200)) + 
                                  shrinkVertically(animationSpec = tween(200)) +
                                  slideOutVertically(animationSpec = tween(200)) { -it / 2 }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        // Performance optimization: memoize item state
                                        key(getItemId(item)) {
                                            itemContent(item)
                                        }
                                    }
                                }
                                // Fill remaining columns with spacers
                                repeat(gridColumns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // List mode for all screen types
                    items(
                        items = categoryData.items,
                        key = { item -> "list_${categoryData.categoryId}_${getItemId(item)}" }
                    ) { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(250)) + 
                                   expandVertically(animationSpec = tween(250)) +
                                   slideInVertically(animationSpec = tween(250)) { -it / 2 },
                            exit = fadeOut(animationSpec = tween(200)) + 
                                  shrinkVertically(animationSpec = tween(200)) +
                                  slideOutVertically(animationSpec = tween(200)) { -it / 2 }
                        ) {
                            // Performance optimization: memoize item state
                            key(getItemId(item), isItemSelected(item), isItemFavorite(item)) {
                                itemContent(item)
                            }
                        }
                    }
                }
            }
        }
    }

    // Performance monitoring for large lists
    LaunchedEffect(lazyListState.layoutInfo.totalItemsCount) {
        if (DEBUG) {
            val totalItems = lazyListState.layoutInfo.totalItemsCount
            if (totalItems > 100) {
                Log.w(TAG, "Large list detected: $totalItems items. Consider implementing virtualization.")
            }
        }
    }
}

/**
 * Data class representing a category with its items
 */
data class CategoryData<T>(
    val categoryId: String,
    val categoryName: String,
    val items: List<T>,
    val isExpanded: Boolean = false
)

/**
 * Helper function to get item ID - optimized for different types
 */
private fun <T> getItemId(item: T): Any {
    return when (item) {
        is com.kybers.play.data.remote.model.LiveStream -> item.streamId
        is com.kybers.play.data.remote.model.Movie -> item.streamId
        is com.kybers.play.data.remote.model.Series -> item.seriesId
        else -> item.hashCode()
    }
}

/**
 * Extension function to convert legacy expandable categories to smart category data
 * with performance optimization
 */
fun <T> List<T>.toSmartCategoryData(
    getCategoryId: (T) -> String,
    getCategoryName: (T) -> String,
    getItems: (T) -> List<Any>,
    isExpanded: (T) -> Boolean
): List<CategoryData<Any>> {
    return this.map { expandableCategory ->
        CategoryData(
            categoryId = getCategoryId(expandableCategory),
            categoryName = getCategoryName(expandableCategory),
            items = getItems(expandableCategory),
            isExpanded = isExpanded(expandableCategory)
        )
    }
}

/**
 * Empty state message component for specific categories
 */
@Composable
private fun EmptyStateMessage(
    categoryId: String,
    modifier: Modifier = Modifier
) {
    val message = when (categoryId) {
        "favorites" -> "No has agregado ninguna serie a favoritos aún.\nExplora y marca tus series preferidas tocando el ❤️."
        "continue_watching" -> "No tienes series en progreso.\nComienza a ver alguna serie para que aparezca aquí."
        else -> "Esta categoría está vacía por el momento."
    }
    
    val icon = when (categoryId) {
        "favorites" -> Icons.Default.FavoriteBorder
        "continue_watching" -> Icons.Default.PlayArrow
        else -> Icons.Default.Info
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}