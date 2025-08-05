package com.kybers.play.ui.components.categories

/**
 * Represents the state of a category in the smart category system
 */
data class CategoryState(
    val id: String,
    val name: String,
    val isExpanded: Boolean = false,
    val itemCount: Int = 0,
    val hasActiveContent: Boolean = false, // Indicates if content in this category is currently playing
    val isVisible: Boolean = true,
    val iconType: CategoryIconType = CategoryIconType.FOLDER
)

/**
 * Types of icons for different categories
 */
enum class CategoryIconType {
    FOLDER,
    FAVORITES,
    RECENT,
    LIVE,
    MOVIES,
    SERIES
}

/**
 * Represents which screen is currently active
 */
enum class ScreenType {
    CHANNELS,
    MOVIES,
    SERIES
}

/**
 * Event types for category interactions
 */
sealed class CategoryEvent {
    data class ToggleCategory(val categoryId: String, val screenType: ScreenType) : CategoryEvent()
    data class SetActiveContent(val categoryId: String, val contentId: String?) : CategoryEvent()
    data class UpdateCategoryCount(val categoryId: String, val count: Int) : CategoryEvent()
    data class ResetScreen(val screenType: ScreenType) : CategoryEvent()
    object CollapseAll : CategoryEvent()
}

/**
 * State holder for the smart category system
 */
data class SmartCategoryState(
    val channelCategories: Map<String, CategoryState> = emptyMap(),
    val movieCategories: Map<String, CategoryState> = emptyMap(),
    val seriesCategories: Map<String, CategoryState> = emptyMap(),
    val activeContentId: String? = null,
    val activeCategoryId: String? = null,
    val activeScreenType: ScreenType = ScreenType.CHANNELS
) {
    
    /**
     * Gets categories for the specified screen type
     */
    fun getCategoriesForScreen(screenType: ScreenType): Map<String, CategoryState> {
        return when (screenType) {
            ScreenType.CHANNELS -> channelCategories
            ScreenType.MOVIES -> movieCategories
            ScreenType.SERIES -> seriesCategories
        }
    }
    
    /**
     * Gets the currently expanded category for the specified screen
     */
    fun getExpandedCategoryId(screenType: ScreenType): String? {
        return getCategoriesForScreen(screenType).values.firstOrNull { it.isExpanded }?.id
    }
    
    /**
     * Checks if any category is expanded for the specified screen
     */
    fun hasExpandedCategory(screenType: ScreenType): Boolean {
        return getCategoriesForScreen(screenType).values.any { it.isExpanded }
    }
}