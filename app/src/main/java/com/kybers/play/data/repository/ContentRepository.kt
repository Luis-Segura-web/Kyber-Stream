package com.kybers.play.data.repository

import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.LiveStream
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.XtreamResponse

/**
 * Repositorio para gestionar el contenido (canales, películas, series) desde la API de Xtream Codes.
 *
 * @param apiService La instancia de XtreamApiService configurada para el servidor de un usuario específico.
 */
open class ContentRepository(private val apiService: XtreamApiService) { // ¡CAMBIO AQUÍ! Ahora es 'open'

    open suspend fun authenticate(user: String, pass: String): XtreamResponse? { // También hacemos las funciones 'open' para mocks
        return try {
            val response = apiService.authenticate(user, pass)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * AÑADIDO: Obtiene las categorías de los canales en vivo.
     */
    open suspend fun getLiveCategories(user: String, pass: String): List<Category> {
        return try {
            val response = apiService.getLiveCategories(user, pass)
            response.body() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    open suspend fun getLiveStreams(user: String, pass: String, categoryId: Int): List<LiveStream> {
        return try {
            val response = apiService.getLiveStreams(user, pass, categoryId)
            response.body() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    open suspend fun getMovies(user: String, pass: String, categoryId: Int = 1): List<Movie> {
        return try {
            val response = apiService.getMovies(user, pass, categoryId)
            response.body() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    open suspend fun getSeries(user: String, pass: String, categoryId: Int = 1): List<Series> {
        return try {
            val response = apiService.getSeries(user, pass, categoryId)
            response.body() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
