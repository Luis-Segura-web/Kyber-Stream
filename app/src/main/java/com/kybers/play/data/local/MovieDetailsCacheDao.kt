package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kybers.play.data.local.model.MovieDetailsCache

@Dao
interface MovieDetailsCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(details: MovieDetailsCache)

    @Query("SELECT * FROM movie_details_cache WHERE streamId = :streamId")
    suspend fun getByStreamId(streamId: Int): MovieDetailsCache?

    // --- ¡NUEVA FUNCIÓN! ---
    // Devuelve todos los detalles cacheados en un mapa para un acceso rápido.
    // La clave del mapa será el streamId.
    @Query("SELECT * FROM movie_details_cache")
    suspend fun getAllDetails(): List<MovieDetailsCache>
}