package com.kybers.play.util

import android.util.Log
import android.util.Xml
import com.kybers.play.data.remote.model.EpgEvent
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Un parser para procesar archivos XMLTV y extraer la información de la EPG.
 */
object XmlTvParser {

    private val xmlTvDateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    /**
     * ¡LÓGICA MEJORADA! Ahora acepta un mapa de epg_id a una LISTA de stream_ids.
     */
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
                            // Buscamos la lista de stream_ids que coinciden con el epg_id del XML.
                            epgIdToStreamIdsMap[currentProgramme.channel]?.forEach { streamId ->
                                successfulMatches++
                                val startTimestamp = parseXmlTvDate(currentProgramme.start)
                                val stopTimestamp = parseXmlTvDate(currentProgramme.stop)
                                if (startTimestamp != null && stopTimestamp != null) {
                                    // Creamos un evento EPG para CADA stream_id en la lista.
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
        }

        Log.d("EPG_DEBUG", "Análisis del XML completado. Total de IDs de canal únicos (limpios) en XML: ${xmlChannelIdsFound.size}")
        if (xmlChannelIdsFound.isNotEmpty()) {
            Log.d("EPG_DEBUG", "Ejemplos de IDs de canal (limpios) del archivo XML: ${xmlChannelIdsFound.take(5)}")
        }
        Log.d("EPG_DEBUG", "Procesamiento finalizado. Número de eventos EPG creados (considerando duplicados): $successfulMatches")

        return events
    }

    private fun parseXmlTvDate(dateString: String): Long? {
        return try {
            val date = xmlTvDateFormat.parse(dateString)
            date?.time?.div(1000)
        } catch (e: Exception) {
            null
        }
    }

    private data class TempProgramme(
        val channel: String,
        val start: String,
        val stop: String,
        var title: String? = null,
        var desc: String? = null
    )
}
