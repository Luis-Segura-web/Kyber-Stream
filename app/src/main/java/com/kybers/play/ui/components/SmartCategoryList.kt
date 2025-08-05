package com.kybers.play.ui.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kybers.play.ui.components.categories.*
import com.kybers.play.ui.components.DisplayMode

/**
 * Modern, unified smart category list component with performance optimizations that provides:
 * - Accordion behavior (one category open at a time)
 * - Sticky positioning with intelligent scrolling
 * - Smooth animations
 * - Support for different content types (TV, Movies, Series)
 * - Real-time playback indicators
 * - Grid and List display modes
 * - Optimized recomposition and memory management
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
    val DEBUG = false // Set to true for performance debugging
    
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

    // Auto-scroll when categories are toggled with debouncing
    LaunchedEffect(categories.map { "${it.categoryId}:${it.isExpanded}" }.joinToString()) {
        val expandedCategory = categories.firstOrNull { it.isExpanded }
        if (expandedCategory != null) {
            val categoryIndex = categories.indexOfFirst { it.categoryId == expandedCategory.categoryId }
            if (categoryIndex != -1) {
                if (DEBUG) Log.d(TAG, "Auto-scrolling to category index: $categoryIndex")
                lazyListState.animateScrollToItem(categoryIndex)
            }
        }
    }

    // Performance monitoring
    LaunchedEffect(categories.size) {
        if (DEBUG) {
            Log.d(TAG, "Rendering ${categories.size} categories for $screenType")
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
                            if (DEBUG) Log.d(TAG, "Category toggled: ${categoryData.categoryId}")
                            onCategoryToggled(categoryData.categoryId)
                        }
                    )
                }
            }

            // Category content with smooth animation and performance optimization
            if (categoryStateData.isExpanded) {
                if (displayMode == DisplayMode.GRID && screenType != ScreenType.CHANNELS) {
                    // Grid mode for movies/series with optimized chunking
                    val itemRows = categoryData.items.chunked(gridColumns)
                    
                    itemsIndexed(
                        items = itemRows,
                        key = { rowIndex, _ -> "grid_${categoryData.categoryId}_row_$rowIndex" }
                    ) { rowIndex, rowItems ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
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
                    // List mode or channels with optimized item rendering
                    items(
                        items = categoryData.items,
                        key = { item -> "list_${categoryData.categoryId}_${getItemId(item)}" }
                    ) { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
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