package com.kybers.play.ui.components

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
 * Modern, unified smart category list component that provides:
 * - Accordion behavior (one category open at a time)
 * - Sticky positioning with intelligent scrolling
 * - Smooth animations
 * - Support for different content types (TV, Movies, Series)
 * - Real-time playback indicators
 * - Grid and List display modes
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
    val categoryManager = GlobalCategoryStateManager.instance
    val categoryState by categoryManager.state.collectAsState()

    // Auto-scroll when categories are toggled
    LaunchedEffect(categoryState.getExpandedCategoryId(screenType)) {
        val expandedCategoryId = categoryState.getExpandedCategoryId(screenType)
        if (expandedCategoryId != null) {
            val categoryIndex = categories.indexOfFirst { it.categoryId == expandedCategoryId }
            if (categoryIndex != -1) {
                lazyListState.animateScrollToItem(categoryIndex)
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        categories.forEachIndexed { index, categoryData ->
            val categoryStateData = categoryState.getCategoriesForScreen(screenType)[categoryData.categoryId]
                ?: CategoryState(
                    id = categoryData.categoryId,
                    name = categoryData.categoryName,
                    itemCount = categoryData.items.size,
                    hasActiveContent = categoryData.items.any { item ->
                        activeContentId != null && getItemId(item).toString() == activeContentId
                    }
                )

            // Sticky header for category
            stickyHeader(key = categoryData.categoryId) {
                SmartCategoryHeader(
                    categoryState = categoryStateData,
                    screenType = screenType,
                    onHeaderClick = { 
                        onCategoryToggled(categoryData.categoryId)
                    }
                )
            }

            // Category content with smooth animation
            if (categoryStateData.isExpanded) {
                if (displayMode == DisplayMode.GRID && screenType != ScreenType.CHANNELS) {
                    // Grid mode for movies/series
                    val itemRows = categoryData.items.chunked(gridColumns)
                    itemsIndexed(
                        items = itemRows,
                        key = { rowIndex, _ -> "${categoryData.categoryId}-row-$rowIndex" }
                    ) { _, rowItems ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        itemContent(item)
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
                    // List mode or channels
                    items(
                        items = categoryData.items,
                        key = { item -> "${categoryData.categoryId}-${getItemId(item)}" }
                    ) { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
                        ) {
                            itemContent(item)
                        }
                    }
                }
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
 * Helper function to get item ID - can be overridden for different types
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