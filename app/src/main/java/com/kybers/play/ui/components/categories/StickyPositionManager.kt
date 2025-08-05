package com.kybers.play.ui.components.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Helper functions for implementing sticky category headers in lazy lists
 */

/**
 * Extension function to add a sticky category header to a LazyColumn
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.smartCategorySection(
    categoryState: CategoryState,
    screenType: ScreenType,
    onToggle: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    // Sticky header
    stickyHeader(key = "header_${categoryState.id}") {
        SmartCategoryHeader(
            categoryState = categoryState,
            screenType = screenType,
            onHeaderClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        )
    }
    
    // Content items (only if expanded)
    if (categoryState.isExpanded) {
        content()
    }
}

/**
 * Extension function for managing category sections with smart behavior
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.smartCategoryList(
    categories: Map<String, CategoryState>,
    screenType: ScreenType,
    onCategoryToggle: (String) -> Unit,
    itemContent: LazyListScope.(String, CategoryState) -> Unit
) {
    categories.forEach { (categoryId, categoryState) ->
        if (categoryState.isVisible) {
            smartCategorySection(
                categoryState = categoryState,
                screenType = screenType,
                onToggle = { onCategoryToggle(categoryId) }
            ) {
                itemContent(categoryId, categoryState)
            }
        }
    }
}

/**
 * Composable for managing scroll position and category visibility
 */
@Composable
fun rememberCategoryScrollManager(
    listState: LazyListState,
    categories: Map<String, CategoryState>
): CategoryScrollManager {
    return remember(listState, categories) {
        CategoryScrollManager(listState, categories)
    }
}

/**
 * Helper class for managing category scroll behavior
 */
class CategoryScrollManager(
    private val listState: LazyListState,
    private val categories: Map<String, CategoryState>
) {
    
    /**
     * Scrolls to a specific category when it's expanded
     */
    suspend fun scrollToCategory(categoryId: String) {
        val categoryIndex = getCategoryIndex(categoryId)
        if (categoryIndex >= 0) {
            listState.animateScrollToItem(categoryIndex)
        }
    }
    
    /**
     * Gets the index of a category in the list
     */
    private fun getCategoryIndex(categoryId: String): Int {
        var index = 0
        for ((id, category) in categories) {
            if (id == categoryId) {
                return index
            }
            index++ // Header
            if (category.isExpanded) {
                index += category.itemCount // Items
            }
        }
        return -1
    }
    
    /**
     * Gets the currently visible category based on scroll position
     */
    fun getCurrentVisibleCategory(): String? {
        val firstVisibleIndex = listState.firstVisibleItemIndex
        var currentIndex = 0
        
        for ((categoryId, category) in categories) {
            if (currentIndex == firstVisibleIndex) {
                return categoryId
            }
            currentIndex++ // Header
            if (category.isExpanded) {
                if (firstVisibleIndex < currentIndex + category.itemCount) {
                    return categoryId
                }
                currentIndex += category.itemCount
            }
        }
        
        return null
    }
}

/**
 * Utility for creating properly ordered category lists
 */
object CategoryOrdering {
    
    /**
     * Orders categories according to smart rules:
     * 1. Favorites first (always visible)
     * 2. Recent second
     * 3. Other categories alphabetically
     */
    fun orderCategories(categories: Map<String, CategoryState>): List<Pair<String, CategoryState>> {
        return categories.toList().sortedWith { (_, a), (_, b) ->
            when {
                a.iconType == CategoryIconType.FAVORITES -> -1
                b.iconType == CategoryIconType.FAVORITES -> 1
                a.iconType == CategoryIconType.RECENT -> -1
                b.iconType == CategoryIconType.RECENT -> 1
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }
    }
    
    /**
     * Ensures favorites category is always present
     */
    fun ensureFavoritesCategory(categories: Map<String, CategoryState>): Map<String, CategoryState> {
        val hasFavorites = categories.values.any { it.iconType == CategoryIconType.FAVORITES }
        
        return if (!hasFavorites) {
            categories + ("favorites" to CategoryState(
                id = "favorites",
                name = "Favoritos",
                iconType = CategoryIconType.FAVORITES,
                isVisible = true,
                itemCount = 0
            ))
        } else {
            categories
        }
    }
}