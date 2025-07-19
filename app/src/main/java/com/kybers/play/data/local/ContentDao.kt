package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import kotlinx.coroutines.flow.Flow
import android.util.Log // <-- ¡NUEVA IMPORTACIÓN!

@Dao
interface LiveStreamDao {
    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId AND userId = :userId")
    fun getLiveStreamsByCategory(categoryId: String, userId: Int): Flow<List<LiveStream>>

    @Query("SELECT * FROM live_streams WHERE userId = :userId")
    fun getAllLiveStreams(userId: Int): Flow<List<LiveStream>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(streams: List<LiveStream>)

    @Query("DELETE FROM live_streams WHERE userId = :userId")
    suspend fun deleteAllByUserId(userId: Int)

    @Transaction
    suspend fun replaceAll(streams: List<LiveStream>, userId: Int) {
        Log.d("LiveStreamDao", "replaceAll: Eliminando streams antiguos para userId: $userId")
        deleteAllByUserId(userId)
        Log.d("LiveStreamDao", "replaceAll: Insertando ${streams.size} nuevos streams para userId: $userId")
        if (streams.isNotEmpty()) { // Solo insertar si la lista no está vacía
            insertAll(streams)
        } else {
            Log.d("LiveStreamDao", "replaceAll: No hay streams para insertar para userId: $userId")
        }
    }
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE categoryId = :categoryId AND userId = :userId")
    fun getMoviesByCategory(categoryId: String, userId: Int): Flow<List<Movie>>

    /**
     * ¡MODIFICADO!
     * Nos aseguramos de que esta función exista y devuelva un Flow.
     * Nuestro MoviesViewModel la usará para obtener todas las películas de una vez
     * y manejarlas en memoria, lo cual es muy eficiente.
     */
    @Query("SELECT * FROM movies WHERE userId = :userId")
    fun getAllMovies(userId: Int): Flow<List<Movie>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<Movie>)

    @Query("DELETE FROM movies WHERE userId = :userId")
    suspend fun deleteAllByUserId(userId: Int)

    @Transaction
    suspend fun replaceAll(movies: List<Movie>, userId: Int) {
        Log.d("MovieDao", "replaceAll: Eliminando películas antiguas para userId: $userId")
        deleteAllByUserId(userId)
        Log.d("MovieDao", "replaceAll: Insertando ${movies.size} nuevas películas para userId: $userId")
        if (movies.isNotEmpty()) {
            insertAll(movies)
        } else {
            Log.d("MovieDao", "replaceAll: No hay películas para insertar para userId: $userId")
        }
    }
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE categoryId = :categoryId AND userId = :userId")
    fun getSeriesByCategory(categoryId: String, userId: Int): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE userId = :userId")
    fun getAllSeries(userId: Int): Flow<List<Series>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<Series>)

    @Query("DELETE FROM series WHERE userId = :userId")
    suspend fun deleteAllByUserId(userId: Int)

    @Transaction
    suspend fun replaceAll(series: List<Series>, userId: Int) {
        Log.d("SeriesDao", "replaceAll: Eliminando series antiguas para userId: $userId")
        deleteAllByUserId(userId)
        Log.d("SeriesDao", "replaceAll: Insertando ${series.size} nuevas series para userId: $userId")
        if (series.isNotEmpty()) {
            insertAll(series)
        } else {
            Log.d("SeriesDao", "replaceAll: No hay series para insertar para userId: $userId")
        }
    }
}
