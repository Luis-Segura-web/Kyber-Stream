package com.kybers.play.data.local

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kybers.play.data.remote.model.EpgEvent
import kotlinx.coroutines.flow.Flow

/**
 * --- ¡DAO MEJORADO! ---
 * DAO (Data Access Object) para la entidad EpgEvent.
 * Ahora incluye una consulta optimizada para encontrar el último evento de la guía.
 */
@Dao
interface EpgEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(epgEvents: List<EpgEvent>)

    @Query("SELECT * FROM epg_events WHERE channelId = :channelId AND userId = :userId ORDER BY startTimestamp ASC")
    fun getEpgEventsForChannel(channelId: Int, userId: Int): Flow<List<EpgEvent>>

    @Query("SELECT * FROM epg_events WHERE userId = :userId")
    suspend fun getAllEventsForUser(userId: Int): List<EpgEvent>

    @Query("DELETE FROM epg_events WHERE userId = :userId")
    suspend fun deleteAllByUserId(userId: Int)

    @Transaction
    suspend fun replaceAll(epgEvents: List<EpgEvent>, userId: Int) {
        deleteAllByUserId(userId)
        if (epgEvents.isNotEmpty()) {
            insertAll(epgEvents)
        }
    }

    @Query("SELECT COUNT(apiEventId) FROM epg_events WHERE userId = :userId")
    suspend fun getAllEventsCountForUser(userId: Int): Int

    /**
     * --- ¡NUEVA FUNCIÓN OPTIMIZADA! ---
     * Busca la fecha de finalización (stopTimestamp) más lejana en el futuro.
     * Esto nos dice hasta cuándo tenemos información en la guía de canales.
     * @param userId El ID del usuario.
     * @return El timestamp más alto, o null si no hay eventos.
     */
    @Query("SELECT MAX(stopTimestamp) FROM epg_events WHERE userId = :userId")
    suspend fun getLatestStopTimestamp(userId: Int): Long?
}
