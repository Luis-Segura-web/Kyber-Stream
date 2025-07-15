package com.kybers.play.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kybers.play.data.local.model.User

/**
 * La clase principal de la base de datos para la aplicación.
 * Esta clase abstracta une todas las partes de la base de datos (entidades y DAOs).
 *
 * @property entities Un array de todas las clases de entidad que pertenecen a esta base de datos.
 * @property version El número de versión de la base de datos. Debe incrementarse cada vez que cambies el esquema.
 * @property exportSchema Si se debe exportar el esquema de la base de datos a un archivo JSON en el proyecto. Es útil para migraciones complejas.
 */
@Database(entities = [User::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Declara una función abstracta para cada DAO.
     * Room generará la implementación para devolver una instancia del DAO.
     */
    abstract fun userDao(): UserDao

    /**
     * El companion object nos permite acceder a los métodos para crear u obtener la base de datos
     * sin tener que instanciar la clase. Es el lugar perfecto para nuestro Singleton.
     */
    companion object {
        // La anotación @Volatile asegura que el valor de INSTANCE siempre esté actualizado
        // y sea el mismo para todos los hilos de ejecución.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtiene la instancia Singleton de la base de datos.
         * Si la instancia no es nula, la devuelve.
         * Si es nula, crea la base de datos en un bloque sincronizado para evitar que múltiples hilos
         * la creen al mismo tiempo.
         *
         * @param context El contexto de la aplicación.
         * @return La instancia Singleton de AppDatabase.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv_app_database" // El nombre del archivo de la base de datos en el dispositivo.
                )
                    // Aquí se pueden añadir estrategias de migración en el futuro si cambiamos el esquema.
                    .build()
                INSTANCE = instance
                // Devuelve la instancia recién creada.
                instance
            }
        }
    }
}
