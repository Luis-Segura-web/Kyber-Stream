package com.kybers.play.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kybers.play.data.local.model.User
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.EpgEvent

@Database(
    entities = [User::class, Movie::class, Series::class, LiveStream::class, EpgEvent::class, MovieDetailsCache::class],
    version = 5, // ¡VERSIÓN INCREMENTADA! Esto le dice a Room que el esquema ha cambiado.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun liveStreamDao(): LiveStreamDao
    abstract fun epgEventDao(): EpgEventDao
    abstract fun movieDetailsCacheDao(): MovieDetailsCacheDao

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
                    // Esta estrategia destruirá y reconstruirá la base de datos si no
                    // proveemos una migración. Es perfecta para la fase de desarrollo.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
