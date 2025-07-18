package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kybers.play.data.remote.model.EpgEvent
import kotlinx.coroutines.flow.Flow
import android.util.Log // Importar Log para mensajes de depuración

/**
 * DAO (Data Access Object) para la entidad EpgEvent.
 * Define los métodos para interactuar con la tabla 'epg_events' en la base de datos.
 */
@Dao
interface EpgEventDao {

    /**
     * Inserta una lista de eventos EPG en la base de datos.
     * Si ya existe un evento con la misma clave primaria (epgId y userId), lo reemplazará.
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
     * Esto es crucial para limpiar la caché EPG cuando un usuario cierra sesión o se sincroniza.
     */
    @Query("DELETE FROM epg_events WHERE userId = :userId")
    suspend fun deleteAllByUserId(userId: Int)

    /**
     * Elimina todos los eventos EPG para un canal específico de un usuario.
     * Útil si solo queremos refrescar la EPG de un canal.
     */
    @Query("DELETE FROM epg_events WHERE channelId = :channelId AND userId = :userId")
    suspend fun deleteEpgForChannel(channelId: Int, userId: Int)

    /**
     * ¡NUEVO! Transacción para limpiar y luego insertar nuevos eventos EPG para un usuario.
     * Esto asegura que la caché EPG de un usuario se reemplace completamente.
     */
    @Transaction
    suspend fun replaceAll(epgEvents: List<EpgEvent>, userId: Int) {
        Log.d("EpgEventDao", "replaceAll: Eliminando eventos EPG antiguos para userId: $userId")
        deleteAllByUserId(userId)
        Log.d("EpgEventDao", "replaceAll: Insertando ${epgEvents.size} nuevos eventos EPG para userId: $userId")
        if (epgEvents.isNotEmpty()) {
            insertAll(epgEvents)
        } else {
            Log.d("EpgEventDao", "replaceAll: No hay eventos EPG para insertar para userId: $userId")
        }
    }

    /**
     * ¡NUEVO! Transacción para limpiar y luego insertar nuevos eventos EPG para un canal específico de un usuario.
     * Esto es útil para actualizaciones más granulares de la EPG.
     */
    @Transaction
    suspend fun replaceEpgForChannel(epgEvents: List<EpgEvent>, channelId: Int, userId: Int) {
        Log.d("EpgEventDao", "replaceEpgForChannel: Eliminando eventos EPG antiguos para channelId $channelId, userId: $userId")
        deleteEpgForChannel(channelId, userId)
        Log.d("EpgEventDao", "replaceEpgForChannel: Insertando ${epgEvents.size} nuevos eventos EPG para channelId $channelId, userId: $userId")
        if (epgEvents.isNotEmpty()) {
            insertAll(epgEvents)
        } else {
            Log.d("EpgEventDao", "replaceEpgForChannel: No hay eventos EPG para insertar para channelId $channelId, userId: $userId")
        }
    }
}
