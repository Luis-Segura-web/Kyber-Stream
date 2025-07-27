package com.kybers.play.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kybers.play.data.local.model.ActorDetailsCache
import com.kybers.play.data.local.model.CategoryCache
import com.kybers.play.data.local.model.EpisodeDetailsCache
import com.kybers.play.data.local.model.User
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.local.model.SeriesDetailsCache
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.Episode

@Database(
    entities = [
        User::class,
        Movie::class,
        Series::class,
        LiveStream::class,
        EpgEvent::class,
        MovieDetailsCache::class,
        Episode::class,
        SeriesDetailsCache::class,
        // --- ¡NUEVAS ENTIDADES AÑADIDAS! ---
        ActorDetailsCache::class,
        EpisodeDetailsCache::class,
        CategoryCache::class
    ],
    // --- ¡VERSIÓN INCREMENTADA A 4! ---
    version = 4,
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
    abstract fun episodeDao(): EpisodeDao
    abstract fun seriesDetailsCacheDao(): SeriesDetailsCacheDao

    // --- ¡NUEVOS DAOs AÑADIDOS! ---
    abstract fun actorDetailsCacheDao(): ActorDetailsCacheDao
    abstract fun episodeDetailsCacheDao(): EpisodeDetailsCacheDao
    abstract fun categoryCacheDao(): CategoryCacheDao

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
                    // Esta opción destruirá y reconstruirá la base de datos si cambiamos la versión.
                    // Es útil durante el desarrollo, pero para una app en producción se usaría una migración real.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
