package com.kybers.play.ui.splash

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the splash screen.
 * This ViewModel is kept for potential future use but currently
 * the splash screen navigates directly without complex logic.
 */
@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {
    // Simple ViewModel with no dependencies
}
