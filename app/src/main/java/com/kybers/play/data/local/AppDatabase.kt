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
        ActorDetailsCache::class,
        EpisodeDetailsCache::class,
        CategoryCache::class
    ],
    version = 5,
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
                    // --- ¡ADVERTENCIA CORREGIDA! Usamos la nueva versión de la función ---
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
