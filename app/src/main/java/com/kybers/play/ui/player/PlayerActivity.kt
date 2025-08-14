package com.kybers.play.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

// Legacy activity kept as a no-op to avoid breaking references. Not used.
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Close immediately; legacy player flow removed.
        finish()
    }
}
