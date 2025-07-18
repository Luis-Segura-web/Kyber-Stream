package com.kybers.play.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kybers.play.data.local.model.User
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series

@Database(
    // Las entidades LiveStream, Movie, Series ahora tienen claves primarias compuestas con userId.
    // Room manejará esto automáticamente con la configuración actual.
    entities = [User::class, Movie::class, Series::class, LiveStream::class],
    version = 2, // La versión de la base de datos es correcta.
    exportSchema = false // No exportamos el esquema JSON.
)
@TypeConverters(Converters::class) // Se usan convertidores para tipos complejos (ej. List<String>)
abstract class AppDatabase : RoomDatabase() {

    // DAOs para acceder a los datos de cada entidad
    abstract fun userDao(): UserDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun liveStreamDao(): LiveStreamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Patrón Singleton para asegurar una única instancia de la base de datos
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv_app_database" // Nombre del archivo de la base de datos
                )
                    // Si el esquema de la base de datos cambia y no hay una migración definida,
                    // Room destruirá y recreará la base de datos.
                    // Esto es útil en desarrollo, pero en producción se preferirían migraciones.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}