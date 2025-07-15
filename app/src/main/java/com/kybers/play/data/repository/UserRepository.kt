package com.kybers.play.data.repository

import com.kybers.play.data.local.UserDao
import com.kybers.play.data.local.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar los datos de los perfiles de usuario.
 * Actúa como una capa de abstracción entre las fuentes de datos (el DAO) y los ViewModels.
 * Esto nos permite centralizar la lógica de datos y hacerla más mantenible.
 *
 * @param userDao El Data Access Object para la entidad User. El repositorio lo usa para
 * realizar operaciones en la base de datos local.
 */
class UserRepository(private val userDao: UserDao) {

    /**
     * Obtiene un Flow con la lista de todos los perfiles de usuario.
     * Al ser un Flow, cualquier cambio en la base de datos se reflejará automáticamente
     * en la UI que esté observando este dato.
     */
    val allUsers: Flow<List<User>> = userDao.getAllUsers()

    /**
     * Inserta un nuevo usuario en la base de datos.
     * Esta es una función 'suspend' porque se ejecuta en una coroutina para no
     * bloquear el hilo principal.
     *
     * @param user El usuario a insertar.
     */
    suspend fun insert(user: User) {
        userDao.insertUser(user)
    }

    /**
     * Elimina un usuario de la base de datos.
     *
     * @param user El usuario a eliminar.
     */
    suspend fun delete(user: User) {
        userDao.deleteUser(user)
    }

    /**
     * Busca un usuario por su ID.
     *
     * @param id El ID del usuario a buscar.
     * @return El objeto User o null si no se encuentra.
     */
    suspend fun getUserById(id: Int): User? {
        return userDao.getUserById(id)
    }
}