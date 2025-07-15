package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kybers.play.data.local.model.User
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) para la entidad User.
 * Define los métodos para interactuar con la tabla 'user_profiles' en la base de datos.
 * Room generará la implementación de esta interfaz automáticamente.
 */
@Dao
interface UserDao {

    /**
     * Inserta un nuevo perfil de usuario en la base de datos.
     * Si ya existe un usuario con el mismo ID, lo reemplazará.
     * La anotación 'suspend' indica que esta función debe ser llamada desde una coroutina,
     * ya que las operaciones de base de datos no deben bloquear el hilo principal.
     * @param user El objeto User a insertar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    /**
     * Obtiene todos los perfiles de usuario de la base de datos, ordenados por su ID.
     * Devuelve un 'Flow', lo que nos permite observar los cambios en la tabla en tiempo real.
     * Si se añade, borra o actualiza un usuario, esta lista se emitirá de nuevo automáticamente.
     * @return Un Flow que emite una lista de todos los objetos User.
     */
    @Query("SELECT * FROM user_profiles ORDER BY id ASC")
    fun getAllUsers(): Flow<List<User>>

    /**
     * Obtiene un perfil de usuario específico por su ID.
     * @param id El ID del usuario a buscar.
     * @return El objeto User correspondiente o null si no se encuentra.
     */
    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getUserById(id: Int): User?

    /**
     * Elimina un perfil de usuario de la base de datos.
     * @param user El objeto User a eliminar.
     */
    @Delete
    suspend fun deleteUser(user: User)
}
