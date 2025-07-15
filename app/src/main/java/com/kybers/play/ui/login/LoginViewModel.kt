package com.kybers.play.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kybers.play.data.local.model.User
import com.kybers.play.data.repository.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel para la pantalla de Login/Selección de Perfil.
 * Se encarga de la lógica de negocio y de preparar los datos para la UI.
 *
 * @param userRepository El repositorio para acceder a los datos de los usuarios.
 */
class LoginViewModel(private val userRepository: UserRepository) : ViewModel() {

    /**
     * Un StateFlow que expone la lista de todos los perfiles de usuario guardados.
     * La UI observará este Flow para actualizarse automáticamente cuando los datos cambien.
     */
    val users: StateFlow<List<User>> = userRepository.allUsers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Guarda un nuevo perfil de usuario en la base de datos.
     * Lanza una coroutina en el viewModelScope para realizar la operación de inserción
     * en un hilo secundario, sin bloquear la UI.
     *
     * @param profileName El nombre para el nuevo perfil.
     * @param url La URL del servidor.
     * @param username El nombre de usuario.
     * @param password La contraseña.
     */
    fun addUser(profileName: String, url: String, username: String, password: String) {
        viewModelScope.launch {
            val newUser = User(
                profileName = profileName,
                url = url,
                username = username,
                password = password
            )
            userRepository.insert(newUser)
        }
    }

    /**
     * Elimina un perfil de usuario de la base de datos.
     *
     * @param user El usuario a eliminar.
     */
    fun deleteUser(user: User) {
        viewModelScope.launch {
            userRepository.delete(user)
        }
    }
}
