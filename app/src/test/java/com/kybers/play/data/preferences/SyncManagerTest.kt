package com.kybers.play.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit

/**
 * Tests unitarios para SyncManager mejorado
 * Valida la funcionalidad de sincronización inteligente de EPG
 */
class SyncManagerTest {

    private val mockContext = mock<Context>()
    private val mockSharedPreferences = mock<SharedPreferences>()
    private val mockEditor = mock<SharedPreferences.Editor>()
    private val mockPreferenceManager = mock<PreferenceManager>()
    
    private lateinit var syncManager: SyncManager
    
    private val testUserId = 123
    private val currentTime = System.currentTimeMillis()

    @Before
    fun setup() {
        // Configurar mocks
        whenever(mockContext.getSharedPreferences(any(), eq(Context.MODE_PRIVATE)))
            .thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putLong(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putInt(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        
        // Configurar PreferenceManager para retornar 24 horas por defecto
        whenever(mockPreferenceManager.getSyncFrequency()).thenReturn(24)
        
        syncManager = SyncManager(mockContext, mockPreferenceManager)
    }

    @Test
    fun `isEpgSyncNeeded should return true when never synced`() {
        // Simular que nunca se ha sincronizado (timestamp = 0)
        whenever(mockSharedPreferences.getLong(argThat { contains("epg_last_sync_timestamp_$testUserId") }, eq(0L)))
            .thenReturn(0L)

        val result = syncManager.isEpgSyncNeeded(testUserId)
        
        assertTrue("Should need sync when never synced", result)
    }

    @Test
    fun `isEpgSyncNeeded should return false when recently synced`() {
        // Simular sincronización reciente (hace 1 hora)
        val oneHourAgo = currentTime - TimeUnit.HOURS.toMillis(1)
        whenever(mockSharedPreferences.getLong(argThat { contains("epg_last_sync_timestamp_$testUserId") }, eq(0L)))
            .thenReturn(oneHourAgo)

        val result = syncManager.isEpgSyncNeeded(testUserId)
        
        assertFalse("Should not need sync when recently synced", result)
    }

    @Test
    fun `isEpgSyncNeeded should return true when sync threshold exceeded`() {
        // Simular sincronización antigua (hace 25 horas, excede el umbral de 24h)
        val twentyFiveHoursAgo = currentTime - TimeUnit.HOURS.toMillis(25)
        whenever(mockSharedPreferences.getLong(argThat { contains("epg_last_sync_timestamp_$testUserId") }, eq(0L)))
            .thenReturn(twentyFiveHoursAgo)

        val result = syncManager.isEpgSyncNeeded(testUserId)
        
        assertTrue("Should need sync when threshold exceeded", result)
    }

    @Test
    fun `isEpgDataStale should return true when no EPG data exists`() {
        val result = syncManager.isEpgDataStale(testUserId, null)
        
        assertTrue("Should be stale when no EPG data exists", result)
    }

    @Test
    fun `isEpgDataStale should return false when EPG covers next 12+ hours`() {
        // Timestamp que cubre las próximas 24 horas
        val twentyFourHoursFromNow = (currentTime / 1000) + TimeUnit.HOURS.toSeconds(24)
        
        val result = syncManager.isEpgDataStale(testUserId, twentyFourHoursFromNow)
        
        assertFalse("Should not be stale when EPG covers sufficient time", result)
    }

    @Test
    fun `isEpgDataStale should return true when EPG coverage insufficient`() {
        // Timestamp que solo cubre las próximas 6 horas (menos del umbral de 12h)
        val sixHoursFromNow = (currentTime / 1000) + TimeUnit.HOURS.toSeconds(6)
        
        val result = syncManager.isEpgDataStale(testUserId, sixHoursFromNow)
        
        assertTrue("Should be stale when EPG coverage insufficient", result)
    }

    @Test
    fun `saveEpgLastSyncTimestamp should save timestamp and event count`() {
        val eventCount = 150
        
        syncManager.saveEpgLastSyncTimestamp(testUserId, eventCount)
        
        // Verificar que se guardó el timestamp
        verify(mockEditor).putLong(eq("epg_last_sync_timestamp_$testUserId"), any())
        // Verificar que se guardó el conteo de eventos
        verify(mockEditor).putInt(eq("epg_last_sync_timestamp_events_$testUserId"), eq(eventCount))
        verify(mockEditor, times(2)).apply()
    }

    @Test
    fun `saveEpgLastSyncTimestamp should only save timestamp when event count is zero`() {
        syncManager.saveEpgLastSyncTimestamp(testUserId, 0)
        
        // Verificar que se guardó el timestamp
        verify(mockEditor).putLong(eq("epg_last_sync_timestamp_$testUserId"), any())
        // Verificar que NO se guardó el conteo de eventos
        verify(mockEditor, never()).putInt(argThat { contains("events") }, any())
        verify(mockEditor).apply()
    }

    @Test
    fun `isSyncNeeded should respect user preference for sync frequency`() {
        // Configurar frecuencia de sincronización personalizada (6 horas)
        whenever(mockPreferenceManager.getSyncFrequency()).thenReturn(6)
        
        // Timestamp de hace 8 horas (debe necesitar sync)
        val eightHoursAgo = currentTime - TimeUnit.HOURS.toMillis(8)
        whenever(mockSharedPreferences.getLong(argThat { contains("last_sync_timestamp_$testUserId") }, eq(0L)))
            .thenReturn(eightHoursAgo)
        
        val result = syncManager.isSyncNeeded(testUserId)
        
        assertTrue("Should need sync when user frequency threshold exceeded", result)
    }

    @Test
    fun `isSyncNeeded should return false when user disabled sync`() {
        // Configurar frecuencia = 0 (nunca sincronizar)
        whenever(mockPreferenceManager.getSyncFrequency()).thenReturn(0)
        
        val result = syncManager.isSyncNeeded(testUserId)
        
        assertFalse("Should not need sync when user disabled auto-sync", result)
    }

    @Test
    fun `getOldestSyncTimestamp should return oldest timestamp among all content types`() {
        val moviesTimestamp = currentTime - TimeUnit.HOURS.toMillis(10)  // 10h ago
        val seriesTimestamp = currentTime - TimeUnit.HOURS.toMillis(5)   // 5h ago  
        val liveTimestamp = currentTime - TimeUnit.HOURS.toMillis(15)    // 15h ago (oldest)
        val generalTimestamp = currentTime - TimeUnit.HOURS.toMillis(8)  // 8h ago
        
        // Configurar mocks para timestamps específicos
        whenever(mockSharedPreferences.getLong(eq("movies_last_sync_timestamp_$testUserId"), eq(0L)))
            .thenReturn(moviesTimestamp)
        whenever(mockSharedPreferences.getLong(eq("series_last_sync_timestamp_$testUserId"), eq(0L)))
            .thenReturn(seriesTimestamp)
        whenever(mockSharedPreferences.getLong(eq("live_last_sync_timestamp_$testUserId"), eq(0L)))
            .thenReturn(liveTimestamp)
        whenever(mockSharedPreferences.getLong(eq("last_sync_timestamp_$testUserId"), eq(0L)))
            .thenReturn(generalTimestamp)
        
        val result = syncManager.getOldestSyncTimestamp(testUserId)
        
        assertEquals("Should return oldest timestamp", liveTimestamp, result)
    }

    @Test
    fun `getOldestSyncTimestamp should return 0 when no timestamps exist`() {
        // Configurar todos los timestamps como 0 (nunca sincronizado)
        whenever(mockSharedPreferences.getLong(any(), eq(0L))).thenReturn(0L)
        
        val result = syncManager.getOldestSyncTimestamp(testUserId)
        
        assertEquals("Should return 0 when no sync timestamps exist", 0L, result)
    }
}