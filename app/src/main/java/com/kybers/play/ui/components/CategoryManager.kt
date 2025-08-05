package com.kybers.play.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.ui.components.categories.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Enhanced category manager with intelligent positioning and playback tracking
 */
class CategoryManager : ViewModel() {
    
    private val categoryStateManager = GlobalCategoryStateManager.instance
    
    // Scroll events for auto-positioning
    private val _scrollToItemEvent = MutableSharedFlow<String>()
    val scrollToItemEvent: SharedFlow<String> = _scrollToItemEvent.asSharedFlow()
    
    // Playback state tracking
    private val _activePlaybackState = MutableStateFlow<PlaybackState?>(null)
    val activePlaybackState: StateFlow<PlaybackState?> = _activePlaybackState.asStateFlow()
    
    /**
     * Toggles a category with intelligent behavior
     */
    fun toggleCategory(categoryId: String, screenType: ScreenType) {
        categoryStateManager.handleEvent(
            CategoryEvent.ToggleCategory(categoryId, screenType)
        )
        
        // Auto-scroll to the category
        viewModelScope.launch {
            _scrollToItemEvent.emit(categoryId)
        }
    }
    
    /**
     * Sets active content and updates category states
     */
    fun setActiveContent(contentId: String?, categoryId: String?, screenType: ScreenType) {
        _activePlaybackState.value = if (contentId != null && categoryId != null) {
            PlaybackState(contentId, categoryId, screenType)
        } else {
            null
        }
        
        if (categoryId != null) {
            categoryStateManager.handleEvent(
                CategoryEvent.SetActiveContent(categoryId, contentId)
            )
        }
    }
    
    /**
     * Updates category item count with automatic refresh
     */
    fun updateCategoryCount(categoryId: String, count: Int) {
        categoryStateManager.handleEvent(
            CategoryEvent.UpdateCategoryCount(categoryId, count)
        )
    }
    
    /**
     * Collapses all categories across all screens
     */
    fun collapseAllCategories() {
        categoryStateManager.handleEvent(CategoryEvent.CollapseAll)
    }
    
    /**
     * Gets the current state for a specific screen
     */
    fun getCategoriesForScreen(screenType: ScreenType): StateFlow<Map<String, CategoryState>> {
        return categoryStateManager.state.map { state ->
            state.getCategoriesForScreen(screenType)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    }
    
    /**
     * Checks if a category is expanded
     */
    fun isCategoryExpanded(categoryId: String, screenType: ScreenType): Boolean {
        return categoryStateManager.isCategoryExpanded(screenType, categoryId)
    }
    
    /**
     * Scroll to specific category
     */
    fun scrollToCategory(categoryId: String) {
        viewModelScope.launch {
            _scrollToItemEvent.emit(categoryId)
        }
    }
    
    /**
     * Initialize categories for a screen
     */
    fun initializeCategories(screenType: ScreenType, categories: List<Pair<String, String>>) {
        categoryStateManager.initializeCategories(screenType, categories)
    }
    
    /**
     * Smart positioning: expands a category and scrolls to first item
     */
    fun expandAndScrollToCategory(categoryId: String, screenType: ScreenType) {
        toggleCategory(categoryId, screenType)
        
        viewModelScope.launch {
            // Small delay to allow expansion animation to start
            kotlinx.coroutines.delay(100)
            _scrollToItemEvent.emit(categoryId)
        }
    }
    
    /**
     * Handles viewport changes and adjusts positioning
     */
    fun onViewportChanged(visibleItemsInfo: LazyListLayoutInfo) {
        // This can be extended to implement smart positioning based on visible items
        // For now, we keep it simple but the structure is ready for enhancement
    }
}

/**
 * Represents the current playback state
 */
data class PlaybackState(
    val contentId: String,
    val categoryId: String,
    val screenType: ScreenType
)

/**
 * Global singleton for category management across the app
 */
object GlobalCategoryManager {
    val instance: CategoryManager by lazy { CategoryManager() }
}