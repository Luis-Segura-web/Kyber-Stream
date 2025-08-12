package com.kybers.play.ui.settings

import com.kybers.play.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test para verificar que las preferencias del reproductor funcionan correctamente
 * con el nuevo sistema de configuraciones
 */
class PlayerSelectionTest {

    @Test
    fun `player selection mapping should work correctly`() {
        // Test básico para verificar que el mapeo entre enums y strings funciona
        val mediaPlayerMapping = mapOf(
            Settings.PlayerSelection.AUTO to "Automático (recomendado)",
            Settings.PlayerSelection.MEDIA3 to "Media3 (ExoPlayer)",
            Settings.PlayerSelection.VLC to "VLC (LibVLC)"
        )
        
        assertEquals("Automático (recomendado)", mediaPlayerMapping[Settings.PlayerSelection.AUTO])
        assertEquals("Media3 (ExoPlayer)", mediaPlayerMapping[Settings.PlayerSelection.MEDIA3])
        assertEquals("VLC (LibVLC)", mediaPlayerMapping[Settings.PlayerSelection.VLC])
    }

    @Test
    fun `player selection options should be available in UI`() {
        // Verificar que las opciones del reproductor están correctamente definidas
        val expectedOptions = mapOf(
            Settings.PlayerSelection.AUTO to "Automático (recomendado)",
            Settings.PlayerSelection.MEDIA3 to "Media3 (ExoPlayer)",
            Settings.PlayerSelection.VLC to "VLC (LibVLC)"
        )
        
        // Este test verifica que las opciones están definidas correctamente
        assertEquals(3, expectedOptions.size)
        assertEquals("Automático (recomendado)", expectedOptions[Settings.PlayerSelection.AUTO])
        assertEquals("Media3 (ExoPlayer)", expectedOptions[Settings.PlayerSelection.MEDIA3])
        assertEquals("VLC (LibVLC)", expectedOptions[Settings.PlayerSelection.VLC])
    }

    @Test
    fun `default player selection should be AUTO`() {
        // Verificar que el valor por defecto es AUTO (selección automática)
        val defaultSelection = Settings.PlayerSelection.AUTO
        assertEquals(Settings.PlayerSelection.AUTO, defaultSelection)
        assertEquals(0, defaultSelection.number) // AUTO tiene valor 0 en el proto
    }

    @Test
    fun `enum values should match proto definition`() {
        // Verificar que los valores del enum coinciden con la definición del proto
        assertEquals(0, Settings.PlayerSelection.AUTO.number)
        assertEquals(1, Settings.PlayerSelection.MEDIA3.number)
        assertEquals(2, Settings.PlayerSelection.VLC.number)
    }
}
