package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kybers.play.data.remote.model.Episode
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) para la entidad Episode.
 * Define los métodos para interactuar con la tabla 'episodes' en la base de datos.
 */
@Dao
interface EpisodeDao {

    /**
     * Inserta una lista de episodios en la base de datos.
     * Si un episodio con la misma clave primaria ya existe, será reemplazado.
     * @param episodes La lista de episodios a insertar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<Episode>)

    /**
     * Obtiene un Flow con todos los episodios de una serie específica para un usuario.
     * El Flow emitirá una nueva lista cada vez que los datos de los episodios cambien.
     * @param seriesId El ID de la serie a la que pertenecen los episodios.
     * @param userId El ID del usuario actual.
     * @return Un Flow que emite la lista de episodios.
     */
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND userId = :userId ORDER BY season, episodeNum ASC")
    fun getEpisodesForSeries(seriesId: Int, userId: Int): Flow<List<Episode>>

    /**
     * Elimina todos los episodios de una serie específica para un usuario.
     * @param seriesId El ID de la serie cuyos episodios se eliminarán.
     * @param userId El ID del usuario actual.
     */
    @Query("DELETE FROM episodes WHERE seriesId = :seriesId AND userId = :userId")
    suspend fun deleteAllBySeriesIdAndUserId(seriesId: Int, userId: Int)

    /**
     * Reemplaza todos los episodios de una serie para un usuario.
     * Esta operación se ejecuta dentro de una transacción para asegurar la consistencia de los datos.
     * Primero borra los episodios antiguos y luego inserta los nuevos.
     * @param episodes La nueva lista de episodios.
     * @param seriesId El ID de la serie.
     * @param userId El ID del usuario.
     */
    @Transaction
    suspend fun replaceAll(episodes: List<Episode>, seriesId: Int, userId: Int) {
        deleteAllBySeriesIdAndUserId(seriesId, userId)
        if (episodes.isNotEmpty()) {
            insertAll(episodes)
        }
    }
}
