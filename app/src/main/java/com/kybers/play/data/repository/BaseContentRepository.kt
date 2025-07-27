package com.kybers.play.data.repository

import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import java.io.IOException

/**
 * --- ¡CLASE BASE ACTUALIZADA! ---
 * Ahora las funciones de obtención de categorías incluyen el userId
 * para ser consistentes con las clases hijas que implementan la caché.
 */
abstract class BaseContentRepository(
    protected val xtreamApiService: XtreamApiService
) {

    /**
     * Obtiene las categorías de los canales de TV en vivo desde el servidor.
     * @param userId El ID del usuario, necesario para la lógica de caché en las clases hijas.
     */
    open suspend fun getLiveCategories(user: String, pass: String, userId: Int): List<Category> {
        return try {
            val response = xtreamApiService.getLiveCategories(user = user, pass = pass)
            response.body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    /**
     * Obtiene las categorías de las películas (VOD) desde el servidor.
     * @param userId El ID del usuario, necesario para la lógica de caché en las clases hijas.
     */
    open suspend fun getMovieCategories(user: String, pass: String, userId: Int): List<Category> {
        return try {
            val response = xtreamApiService.getMovieCategories(user = user, pass = pass)
            response.body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    /**
     * Obtiene las categorías de las series desde el servidor.
     * @param userId El ID del usuario, necesario para la lógica de caché en las clases hijas.
     */
    open suspend fun getSeriesCategories(user: String, pass: String, userId: Int): List<Category> {
        return try {
            val response = xtreamApiService.getSeriesCategories(user = user, pass = pass)
            response.body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }
}
