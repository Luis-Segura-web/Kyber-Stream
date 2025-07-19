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
import com.kybers.play.data.remote.model.EpgEvent

@Database(
    // ¡MODIFICADO! Añadimos EpgEvent a la lista de entidades
    entities = [User::class, Movie::class, Series::class, LiveStream::class, EpgEvent::class],
    version = 3, // ¡CORRECCIÓN CLAVE! Se incrementa la versión de 2 a 3 para reflejar los cambios en el esquema.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun liveStreamDao(): LiveStreamDao
    abstract fun epgEventDao(): EpgEventDao // <--- ¡NUEVA FUNCIÓN ABSTRACTA!

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv_app_database"
                )
                    // Esta línea es nuestra salvación en desarrollo.
                    // Le dice a Room que si la versión cambia, simplemente borre la base de datos
                    // anterior y cree una nueva. Evita tener que escribir migraciones complejas.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
