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
 * DAO (Data Access Object) para la entidad EpgEvent.
 * Define los métodos para interactuar con la tabla 'epg_events' en la base de datos.
 */
@Dao
interface EpgEventDao {

    /**
     * Inserta una lista de eventos EPG en la base de datos.
     * Si ya existe un evento con la misma clave primaria, lo reemplazará.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(epgEvents: List<EpgEvent>)

    /**
     * Obtiene todos los eventos EPG para un canal y usuario específicos,
     * ordenados por la hora de inicio.
     */
    @Query("SELECT * FROM epg_events WHERE channelId = :channelId AND userId = :userId ORDER BY startTimestamp ASC")
    fun getEpgEventsForChannel(channelId: Int, userId: Int): Flow<List<EpgEvent>>

    /**
     * Elimina todos los eventos EPG asociados a un usuario específico.
     */
    @Query("DELETE FROM epg_events WHERE userId = :userId")
    suspend fun deleteAllByUserId(userId: Int)

    /**
     * Transacción para limpiar y luego insertar nuevos eventos EPG para un usuario.
     */
    @Transaction
    suspend fun replaceAll(epgEvents: List<EpgEvent>, userId: Int) {
        Log.d("EpgEventDao", "replaceAll: Eliminando eventos EPG antiguos para userId: $userId")
        deleteAllByUserId(userId)
        Log.d("EpgEventDao", "replaceAll: Insertando ${epgEvents.size} nuevos eventos EPG para userId: $userId")
        if (epgEvents.isNotEmpty()) {
            insertAll(epgEvents)
        }
    }

    /**
     * ¡NUEVO! Función de diagnóstico para contar todos los eventos de un usuario.
     */
    @Query("SELECT COUNT(apiEventId) FROM epg_events WHERE userId = :userId")
    suspend fun getAllEventsCountForUser(userId: Int): Int

}
