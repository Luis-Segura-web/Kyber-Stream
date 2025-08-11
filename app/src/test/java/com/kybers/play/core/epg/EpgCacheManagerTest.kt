package com.kybers.play.core.epg

import androidx.room.ColumnInfo
import com.kybers.play.data.local.EpgCoverageStats
import com.kybers.play.data.local.EpgEventDao
import com.kybers.play.data.remote.model.EpgEvent
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit

/**
 * Tests unitarios para EpgCacheManager
 * Valida la funcionalidad de cache inteligente para EPG
 */
class EpgCacheManagerTest {

    private val mockEpgEventDao = mock<EpgEventDao>()
    private lateinit var cacheManager: EpgCacheManager

    private val testUserId = 123
    private val testChannelId = 456
    private val currentTime = System.currentTimeMillis() / 1000

    private val sampleEvent = EpgEvent(
        apiEventId = "test_event_1",
        channelId = testChannelId,
        userId = testUserId,
        title = "Test Program",
        description = "Test Description",
        startTimestamp = currentTime - 3600, // 1 hora atrás
        stopTimestamp = currentTime + 3600   // 1 hora en el futuro
    )

    @Before
    fun setup() {
        cacheManager = EpgCacheManager(mockEpgEventDao)
    }

    @Test
    fun `getCurrentEventForChannel should return cached event on cache hit`() = runTest {
        // Configurar mock para primera llamada
        whenever(mockEpgEventDao.getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any()))
            .thenReturn(sampleEvent)

        // Primera llamada - debería consultar BD
        val firstResult = cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        assertEquals("First call should return event", sampleEvent, firstResult)

        // Segunda llamada - debería usar cache
        val secondResult = cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        assertEquals("Second call should return cached event", sampleEvent, secondResult)

        // Verificar que DAO solo fue llamado una vez
        verify(mockEpgEventDao, times(1)).getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any())
    }

    @Test
    fun `getCurrentEventForChannel should handle null events`() = runTest {
        // Configurar mock para retornar null
        whenever(mockEpgEventDao.getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any()))
            .thenReturn(null)

        val result = cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        assertNull("Should return null when no event exists", result)

        // Segunda llamada debería usar cache
        val secondResult = cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        assertNull("Second call should return cached null", secondResult)

        // Verificar que DAO solo fue llamado una vez
        verify(mockEpgEventDao, times(1)).getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any())
    }

    @Test
    fun `getCurrentEventsForChannels should return batch results`() = runTest {
        val channelIds = listOf(testChannelId, testChannelId + 1)
        val events = listOf(
            sampleEvent,
            sampleEvent.copy(channelId = testChannelId + 1, apiEventId = "test_event_2")
        )

        whenever(mockEpgEventDao.getCurrentEventsForChannels(eq(channelIds), eq(testUserId), any()))
            .thenReturn(events)

        val result = cacheManager.getCurrentEventsForChannels(channelIds, testUserId)

        assertEquals("Should return 2 events", 2, result.size)
        assertEquals("Should map event by channel ID", sampleEvent, result[testChannelId])
        assertEquals("Should map second event", events[1], result[testChannelId + 1])
    }

    @Test
    fun `getCurrentAndNextEventsForChannel should return both events in parallel`() = runTest {
        val nextEvent = sampleEvent.copy(
            apiEventId = "next_event",
            startTimestamp = currentTime + 3600,
            stopTimestamp = currentTime + 7200
        )

        whenever(mockEpgEventDao.getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any()))
            .thenReturn(sampleEvent)
        whenever(mockEpgEventDao.getNextEventForChannel(eq(testChannelId), eq(testUserId), any()))
            .thenReturn(nextEvent)

        val (current, next) = cacheManager.getCurrentAndNextEventsForChannel(testChannelId, testUserId)

        assertEquals("Should return current event", sampleEvent, current)
        assertEquals("Should return next event", nextEvent, next)

        // Verificar que ambas consultas fueron ejecutadas
        verify(mockEpgEventDao).getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any())
        verify(mockEpgEventDao).getNextEventForChannel(eq(testChannelId), eq(testUserId), any())
    }

    @Test
    fun `invalidateChannelCache should clear cache for specific channel`() = runTest {
        // Llenar cache
        whenever(mockEpgEventDao.getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any()))
            .thenReturn(sampleEvent)

        cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        verify(mockEpgEventDao, times(1)).getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any())

        // Invalidar cache
        cacheManager.invalidateChannelCache(testChannelId, testUserId)

        // Siguiente llamada debería consultar BD nuevamente
        cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        verify(mockEpgEventDao, times(2)).getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any())
    }

    @Test
    fun `invalidateUserCache should clear all cache for user`() = runTest {
        // Llenar cache con múltiples canales
        val otherChannelId = testChannelId + 100
        whenever(mockEpgEventDao.getCurrentEventForChannel(any(), eq(testUserId), any()))
            .thenReturn(sampleEvent)

        cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        cacheManager.getCurrentEventForChannel(otherChannelId, testUserId)

        // Invalidar todo el cache del usuario
        cacheManager.invalidateUserCache(testUserId)

        // Siguientes llamadas deberían consultar BD nuevamente
        cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        cacheManager.getCurrentEventForChannel(otherChannelId, testUserId)

        // Verificar llamadas adicionales después de invalidación
        verify(mockEpgEventDao, times(2)).getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any())
        verify(mockEpgEventDao, times(2)).getCurrentEventForChannel(eq(otherChannelId), eq(testUserId), any())
    }

    @Test
    fun `clearAllCache should reset all cache data`() = runTest {
        // Llenar cache
        whenever(mockEpgEventDao.getCurrentEventForChannel(eq(testChannelId), eq(testUserId), any()))
            .thenReturn(sampleEvent)

        cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        
        val statsBefore = cacheManager.getCacheStats()
        assertTrue("Cache should have entries", statsBefore.totalSize > 0)

        // Limpiar cache
        cacheManager.clearAllCache()

        val statsAfter = cacheManager.getCacheStats()
        assertEquals("Cache should be empty", 0, statsAfter.totalSize)
    }

    @Test
    fun `getCacheStats should return correct statistics`() = runTest {
        // Cache está vacío inicialmente
        val emptyStats = cacheManager.getCacheStats()
        assertEquals("Empty cache should have 0 size", 0, emptyStats.totalSize)

        // Agregar algunos eventos al cache
        whenever(mockEpgEventDao.getCurrentEventForChannel(any(), any(), any()))
            .thenReturn(sampleEvent)
        whenever(mockEpgEventDao.getNextEventForChannel(any(), any(), any()))
            .thenReturn(sampleEvent)

        cacheManager.getCurrentEventForChannel(testChannelId, testUserId)
        cacheManager.getNextEventForChannel(testChannelId, testUserId)

        val stats = cacheManager.getCacheStats()
        assertTrue("Cache should have entries", stats.totalSize > 0)
        assertTrue("Should have current events cached", stats.currentEventsSize > 0)
        assertTrue("Should have next events cached", stats.nextEventsSize > 0)
    }
}