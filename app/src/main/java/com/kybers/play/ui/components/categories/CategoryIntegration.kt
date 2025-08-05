package com.kybers.play.ui.components.categories

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Integration helper for migrating from legacy category system to smart category system
 * This allows screens to gradually adopt the new system while maintaining backward compatibility
 */
@Composable
fun rememberSmartCategoryIntegration(
    screenType: ScreenType,
    legacyCategories: List<CategoryInfo> = emptyList()
): SmartCategoryIntegration {
    val categoryManager = GlobalCategoryStateManager.instance
    
    // Initialize categories if not already done
    LaunchedEffect(legacyCategories, screenType) {
        if (legacyCategories.isNotEmpty()) {
            val categoryPairs = legacyCategories.map { it.id to it.name }
            categoryManager.initializeCategories(screenType, categoryPairs)
        }
    }
    
    return remember(screenType) {
        SmartCategoryIntegration(categoryManager, screenType)
    }
}

/**
 * Wrapper class that provides integration between legacy and smart category systems
 */
class SmartCategoryIntegration(
    private val categoryManager: CategoryStateManager,
    private val screenType: ScreenType
) {
    val state: StateFlow<SmartCategoryState> = categoryManager.state
    
    /**
     * Handles category toggle with smart behavior (only one open at a time)
     */
    fun toggleCategory(categoryId: String) {
        categoryManager.handleEvent(
            CategoryEvent.ToggleCategory(categoryId, screenType)
        )
    }
    
    /**
     * Updates category count
     */
    fun updateCategoryCount(categoryId: String, count: Int) {
        categoryManager.handleEvent(
            CategoryEvent.UpdateCategoryCount(categoryId, count)
        )
    }
    
    /**
     * Sets active content for visual indicators
     */
    fun setActiveContent(categoryId: String, contentId: String?) {
        categoryManager.handleEvent(
            CategoryEvent.SetActiveContent(categoryId, contentId)
        )
    }
    
    /**
     * Checks if a category is expanded (for backward compatibility)
     */
    fun isCategoryExpanded(categoryId: String): Boolean {
        return categoryManager.isCategoryExpanded(screenType, categoryId)
    }
    
    /**
     * Gets all categories for this screen
     */
    @Composable
    fun getCategories(): Map<String, CategoryState> {
        val state by categoryManager.state.collectAsState()
        return state.getCategoriesForScreen(screenType)
    }
    
    /**
     * Collapses all categories
     */
    fun collapseAll() {
        categoryManager.handleEvent(CategoryEvent.CollapseAll)
    }
}

/**
 * Data class for legacy category information
 */
data class CategoryInfo(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
    val isExpanded: Boolean = false
)

/**
 * Composable hook for enhanced CategoryHeader that works with both legacy and smart systems
 */
@Composable
fun EnhancedCategoryHeader(
    categoryName: String,
    categoryId: String,
    itemCount: Int,
    screenType: ScreenType,
    onHeaderClick: () -> Unit,
    hasActiveContent: Boolean = false,
    isLegacyMode: Boolean = false,
    legacyIsExpanded: Boolean = false
) {
    if (isLegacyMode) {
        // Use backward compatible CategoryHeader
        CategoryHeader(
            categoryName = categoryName,
            isExpanded = legacyIsExpanded,
            onHeaderClick = onHeaderClick,
            itemCount = itemCount,
            hasActiveContent = hasActiveContent,
            screenType = screenType
        )
    } else {
        // Use smart category system
        val categoryManager = GlobalCategoryStateManager.instance
        val state by categoryManager.state.collectAsState()
        val categoryState = state.getCategoriesForScreen(screenType)[categoryId] ?: CategoryState(
            id = categoryId,
            name = categoryName,
            itemCount = itemCount,
            hasActiveContent = hasActiveContent
        )
        
        SmartCategoryHeader(
            categoryState = categoryState,
            screenType = screenType,
            onHeaderClick = onHeaderClick
        )
    }
}

/**
 * Helper for creating category IDs from names
 */
fun createCategoryId(categoryName: String): String {
    return categoryName.lowercase()
        .replace(" ", "_")
        .replace("ñ", "n")
        .replace(Regex("[^a-z0-9_]"), "")
}

/**
 * Migration utility to convert legacy category data to smart category format
 */
object CategoryMigrationUtils {
    
    /**
     * Converts legacy expandable categories to smart category format
     */
    fun migrateExpandableCategories(
        legacyCategories: List<Any>, // Generic to avoid coupling with specific types
        getName: (Any) -> String,
        getId: (Any) -> String,
        getItemCount: (Any) -> Int,
        isExpanded: (Any) -> Boolean
    ): List<CategoryInfo> {
        return legacyCategories.map { category ->
            CategoryInfo(
                id = getId(category),
                name = getName(category),
                itemCount = getItemCount(category),
                isExpanded = isExpanded(category)
            )
        }
    }
    
    /**
     * Creates a favorites category if it doesn't exist
     */
    fun ensureFavoritesCategory(categories: List<CategoryInfo>): List<CategoryInfo> {
        val hasFavorites = categories.any { 
            it.name.contains("favoritos", ignoreCase = true) ||
            it.name.contains("favorites", ignoreCase = true)
        }
        
        return if (!hasFavorites) {
            listOf(
                CategoryInfo(
                    id = "favorites",
                    name = "Favoritos",
                    itemCount = 0,
                    isExpanded = false
                )
            ) + categories
        } else {
            categories
        }
    }
}