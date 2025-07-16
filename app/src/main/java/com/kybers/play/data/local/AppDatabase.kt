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
    // ¡CORREGIDO! Añadimos LiveStream a la lista de entidades
    entities = [User::class, Movie::class, Series::class, LiveStream::class],
    version = 2, // La versión ya está incrementada, perfecto.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    // ¡CORREGIDO! Añadimos la función abstracta para el nuevo DAO
    abstract fun liveStreamDao(): LiveStreamDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
