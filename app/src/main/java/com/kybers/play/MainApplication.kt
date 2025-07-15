package com.kybers.play

import android.app.Application
import android.content.Context
import com.kybers.play.data.local.AppDatabase
import com.kybers.play.data.remote.RetrofitClient
import com.kybers.play.data.repository.ContentRepository
import com.kybers.play.data.repository.UserRepository

/**
 * Clase Application personalizada. Es el punto de entrada de la app y el lugar
 * ideal para inicializar componentes que vivirán durante todo el ciclo de vida de la app.
 */
class MainApplication : Application() {

    /**
     * Contenedor de dependencias para toda la aplicación.
     * Almacenará las instancias de nuestros repositorios y fuentes de datos.
     */
    lateinit var container: AppContainer
        private set // Hacemos que solo se pueda modificar dentro de esta clase.

    override fun onCreate() {
        super.onCreate()
        // Creamos el contenedor de dependencias cuando se inicia la aplicación.
        container = AppContainer(this)
    }
}

/**
 * Un contenedor simple para gestionar nuestras dependencias.
 * Utiliza inicialización 'lazy' para crear las instancias solo cuando se usan por primera vez.
 *
 * @param context El contexto de la aplicación.
 */
class AppContainer(context: Context) {

    // Creamos la instancia de la base de datos de forma perezosa.
    private val database by lazy { AppDatabase.getDatabase(context) }

    // Creamos la instancia del repositorio de usuarios, proveyéndole el DAO de la base de datos.
    val userRepository by lazy { UserRepository(database.userDao()) }

    // Factoría para crear un ContentRepository bajo demanda.
    fun createContentRepository(baseUrl: String): ContentRepository {
        val apiService = RetrofitClient.create(baseUrl)
        return ContentRepository(apiService)
    }
}
