package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kybers.play.data.local.model.SeriesDetailsCache

/**
 * --- ¡NUEVO DAO! ---
 * DAO (Data Access Object) para la entidad SeriesDetailsCache.
 * Define los métodos para interactuar con la tabla 'series_details_cache' en la base de datos.
 */
@Dao
interface SeriesDetailsCacheDao {

    /**
     * Inserta o actualiza los detalles de una serie en la caché.
     * Si ya existen detalles para la misma seriesId, serán reemplazados.
     * @param details El objeto SeriesDetailsCache a guardar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(details: SeriesDetailsCache)

    /**
     * Obtiene los detalles cacheados de una serie específica por su ID.
     * @param seriesId El ID de la serie a buscar.
     * @return El objeto SeriesDetailsCache correspondiente o null si no se encuentra.
     */
    @Query("SELECT * FROM series_details_cache WHERE seriesId = :seriesId")
    suspend fun getBySeriesId(seriesId: Int): SeriesDetailsCache?

    /**
     * Obtiene todos los detalles de series cacheados.
     * @return Una lista con todos los objetos SeriesDetailsCache de la base de datos.
     */
    @Query("SELECT * FROM series_details_cache")
    suspend fun getAllDetails(): List<SeriesDetailsCache>
}
