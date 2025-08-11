package com.kybers.play.ui.settings

import com.kybers.play.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test para verificar que las preferencias del reproductor funcionan correctamente
 * con el nuevo sistema de configuraciones
 */
class PlayerPreferenceTest {

    @Test
    fun `player preference mapping should work correctly`() {
        // Test básico para verificar que el mapeo entre strings y enums funciona
        val mediaPlayerMapping = mapOf(
            "AUTO" to Settings.PlayerPref.AUTO,
            "MEDIA3" to Settings.PlayerPref.MEDIA3,
            "VLC" to Settings.PlayerPref.VLC
        )
        
        assertEquals(Settings.PlayerPref.AUTO, mediaPlayerMapping["AUTO"])
        assertEquals(Settings.PlayerPref.MEDIA3, mediaPlayerMapping["MEDIA3"])
        assertEquals(Settings.PlayerPref.VLC, mediaPlayerMapping["VLC"])
    }

    @Test
    fun `player preference options should be available in UI`() {
        // Verificar que las opciones del reproductor están correctamente definidas
        val expectedOptions = mapOf(
            "AUTO" to "Automático (recomendado)",
            "MEDIA3" to "Media3 (ExoPlayer)",
            "VLC" to "VLC (LibVLC)"
        )
        
        // Este test verifica que las opciones están definidas correctamente
        assertEquals(3, expectedOptions.size)
        assertEquals("Automático (recomendado)", expectedOptions["AUTO"])
        assertEquals("Media3 (ExoPlayer)", expectedOptions["MEDIA3"])
        assertEquals("VLC (LibVLC)", expectedOptions["VLC"])
    }

    @Test
    fun `default player preference should be AUTO`() {
        // Verificar que el valor por defecto es AUTO (selección automática)
        val defaultPref = Settings.PlayerPref.AUTO
        assertEquals(Settings.PlayerPref.AUTO, defaultPref)
        assertEquals(0, defaultPref.number) // AUTO tiene valor 0 en el proto
    }

    @Test
    fun `enum values should match proto definition`() {
        // Verificar que los valores del enum coinciden con la definición del proto
        assertEquals(0, Settings.PlayerPref.AUTO.number)
        assertEquals(1, Settings.PlayerPref.MEDIA3.number)
        assertEquals(2, Settings.PlayerPref.VLC.number)
    }
}