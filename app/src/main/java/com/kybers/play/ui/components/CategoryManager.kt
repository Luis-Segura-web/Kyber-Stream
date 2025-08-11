package com.kybers.play.ui.components

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.ui.components.categories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Enhanced category manager with intelligent positioning, playback tracking, and comprehensive logging
 */
@HiltViewModel
class CategoryManager @Inject constructor() : ViewModel() {
    
    companion object {
        private const val TAG = "CategoryManager"
        private const val DEBUG = true // Set to false for production
    }
    
    private val categoryStateManager = GlobalCategoryStateManager.instance
    
    // Scroll events for auto-positioning
    private val _scrollToItemEvent = MutableSharedFlow<String>()
    val scrollToItemEvent: SharedFlow<String> = _scrollToItemEvent.asSharedFlow()
    
    // Playback state tracking
    private val _activePlaybackState = MutableStateFlow<PlaybackState?>(null)
    val activePlaybackState: StateFlow<PlaybackState?> = _activePlaybackState.asStateFlow()
    
    /**
     * Toggles a category with intelligent behavior and logging
     */
    fun toggleCategory(categoryId: String, screenType: ScreenType) {
        if (DEBUG) Log.d(TAG, "Toggling category: $categoryId on screen: $screenType")
        
        categoryStateManager.handleEvent(
            CategoryEvent.ToggleCategory(categoryId, screenType)
        )
        
        // Auto-scroll to the category
        viewModelScope.launch {
            if (DEBUG) Log.d(TAG, "Auto-scrolling to category: $categoryId")
            _scrollToItemEvent.emit(categoryId)
        }
    }
    
    /**
     * Sets active content and updates category states with logging
     */
    fun setActiveContent(contentId: String?, categoryId: String?, screenType: ScreenType) {
        if (DEBUG) {
            Log.d(TAG, "Setting active content: contentId=$contentId, categoryId=$categoryId, screenType=$screenType")
        }
        
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
     * Updates category item count with automatic refresh and performance tracking
     */
    fun updateCategoryCount(categoryId: String, count: Int) {
        if (DEBUG) Log.d(TAG, "Updating category count: $categoryId = $count items")
        
        categoryStateManager.handleEvent(
            CategoryEvent.UpdateCategoryCount(categoryId, count)
        )
    }
    
    /**
     * Collapses all categories across all screens with logging
     */
    fun collapseAllCategories() {
        if (DEBUG) Log.d(TAG, "Collapsing all categories across all screens")
        categoryStateManager.handleEvent(CategoryEvent.CollapseAll)
    }
    
    /**
     * Gets the current state for a specific screen with performance optimization
     */
    fun getCategoriesForScreen(screenType: ScreenType): StateFlow<Map<String, CategoryState>> {
        return categoryStateManager.state.map { state ->
            state.getCategoriesForScreen(screenType)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep alive for 5 seconds after last subscriber
            initialValue = emptyMap()
        )
    }
    
    /**
     * Checks if a category is expanded with logging
     */
    fun isCategoryExpanded(categoryId: String, screenType: ScreenType): Boolean {
        val isExpanded = categoryStateManager.isCategoryExpanded(screenType, categoryId)
        if (DEBUG) Log.v(TAG, "Category $categoryId expanded: $isExpanded")
        return isExpanded
    }
    
    /**
     * Scroll to specific category with performance tracking
     */
    fun scrollToCategory(categoryId: String) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            if (DEBUG) Log.d(TAG, "Initiating scroll to category: $categoryId")
            
            _scrollToItemEvent.emit(categoryId)
            
            val duration = System.currentTimeMillis() - startTime
            if (DEBUG) Log.d(TAG, "Scroll event emitted for $categoryId in ${duration}ms")
        }
    }
    
    /**
     * Initialize categories for a screen with performance monitoring
     */
    fun initializeCategories(screenType: ScreenType, categories: List<Pair<String, String>>) {
        val startTime = System.currentTimeMillis()
        if (DEBUG) Log.d(TAG, "Initializing ${categories.size} categories for $screenType")
        
        categoryStateManager.initializeCategories(screenType, categories)
        
        val duration = System.currentTimeMillis() - startTime
        if (DEBUG) Log.d(TAG, "Categories initialized for $screenType in ${duration}ms")
    }
    
    /**
     * Smart positioning: expands a category and scrolls to first item with enhanced logging
     */
    fun expandAndScrollToCategory(categoryId: String, screenType: ScreenType) {
        if (DEBUG) Log.d(TAG, "Smart expand and scroll: $categoryId on $screenType")
        
        toggleCategory(categoryId, screenType)
        
        viewModelScope.launch {
            // Small delay to allow expansion animation to start
            kotlinx.coroutines.delay(100)
            if (DEBUG) Log.d(TAG, "Delayed scroll triggered for $categoryId")
            _scrollToItemEvent.emit(categoryId)
        }
    }
    
    /**
     * Handles viewport changes and adjusts positioning with performance monitoring
     */
    fun onViewportChanged(visibleItemsInfo: LazyListLayoutInfo) {
        // Performance monitoring for large lists
        if (DEBUG && visibleItemsInfo.totalItemsCount > 100) {
            Log.v(TAG, "Large list detected: ${visibleItemsInfo.totalItemsCount} items, " +
                    "visible: ${visibleItemsInfo.visibleItemsInfo.size}")
        }
        
        // This can be extended to implement smart positioning based on visible items
        // For now, we keep it simple but the structure is ready for enhancement
    }
    
    /**
     * Performance debugging - logs current state
     */
    fun logCurrentState() {
        if (DEBUG) {
            viewModelScope.launch {
                val state = categoryStateManager.state.value
                Log.d(TAG, "=== Current Category State ===")
                Log.d(TAG, "Active Screen: ${state.activeScreenType}")
                Log.d(TAG, "Active Content: ${state.activeContentId}")
                Log.d(TAG, "Channel Categories: ${state.channelCategories.size}")
                Log.d(TAG, "Movie Categories: ${state.movieCategories.size}")
                Log.d(TAG, "Series Categories: ${state.seriesCategories.size}")
                
                // Log expanded categories
                state.channelCategories.values.filter { it.isExpanded }.forEach {
                    Log.d(TAG, "Expanded Channel Category: ${it.name} (${it.itemCount} items)")
                }
                state.movieCategories.values.filter { it.isExpanded }.forEach {
                    Log.d(TAG, "Expanded Movie Category: ${it.name} (${it.itemCount} items)")
                }
                state.seriesCategories.values.filter { it.isExpanded }.forEach {
                    Log.d(TAG, "Expanded Series Category: ${it.name} (${it.itemCount} items)")
                }
                Log.d(TAG, "============================")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        if (DEBUG) Log.d(TAG, "CategoryManager cleared")
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