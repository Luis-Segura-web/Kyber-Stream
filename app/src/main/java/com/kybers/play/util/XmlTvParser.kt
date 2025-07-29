package com.kybers.play.util

import android.util.Log
import android.util.Xml
import com.kybers.play.data.remote.model.EpgEvent
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * --- ¡PARSER OPTIMIZADO Y ROBUSTO! ---
 * Un parser para procesar archivos XMLTV y extraer la información de la EPG.
 * Ahora es más eficiente y resistente a diferentes formatos de fecha.
 */
object XmlTvParser {

    // ¡OPTIMIZACIÓN! Creamos las instancias de los parsers de fecha una sola vez.
    private val xmlTvDateFormatWithZone by lazy {
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            isLenient = false
        }
    }
    private val xmlTvDateFormatNoZone by lazy {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
    }

    fun parse(inputStream: InputStream, epgIdToStreamIdsMap: Map<String, List<Int>>, userId: Int): List<EpgEvent> {
        val events = mutableListOf<EpgEvent>()
        val xmlChannelIdsFound = mutableSetOf<String>()
        var successfulMatches = 0

        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentProgramme: TempProgramme? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "programme") {
                            val channelId = parser.getAttributeValue(null, "channel")
                            val start = parser.getAttributeValue(null, "start")
                            val stop = parser.getAttributeValue(null, "stop")
                            if (channelId != null && start != null && stop != null) {
                                val cleanChannelId = channelId.lowercase().trim()
                                xmlChannelIdsFound.add(cleanChannelId)
                                currentProgramme = TempProgramme(cleanChannelId, start, stop)
                            }
                        } else if (currentProgramme != null) {
                            when (parser.name) {
                                "title" -> currentProgramme.title = parser.nextText()
                                "desc" -> currentProgramme.desc = parser.nextText()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "programme" && currentProgramme != null) {
                            epgIdToStreamIdsMap[currentProgramme.channel]?.forEach { streamId ->
                                val startTimestamp = parseXmlTvDate(currentProgramme.start)
                                val stopTimestamp = parseXmlTvDate(currentProgramme.stop)
                                if (startTimestamp != null && stopTimestamp != null) {
                                    successfulMatches++
                                    events.add(
                                        EpgEvent(
                                            apiEventId = "${streamId}_${startTimestamp}",
                                            channelId = streamId,
                                            userId = userId,
                                            title = currentProgramme.title ?: "Sin título",
                                            description = currentProgramme.desc ?: "",
                                            startTimestamp = startTimestamp,
                                            stopTimestamp = stopTimestamp
                                        )
                                    )
                                }
                            }
                            currentProgramme = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("EPG_DEBUG", "Error durante el parsing del XML: ${e.message}")
            e.printStackTrace()
        } finally {
            inputStream.close()
        }

        Log.d("EPG_DEBUG", "Análisis del XML completado. Total de IDs de canal únicos (limpios) en XML: ${xmlChannelIdsFound.size}")
        if (xmlChannelIdsFound.isNotEmpty()) {
            Log.d("EPG_DEBUG", "Ejemplos de IDs de canal (limpios) del archivo XML: ${xmlChannelIdsFound.take(5)}")
        }
        Log.d("EPG_DEBUG", "Procesamiento finalizado. Número de eventos EPG creados (coincidencias exitosas): $successfulMatches")

        return events
    }

    /**
     * --- ¡LÓGICA DE PARSEO DE FECHAS MEJORADA! ---
     * Esta función ahora es mucho más resistente a formatos de fecha inesperados.
     */
    private fun parseXmlTvDate(dateString: String): Long? {
        // Intento 1: Formato con zona horaria (el más común y correcto).
        try {
            return xmlTvDateFormatWithZone.parse(dateString)?.time?.div(1000)
        } catch (e: Exception) {
            // Ignoramos el error y probamos el siguiente formato.
        }

        // Intento 2: Formato sin zona horaria (asumiendo UTC).
        try {
            return xmlTvDateFormatNoZone.parse(dateString)?.time?.div(1000)
        } catch (e: Exception) {
            // Si ambos fallan, lo registramos pero no rompemos la app.
            Log.w("XmlTvParser", "No se pudo parsear la fecha en ninguno de los formatos: '$dateString'")
        }

        return null
    }

    private data class TempProgramme(
        val channel: String,
        val start: String,
        val stop: String,
        var title: String? = null,
        var desc: String? = null
    )
}
