package com.kybers.play.ui.sync

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

    // El ViewModel se inicializa más tarde, una vez que currentUser esté disponible.
    private val syncViewModel: SyncViewModel by viewModels {
        // Asegurarse de que currentUser no sea nulo antes de usarlo.
        val user = currentUser ?: throw IllegalStateException("User must be set before accessing SyncViewModel.")
        val appContainer = (application as MainApplication).container
        val contentRepository = appContainer.createContentRepository(user.url)
        SyncViewModelFactory(contentRepository, appContainer.syncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = intent.getIntExtra("USER_ID", -1)
        Log.d("SyncActivity", "onCreate: Recibido userId = $userId")

        if (userId == -1) {
            Log.e("SyncActivity", "onCreate: No se recibió un userId válido. Volviendo a Login.")
            navigateToLogin()
            return
        }

        // --- CORRECCIÓN ---
        // Aseguramos que 'launch' esté importado para que lifecycleScope funcione.
        lifecycleScope.launch {
            val user = (application as MainApplication).container.userRepository.getUserById(userId)
            if (user == null) {
                Log.e("SyncActivity", "onCreate: Usuario con ID $userId no encontrado en la base de datos. Volviendo a Login.")
                navigateToLogin()
            } else {
                currentUser = user
                Log.d("SyncActivity", "onCreate: Usuario cargado: ${currentUser?.profileName} (ID: ${currentUser?.id})")

                setContent {
                    IPTVAppTheme {
                        SyncScreen(
                            viewModel = syncViewModel,
                            onSyncComplete = { navigateToMain(userId) },
                            onSyncFailed = {
                                Toast.makeText(this@SyncActivity, "Data sync failed. Please try again.", Toast.LENGTH_LONG).show()
                                Log.e("SyncActivity", "onCreate: Sincronización fallida para userId: $userId")
                                navigateToLogin()
                            }
                        )
                    }
                }
                // Iniciar el proceso de sincronización después de que la UI esté configurada.
                currentUser?.let { syncViewModel.startSync(it) } ?: Log.e("SyncActivity", "Error: currentUser es nulo al intentar iniciar la sincronización.")
            }
        }
    }

    private fun navigateToMain(userId: Int) {
        Log.d("SyncActivity", "navigateToMain: Navegando a MainActivity con userId = $userId")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("USER_ID", userId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        Log.d("SyncActivity", "navigateToLogin: Navegando de vuelta a LoginActivity.")
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
