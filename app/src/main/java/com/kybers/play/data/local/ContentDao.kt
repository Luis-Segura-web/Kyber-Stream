package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveStreamDao {
    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId")
    fun getLiveStreamsByCategory(categoryId: String): Flow<List<LiveStream>>

    // ¡NUEVO! Consulta para obtener todos los LiveStream.
    @Query("SELECT * FROM live_streams")
    fun getAllLiveStreams(): Flow<List<LiveStream>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(streams: List<LiveStream>)
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE categoryId = :categoryId")
    fun getMoviesByCategory(categoryId: String): Flow<List<Movie>>

    // ¡NUEVO! Consulta para obtener todas las películas
    @Query("SELECT * FROM movies")
    fun getAllMovies(): Flow<List<Movie>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<Movie>)
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE categoryId = :categoryId")
    fun getSeriesByCategory(categoryId: String): Flow<List<Series>>

    // ¡NUEVO! Consulta para obtener todas las series
    @Query("SELECT * FROM series")
    fun getAllSeries(): Flow<List<Series>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<Series>)
}
