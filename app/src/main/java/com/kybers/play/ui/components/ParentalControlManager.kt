package com.kybers.play.ui.components

import com.kybers.play.data.preferences.PreferenceManager
import com.kybers.play.data.remote.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enhanced parental control utility that provides immediate content filtering
 */
class ParentalControlManager(
    private val preferenceManager: PreferenceManager
) {
    
    private val _blockedCategoriesState = MutableStateFlow(preferenceManager.getBlockedCategories())
    val blockedCategoriesState: StateFlow<Set<String>> = _blockedCategoriesState.asStateFlow()
    
    /**
     * Updates blocked categories and immediately notifies observers
     */
    fun updateBlockedCategories(blockedIds: Set<String>) {
        preferenceManager.saveBlockedCategories(blockedIds)
        _blockedCategoriesState.value = blockedIds
    }
    
    /**
     * Checks if parental control is enabled
     */
    fun isParentalControlEnabled(): Boolean {
        return preferenceManager.isParentalControlEnabled()
    }
    
    /**
     * Filters categories based on parental control settings
     */
    fun <T> filterCategories(
        categories: List<T>,
        getCategoryId: (T) -> String?
    ): List<T> {
        if (!isParentalControlEnabled()) {
            return categories
        }
        
        val blockedIds = _blockedCategoriesState.value
        return categories.filter { category ->
            val categoryId = getCategoryId(category)
            categoryId != null && !blockedIds.contains(categoryId)
        }
    }
    
    /**
     * Filters content items based on their category
     */
    fun <T> filterContentByCategory(
        content: List<T>,
        getCategoryId: (T) -> String?
    ): List<T> {
        if (!isParentalControlEnabled()) {
            return content
        }
        
        val blockedIds = _blockedCategoriesState.value
        return content.filter { item ->
            val categoryId = getCategoryId(item)
            categoryId == null || !blockedIds.contains(categoryId)
        }
    }
    
    /**
     * Checks if a specific category is blocked
     */
    fun isCategoryBlocked(categoryId: String?): Boolean {
        if (!isParentalControlEnabled() || categoryId == null) {
            return false
        }
        return _blockedCategoriesState.value.contains(categoryId)
    }
    
    /**
     * Refreshes the blocked categories from preferences
     */
    fun refreshFromPreferences() {
        _blockedCategoriesState.value = preferenceManager.getBlockedCategories()
    }
}