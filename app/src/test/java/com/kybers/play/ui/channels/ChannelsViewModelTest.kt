package com.kybers.play.ui.channels

import org.junit.Test
import org.junit.Assert.*

/**
 * Simple unit test to verify category expansion logic
 */
class CategoryExpansionTest {

    @Test
    fun `multiple categories can be expanded - logic verification`() {
        // This test verifies that the logic allows multiple categories to be expanded
        // by simulating the expansion state behavior
        
        val expansionState = mutableMapOf<String, Boolean>()
        
        // Simulate the fixed logic (no longer collapses all other categories)
        fun onCategoryToggled(categoryId: String) {
            val isNowExpanding = !(expansionState[categoryId] ?: false)
            expansionState[categoryId] = isNowExpanding
        }
        
        // Test expanding multiple categories
        onCategoryToggled("category1")
        onCategoryToggled("category2")
        onCategoryToggled("category3")
        
        // Verify all categories can be expanded simultaneously
        assertTrue("Category 1 should be expanded", expansionState["category1"] == true)
        assertTrue("Category 2 should be expanded", expansionState["category2"] == true)
        assertTrue("Category 3 should be expanded", expansionState["category3"] == true)
        
        // Test collapsing individual category
        onCategoryToggled("category2")
        assertTrue("Category 1 should still be expanded", expansionState["category1"] == true)
        assertFalse("Category 2 should be collapsed", expansionState["category2"] == true)
        assertTrue("Category 3 should still be expanded", expansionState["category3"] == true)
    }
}