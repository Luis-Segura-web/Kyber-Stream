package com.kybers.play.ui.components.categories

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized manager for the smart category system
 * Implements accordion-style behavior where only one category can be open at a time
 * Enhanced with sticky header collapse detection and intelligent repositioning
 */
class CategoryStateManager {
    
    companion object {
        private const val TAG = "CategoryStateManager"
        private const val DEBUG = true // Enable detailed logging for debugging
    }
    
    private val _state = MutableStateFlow(SmartCategoryState())
    val state: StateFlow<SmartCategoryState> = _state.asStateFlow()
    
    // Track previous states for collapse detection
    private var previousStates: Map<ScreenType, Map<String, CategoryState>> = emptyMap()
    
    /**
     * Handles category events and updates state accordingly
     * Enhanced with collapse detection and intelligent repositioning
     */
    fun handleEvent(event: CategoryEvent) {
        val currentState = _state.value
        
        // Store current state as previous before making changes
        storePreviousStates(currentState)
        
        when (event) {
            is CategoryEvent.ToggleCategory -> {
                val newState = handleToggleCategory(currentState, event.categoryId, event.screenType)
                _state.value = newState
                
                // Detect collapses and trigger repositioning if needed
                detectAndHandleCollapses(event.screenType, event.categoryId)
            }
            
            is CategoryEvent.SetActiveContent -> {
                _state.value = currentState.copy(
                    activeContentId = event.contentId,
                    activeCategoryId = if (event.contentId != null) event.categoryId else null
                )
            }
            
            is CategoryEvent.UpdateCategoryCount -> {
                _state.value = handleUpdateCategoryCount(currentState, event.categoryId, event.count)
            }
            
            is CategoryEvent.ResetScreen -> {
                _state.value = handleResetScreen(currentState, event.screenType)
            }
            
            CategoryEvent.CollapseAll -> {
                _state.value = handleCollapseAll(currentState)
            }
        }
    }
    
    /**
     * Initializes categories for a specific screen
     */
    fun initializeCategories(screenType: ScreenType, categories: List<Pair<String, String>>) {
        val currentState = _state.value
        val categoryMap = categories.associate { (id, name) ->
            id to CategoryState(
                id = id,
                name = name,
                iconType = determineIconType(name),
                isVisible = true
            )
        }
        
        _state.value = when (screenType) {
            ScreenType.CHANNELS -> currentState.copy(channelCategories = categoryMap)
            ScreenType.MOVIES -> currentState.copy(movieCategories = categoryMap)
            ScreenType.SERIES -> currentState.copy(seriesCategories = categoryMap)
        }
    }
    
    /**
     * Updates the count for a specific category
     */
    fun updateCategoryCount(screenType: ScreenType, categoryId: String, count: Int) {
        handleEvent(CategoryEvent.UpdateCategoryCount(categoryId, count))
    }
    
    /**
     * Gets the current category state for a specific category
     */
    fun getCategoryState(screenType: ScreenType, categoryId: String): CategoryState? {
        return _state.value.getCategoriesForScreen(screenType)[categoryId]
    }
    
    /**
     * Checks if a category is currently expanded
     */
    fun isCategoryExpanded(screenType: ScreenType, categoryId: String): Boolean {
        return getCategoryState(screenType, categoryId)?.isExpanded ?: false
    }
    
    /**
     * Gets the list of collapsed categories for intelligent repositioning
     */
    fun getCollapsedCategories(screenType: ScreenType): List<String> {
        return _state.value.getCollapsedCategoriesForScreen(screenType)
    }
    
    /**
     * Clears the collapsed categories list after repositioning
     */
    fun clearCollapsedCategories(screenType: ScreenType) {
        val currentState = _state.value
        _state.value = when (screenType) {
            ScreenType.CHANNELS -> currentState.copy(collapsedChannelCategories = emptyList())
            ScreenType.MOVIES -> currentState.copy(collapsedMovieCategories = emptyList())
            ScreenType.SERIES -> currentState.copy(collapsedSeriesCategories = emptyList())
        }
        
        if (DEBUG) {
            Log.d(TAG, "Cleared collapsed categories for $screenType")
        }
    }
    
    // Private helper methods
    
    /**
     * Stores current state as previous state for collapse detection
     */
    private fun storePreviousStates(currentState: SmartCategoryState) {
        previousStates = mapOf(
            ScreenType.CHANNELS to currentState.channelCategories,
            ScreenType.MOVIES to currentState.movieCategories,
            ScreenType.SERIES to currentState.seriesCategories
        )
    }
    
    /**
     * Detects category collapses and marks them for intelligent repositioning
     */
    private fun detectAndHandleCollapses(screenType: ScreenType, toggledCategoryId: String) {
        val previousScreenStates = previousStates[screenType] ?: return
        val currentScreenStates = _state.value.getCategoriesForScreen(screenType)
        
        val collapsedCategories = mutableListOf<String>()
        
        // Find categories that were expanded but are now collapsed
        previousScreenStates.forEach { (categoryId, previousState) ->
            val currentState = currentScreenStates[categoryId]
            
            if (previousState.isExpanded && currentState?.isExpanded == false) {
                // This category just collapsed
                collapsedCategories.add(categoryId)
                
                if (DEBUG) {
                    Log.d(TAG, "Detected collapse of category: $categoryId in $screenType")
                }
            }
        }
        
        // Store collapsed categories for intelligent repositioning
        if (collapsedCategories.isNotEmpty()) {
            val currentState = _state.value
            _state.value = when (screenType) {
                ScreenType.CHANNELS -> currentState.copy(
                    collapsedChannelCategories = collapsedCategories
                )
                ScreenType.MOVIES -> currentState.copy(
                    collapsedMovieCategories = collapsedCategories
                )
                ScreenType.SERIES -> currentState.copy(
                    collapsedSeriesCategories = collapsedCategories
                )
            }
            
            if (DEBUG) {
                Log.d(TAG, "Marked ${collapsedCategories.size} categories for repositioning in $screenType: $collapsedCategories")
            }
        }
    }
    
    // Private helper methods
    
    private fun handleToggleCategory(state: SmartCategoryState, categoryId: String, screenType: ScreenType): SmartCategoryState {
        val categories = state.getCategoriesForScreen(screenType).toMutableMap()
        val currentCategory = categories[categoryId] ?: return state
        
        // Accordion behavior: close all other categories first
        val updatedCategories = categories.mapValues { (id, category) ->
            if (id == categoryId) {
                // Toggle the clicked category
                category.copy(isExpanded = !category.isExpanded)
            } else {
                // Close all other categories
                category.copy(isExpanded = false)
            }
        }
        
        return when (screenType) {
            ScreenType.CHANNELS -> state.copy(
                channelCategories = updatedCategories,
                activeScreenType = screenType
            )
            ScreenType.MOVIES -> state.copy(
                movieCategories = updatedCategories,
                activeScreenType = screenType
            )
            ScreenType.SERIES -> state.copy(
                seriesCategories = updatedCategories,
                activeScreenType = screenType
            )
        }
    }
    
    private fun handleUpdateCategoryCount(state: SmartCategoryState, categoryId: String, count: Int): SmartCategoryState {
        // Update count across all screen types
        val updatedChannels = updateCategoryInMap(state.channelCategories, categoryId, count)
        val updatedMovies = updateCategoryInMap(state.movieCategories, categoryId, count)
        val updatedSeries = updateCategoryInMap(state.seriesCategories, categoryId, count)
        
        return state.copy(
            channelCategories = updatedChannels,
            movieCategories = updatedMovies,
            seriesCategories = updatedSeries
        )
    }
    
    private fun updateCategoryInMap(categories: Map<String, CategoryState>, categoryId: String, count: Int): Map<String, CategoryState> {
        return if (categories.containsKey(categoryId)) {
            categories.toMutableMap().apply {
                this[categoryId] = this[categoryId]!!.copy(itemCount = count)
            }
        } else {
            categories
        }
    }
    
    private fun handleResetScreen(state: SmartCategoryState, screenType: ScreenType): SmartCategoryState {
        val categories = state.getCategoriesForScreen(screenType)
        val resetCategories = categories.mapValues { (_, category) ->
            category.copy(isExpanded = false, hasActiveContent = false)
        }
        
        return when (screenType) {
            ScreenType.CHANNELS -> state.copy(
                channelCategories = resetCategories,
                activeContentId = if (state.activeScreenType == screenType) null else state.activeContentId
            )
            ScreenType.MOVIES -> state.copy(
                movieCategories = resetCategories,
                activeContentId = if (state.activeScreenType == screenType) null else state.activeContentId
            )
            ScreenType.SERIES -> state.copy(
                seriesCategories = resetCategories,
                activeContentId = if (state.activeScreenType == screenType) null else state.activeContentId
            )
        }
    }
    
    private fun handleCollapseAll(state: SmartCategoryState): SmartCategoryState {
        val collapsedChannels = state.channelCategories.mapValues { (_, category) ->
            category.copy(isExpanded = false)
        }
        val collapsedMovies = state.movieCategories.mapValues { (_, category) ->
            category.copy(isExpanded = false)
        }
        val collapsedSeries = state.seriesCategories.mapValues { (_, category) ->
            category.copy(isExpanded = false)
        }
        
        return state.copy(
            channelCategories = collapsedChannels,
            movieCategories = collapsedMovies,
            seriesCategories = collapsedSeries
        )
    }
    
    private fun determineIconType(categoryName: String): CategoryIconType {
        return when {
            categoryName.contains("favoritos", ignoreCase = true) || 
            categoryName.contains("favorites", ignoreCase = true) -> CategoryIconType.FAVORITES
            
            categoryName.contains("recientes", ignoreCase = true) || 
            categoryName.contains("recent", ignoreCase = true) -> CategoryIconType.RECENT
            
            categoryName.contains("vivo", ignoreCase = true) || 
            categoryName.contains("live", ignoreCase = true) -> CategoryIconType.LIVE
            
            categoryName.contains("películas", ignoreCase = true) || 
            categoryName.contains("movies", ignoreCase = true) -> CategoryIconType.MOVIES
            
            categoryName.contains("series", ignoreCase = true) -> CategoryIconType.SERIES
            
            else -> CategoryIconType.FOLDER
        }
    }
}

/**
 * Global singleton instance for category state management
 * This ensures consistent state across all screens
 */
object GlobalCategoryStateManager {
    val instance: CategoryStateManager by lazy { CategoryStateManager() }
}