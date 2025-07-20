package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kybers.play.data.local.model.MovieDetailsCache

/**
 * DAO (Data Access Object) para la entidad MovieDetailsCache.
 * Define los métodos para interactuar con la tabla 'movie_details_cache'.
 */
@Dao
interface MovieDetailsCacheDao {

    /**
     * Inserta o actualiza los detalles de una película en el caché.
     * Si ya existe una entrada con el mismo streamId, será reemplazada.
     * @param details El objeto MovieDetailsCache a guardar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(details: MovieDetailsCache)

    /**
     * Obtiene los detalles cacheados de una película por su streamId.
     * @param streamId El ID de la película a buscar.
     * @return El objeto MovieDetailsCache o null si no se encuentra en el caché.
     */
    @Query("SELECT * FROM movie_details_cache WHERE streamId = :streamId")
    suspend fun getByStreamId(streamId: Int): MovieDetailsCache?

    /**
     * Elimina una entrada específica del caché.
     * @param streamId El ID de la película cuyos detalles se van a eliminar.
     */
    @Query("DELETE FROM movie_details_cache WHERE streamId = :streamId")
    suspend fun deleteById(streamId: Int)
}
