package com.kybers.play.data.local

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.ColumnInfo
import com.kybers.play.data.remote.model.EpgEvent
import kotlinx.coroutines.flow.Flow

/**
 * Clase de datos para estadísticas de cobertura EPG.
 */
data class EpgCoverageStats(
    @ColumnInfo(name = "channels_with_epg") val channelsWithEpg: Int,
    @ColumnInfo(name = "total_events") val totalEvents: Int,
    @ColumnInfo(name = "earliest_event") val earliestEvent: Long,
    @ColumnInfo(name = "latest_event") val latestEvent: Long
)

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

    /**
     * --- ¡CONSULTAS OPTIMIZADAS PARA EPG! ---
     * Obtiene el evento actual para un canal específico (en vivo ahora).
     */
    @Query("""
        SELECT * FROM epg_events 
        WHERE channelId = :channelId AND userId = :userId 
        AND startTimestamp <= :currentTime AND stopTimestamp > :currentTime 
        LIMIT 1
    """)
    suspend fun getCurrentEventForChannel(channelId: Int, userId: Int, currentTime: Long): EpgEvent?

    /**
     * Obtiene el próximo evento para un canal específico.
     */
    @Query("""
        SELECT * FROM epg_events 
        WHERE channelId = :channelId AND userId = :userId 
        AND startTimestamp > :currentTime 
        ORDER BY startTimestamp ASC 
        LIMIT 1
    """)
    suspend fun getNextEventForChannel(channelId: Int, userId: Int, currentTime: Long): EpgEvent?

    /**
     * Obtiene eventos en un rango de tiempo para múltiples canales.
     * Útil para mostrar guía de programación.
     */
    @Query("""
        SELECT * FROM epg_events 
        WHERE channelId IN (:channelIds) AND userId = :userId 
        AND stopTimestamp > :startTime AND startTimestamp < :endTime 
        ORDER BY channelId, startTimestamp ASC
    """)
    suspend fun getEventsInTimeRange(
        channelIds: List<Int>, 
        userId: Int, 
        startTime: Long, 
        endTime: Long
    ): List<EpgEvent>

    /**
     * Obtiene los eventos actuales para múltiples canales de forma eficiente.
     */
    @Query("""
        SELECT * FROM epg_events 
        WHERE channelId IN (:channelIds) AND userId = :userId 
        AND startTimestamp <= :currentTime AND stopTimestamp > :currentTime
    """)
    suspend fun getCurrentEventsForChannels(channelIds: List<Int>, userId: Int, currentTime: Long): List<EpgEvent>

    /**
     * Obtiene los próximos eventos para múltiples canales.
     */
    @Query("""
        SELECT DISTINCT e1.* FROM epg_events e1
        INNER JOIN (
            SELECT channelId, MIN(startTimestamp) as next_start
            FROM epg_events 
            WHERE channelId IN (:channelIds) AND userId = :userId 
            AND startTimestamp > :currentTime
            GROUP BY channelId
        ) e2 ON e1.channelId = e2.channelId AND e1.startTimestamp = e2.next_start
        WHERE e1.userId = :userId
    """)
    suspend fun getNextEventsForChannels(channelIds: List<Int>, userId: Int, currentTime: Long): List<EpgEvent>

    /**
     * Búsqueda de eventos por título (case-insensitive).
     */
    @Query("""
        SELECT * FROM epg_events 
        WHERE userId = :userId AND title LIKE '%' || :searchQuery || '%'
        AND stopTimestamp > :currentTime
        ORDER BY startTimestamp ASC
        LIMIT :limit
    """)
    suspend fun searchEventsByTitle(userId: Int, searchQuery: String, currentTime: Long, limit: Int = 50): List<EpgEvent>

    /**
     * Obtiene estadísticas de cobertura EPG por usuario.
     */
    @Query("""
        SELECT COUNT(DISTINCT channelId) as channels_with_epg,
               COUNT(*) as total_events,
               MIN(startTimestamp) as earliest_event,
               MAX(stopTimestamp) as latest_event
        FROM epg_events 
        WHERE userId = :userId
    """)
    suspend fun getEpgCoverageStats(userId: Int): EpgCoverageStats?
}
