package com.kybers.play.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa la tabla de perfiles de usuario en la base de datos local.
 * Cada instancia de esta clase es una fila en la tabla 'user_profiles'.
 *
 * @param id El identificador único para cada perfil, se autogenera.
 * @param profileName Un nombre amigable para el perfil (ej. "Mi Perfil", "Casa").
 * @param url La URL base del servidor Xtream Codes.
 * @param username El nombre de usuario para la autenticación.
 * @param password La contraseña para la autenticación.
 */
@Entity(tableName = "user_profiles")
data class User(
    // Define la clave primaria de la tabla. `autoGenerate = true` hace que Room asigne un ID nuevo a cada usuario.
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Define una columna en la tabla. El nombre de la columna será "profile_name".
    @ColumnInfo(name = "profile_name")
    val profileName: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "password")
    val password: String
)
