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
 * --- ¡ACTIVITY ACTUALIZADA! ---
 * Activity dedicada a mostrar la pantalla de sincronización de datos.
 * Ahora construye el SyncViewModel utilizando los repositorios modulares.
 */
class SyncActivity : ComponentActivity() {

    private var currentUser: User? = null

    private val syncViewModel: SyncViewModel by viewModels {
        val user = currentUser ?: throw IllegalStateException("El usuario debe estar seteado para acceder al SyncViewModel.")
        val appContainer = (application as MainApplication).container

        // --- ¡CAMBIO CLAVE! ---
        // Creamos los repositorios necesarios a través del AppContainer.
        val liveRepository = appContainer.createLiveRepository(user.url)
        val vodRepository = appContainer.createVodRepository(user.url)

        // Pasamos los nuevos repositorios a la fábrica del ViewModel.
        SyncViewModelFactory(
            liveRepository,
            vodRepository,
            appContainer.syncManager,
            appContainer.preferenceManager
        )
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

        lifecycleScope.launch {
            val user = (application as MainApplication).container.userRepository.getUserById(userId)
            if (user == null) {
                Log.e("SyncActivity", "onCreate: Usuario con ID $userId no encontrado. Volviendo a Login.")
                navigateToLogin()
            } else {
                currentUser = user
                Log.d("SyncActivity", "onCreate: Usuario cargado: ${currentUser?.profileName}")

                setContent {
                    IPTVAppTheme {
                        SyncScreen(
                            viewModel = syncViewModel,
                            onSyncComplete = { navigateToMain(userId) },
                            onSyncFailed = {
                                Toast.makeText(this@SyncActivity, "Falló la sincronización. Intenta de nuevo.", Toast.LENGTH_LONG).show()
                                Log.e("SyncActivity", "onCreate: Sincronización fallida para userId: $userId")
                                navigateToLogin()
                            }
                        )
                    }
                }
                // Inicia el proceso de sincronización después de configurar la UI.
                currentUser?.let { syncViewModel.startSync(it) }
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
