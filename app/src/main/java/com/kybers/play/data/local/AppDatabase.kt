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
    // --- ¡VERSIÓN INCREMENTADA! ---
    // Aumentamos la versión para forzar la actualización de la estructura de la tabla de películas.
    version = 1,
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}