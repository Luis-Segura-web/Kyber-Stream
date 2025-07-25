package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.local.LiveStreamDao
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.util.XmlTvParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repositorio especializado en la gestión de contenido en vivo.
 * Se encarga de la lógica para los canales (LiveStream) y la guía de programación (EpgEvent).
 *
 * Hereda de [BaseContentRepository] para reutilizar la lógica de obtención de categorías.
 *
 * @property xtreamApiService La instancia del servicio Retrofit para la API de Xtream.
 * @property liveStreamDao El DAO para acceder a la tabla de canales en la base de datos.
 * @property epgEventDao El DAO para acceder a la tabla de eventos de la EPG.
 */
class LiveRepository(
    xtreamApiService: XtreamApiService,
    private val liveStreamDao: LiveStreamDao,
    private val epgEventDao: EpgEventDao
) : BaseContentRepository(xtreamApiService) {

    /**
     * Obtiene un Flow con todos los canales en vivo cacheados para un usuario.
     *
     * @param userId El ID del usuario.
     * @return Un [Flow] que emite la lista de [LiveStream].
     */
    fun getRawLiveStreams(userId: Int): Flow<List<LiveStream>> = liveStreamDao.getAllLiveStreams(userId)

    /**
     * Obtiene todos los eventos de la EPG para un usuario y los agrupa por ID de canal en un mapa.
     * Esta estrategia de "precarga en memoria" es muy eficiente para enriquecer los canales posteriormente.
     *
     * @param userId El ID del usuario.
     * @return Un [Map] donde la clave es el ID del canal (streamId) y el valor es la lista de sus [EpgEvent]s.
     */
    suspend fun getAllEpgMapForUser(userId: Int): Map<Int, List<EpgEvent>> {
        return epgEventDao.getAllEventsForUser(userId).groupBy { it.channelId }
    }

    /**
     * Enriquece una lista de canales con su información de EPG actual y siguiente.
     *
     * @param channels La lista de [LiveStream] a enriquecer.
     * @param epgMap El mapa precalculado con toda la información de la EPG.
     * @return La misma lista de canales, pero con las propiedades `currentEpgEvent` y `nextEpgEvent` pobladas.
     */
    fun enrichChannelsWithEpg(channels: List<LiveStream>, epgMap: Map<Int, List<EpgEvent>>): List<LiveStream> {
        val currentTime = System.currentTimeMillis() / 1000
        return channels.map { stream ->
            val epgEvents = epgMap[stream.streamId] ?: emptyList()
            if (epgEvents.isNotEmpty()) {
                // Busca el evento que se está emitiendo ahora mismo.
                stream.currentEpgEvent = epgEvents.find { it.startTimestamp <= currentTime && it.stopTimestamp > currentTime }
                // Busca el próximo evento en la programación.
                stream.nextEpgEvent = epgEvents.filter { it.startTimestamp > currentTime }.minByOrNull { it.startTimestamp }
            }
            stream // Devuelve el stream, ya sea enriquecido o no.
        }
    }

    /**
     * Descarga y cachea todos los canales en vivo desde el servidor para un usuario.
     *
     * @param user El nombre de usuario para la autenticación.
     * @param pass La contraseña para la autenticación.
     * @param userId El ID del usuario para asociar los datos.
     */
    suspend fun cacheLiveStreams(user: String, pass: String, userId: Int) {
        val liveCategories = getLiveCategories(user, pass)
        val allStreamsForUser = mutableListOf<LiveStream>()
        for (category in liveCategories) {
            try {
                val streams = xtreamApiService.getLiveStreams(
                    user = user,
                    pass = pass,
                    categoryId = category.categoryId.toInt()
                ).body()
                if (!streams.isNullOrEmpty()) {
                    streams.forEach { it.userId = userId }
                    allStreamsForUser.addAll(streams)
                }
            } catch (e: Exception) {
                Log.e("LiveRepository", "Error al descargar streams de la categoría ${category.categoryId}", e)
            }
        }
        liveStreamDao.replaceAll(allStreamsForUser, userId)
    }

    /**
     * Descarga, parsea y cachea los datos de la EPG (Guía de Programación Electrónica).
     *
     * @param user El nombre de usuario para la autenticación.
     * @param pass La contraseña para la autenticación.
     * @param userId El ID del usuario.
     */
    suspend fun cacheEpgData(user: String, pass: String, userId: Int) {
        try {
            // 1. Obtiene todos los canales que tenemos en la base de datos.
            val allStreams = liveStreamDao.getAllLiveStreams(userId).first()

            // 2. Crea un mapa que relaciona el `epg_channel_id` (del proveedor) con nuestro `streamId`.
            val epgIdToStreamIdsMap = allStreams
                .filter { !it.epgChannelId.isNullOrBlank() }
                .groupBy({ it.epgChannelId!!.lowercase().trim() }, { it.streamId })

            // 3. Descarga el archivo XMLTV.
            val response = xtreamApiService.getXmlTvEpg(user = user, pass = pass)
            if (response.isSuccessful && response.body() != null) {
                val inputStream = response.body()!!.byteStream()
                // 4. Parsea el XML y obtiene la lista de eventos.
                val allEpgEvents = XmlTvParser.parse(inputStream, epgIdToStreamIdsMap, userId)
                // 5. Guarda todos los eventos en la base de datos.
                epgEventDao.replaceAll(allEpgEvents, userId)
                Log.d("LiveRepository", "EPG cacheada. Se procesaron ${allEpgEvents.size} eventos.")
            }
        } catch (e: Exception) {
            Log.e("LiveRepository", "Error al cachear datos de EPG", e)
        }
    }
}
