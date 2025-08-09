package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.data.local.CategoryCacheDao
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.local.LiveStreamDao
import com.kybers.play.data.local.model.CategoryCache
import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.EpgEvent
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.util.XmlTvParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.io.use
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

class LiveRepository(
    xtreamApiService: XtreamApiService,
    private val liveStreamDao: LiveStreamDao,
    private val epgEventDao: EpgEventDao,
    private val categoryCacheDao: CategoryCacheDao
) : BaseContentRepository(xtreamApiService) {

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val categoryCacheExpiry = TimeUnit.HOURS.toMillis(4)

    override suspend fun getLiveCategories(user: String, pass: String, userId: Int): List<Category> {
        val cached = categoryCacheDao.getCategories(userId, "live")
        if (cached != null && System.currentTimeMillis() - cached.lastUpdated < categoryCacheExpiry) {
            Log.d("LiveRepository", "Cargando categorías de TV en vivo desde la caché.")
            return try {
                val type = Types.newParameterizedType(List::class.java, Category::class.java)
                val adapter = moshi.adapter<List<Category>>(type)
                adapter.fromJson(cached.categoriesJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        Log.d("LiveRepository", "Cargando categorías de TV en vivo desde la red.")
        return try {
            val response = xtreamApiService.getLiveCategories(user = user, pass = pass)
            val categories = response.body() ?: emptyList()
            if (categories.isNotEmpty()) {
                val type = Types.newParameterizedType(List::class.java, Category::class.java)
                val adapter = moshi.adapter<List<Category>>(type)
                val json = adapter.toJson(categories)
                categoryCacheDao.insertOrUpdate(
                    CategoryCache(userId = userId, type = "live", categoriesJson = json, lastUpdated = System.currentTimeMillis())
                )
            }
            categories
        } catch (e: IOException) {
            emptyList()
        }
    }

    // --- ¡CORRECCIÓN DE COMPILACIÓN! ---
    // Métodos requeridos por la clase base. No aplican aquí, devolvemos listas vacías.
    override suspend fun getMovieCategories(user: String, pass: String, userId: Int): List<Category> {
        Log.w("LiveRepository", "getMovieCategories fue llamado en LiveRepository. Esto no debería ocurrir.")
        return emptyList()
    }

    override suspend fun getSeriesCategories(user: String, pass: String, userId: Int): List<Category> {
        Log.w("LiveRepository", "getSeriesCategories fue llamado en LiveRepository. Esto no debería ocurrir.")
        return emptyList()
    }

    suspend fun isEpgDataStale(userId: Int): Boolean {
        val latestTimestamp = epgEventDao.getLatestStopTimestamp(userId)
            ?: return true

        val twentyFourHoursFromNowInSeconds = (System.currentTimeMillis() / 1000) + TimeUnit.HOURS.toSeconds(24)

        val isStale = latestTimestamp < twentyFourHoursFromNowInSeconds
        if (isStale) {
            Log.d("LiveRepository", "EPG DESACTUALIZADA. El último programa termina antes del umbral de 24 horas.")
        } else {
            Log.d("LiveRepository", "EPG FRESCA. Aún hay programación para las próximas 24 horas.")
        }
        return isStale
    }

    fun getRawLiveStreams(userId: Int): Flow<List<LiveStream>> = liveStreamDao.getAllLiveStreams(userId)

    suspend fun getAllEpgMapForUser(userId: Int): Map<Int, List<EpgEvent>> {
        return epgEventDao.getAllEventsForUser(userId).groupBy { it.channelId }
    }

    fun enrichChannelsWithEpg(channels: List<LiveStream>, epgMap: Map<Int, List<EpgEvent>>): List<LiveStream> {
        val currentTime = System.currentTimeMillis() / 1000
        return channels.map { stream ->
            val epgEvents = epgMap[stream.streamId] ?: emptyList()
            if (epgEvents.isNotEmpty()) {
                stream.currentEpgEvent = epgEvents.find { it.startTimestamp <= currentTime && it.stopTimestamp > currentTime }
                stream.nextEpgEvent = epgEvents.filter { it.startTimestamp > currentTime }.minByOrNull { it.startTimestamp }
            }
            stream
        }
    }

    suspend fun cacheLiveStreams(user: String, pass: String, userId: Int) {
        val liveCategories = getLiveCategories(user, pass, userId)
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

    suspend fun cacheEpgData(user: String, pass: String, userId: Int) {
        try {
            val allStreams = liveStreamDao.getAllLiveStreams(userId).first()
            val epgIdToStreamIdsMap = allStreams
                .filter { !it.epgChannelId.isNullOrBlank() }
                .groupBy({ it.epgChannelId!!.lowercase().trim() }, { it.streamId })

            val response = xtreamApiService.getXmlTvEpg(user = user, pass = pass)
            if (response.isSuccessful) {
                response.body()?.byteStream()?.use { input ->
                    val allEpgEvents = XmlTvParser.parse(input, epgIdToStreamIdsMap, userId)
                    epgEventDao.replaceAll(allEpgEvents, userId)
                    Log.d("LiveRepository", "EPG cacheada. Se procesaron ${allEpgEvents.size} eventos.")
                }
            }
        } catch (e: Exception) {
            Log.e("LiveRepository", "Error al cachear datos de EPG", e)
        }
    }
}
