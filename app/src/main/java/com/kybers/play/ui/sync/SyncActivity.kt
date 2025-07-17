package com.kybers.play.ui.sync

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.kybers.play.MainApplication
import com.kybers.play.data.local.model.User
import com.kybers.play.ui.SyncViewModelFactory
import com.kybers.play.ui.login.LoginActivity
import com.kybers.play.ui.main.MainActivity
import com.kybers.play.ui.theme.IPTVAppTheme
import kotlinx.coroutines.launch

/**
 * An activity dedicated to showing the data synchronization screen.
 * It's responsible for initiating the sync process and navigating to the
 * main application screen upon completion.
 */
class SyncActivity : ComponentActivity() {

    private var currentUser: User? = null

    private val syncViewModel: SyncViewModel by viewModels {
        val appContainer = (application as MainApplication).container
        // We need to create the ContentRepository for the current user to sync data.
        val contentRepository = appContainer.createContentRepository(currentUser!!.url)
        SyncViewModelFactory(contentRepository, appContainer.syncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getIntExtra("USER_ID", -1)
        if (userId == -1) {
            // If no valid user ID is passed, we can't proceed.
            // Navigate back to LoginActivity.
            navigateToLogin()
            return
        }

        // We need to fetch the user details asynchronously before setting the content.
        lifecycleScope.launch {
            val user = (application as MainApplication).container.userRepository.getUserById(userId)
            if (user == null) {
                navigateToLogin()
            } else {
                currentUser = user
                // Now that we have the user, we can set the content and start the sync.
                setContent {
                    IPTVAppTheme {
                        SyncScreen(
                            viewModel = syncViewModel,
                            onSyncComplete = { navigateToMain(userId) },
                            onSyncFailed = {
                                // Inform the user and navigate back to login.
                                Toast.makeText(this@SyncActivity, "Data sync failed. Please try again.", Toast.LENGTH_LONG).show()
                                navigateToLogin()
                            }
                        )
                    }
                }
                // Start the sync process after the UI is set up.
                syncViewModel.startSync(user)
            }
        }
    }

    private fun navigateToMain(userId: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USER_ID", userId)
            // These flags clear the task stack, so the user can't go back to the
            // login or sync screens.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
