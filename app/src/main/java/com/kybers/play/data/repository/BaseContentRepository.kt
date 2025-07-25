package com.kybers.play.data.repository

import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import java.io.IOException

/**
 * Clase base abstracta para los repositorios de contenido.
 *
 * Propósito:
 * 1.  Centralizar la lógica común que comparten otros repositorios (LiveRepository, VodRepository).
 * 2.  Evitar la duplicación de código (principio DRY - Don't Repeat Yourself).
 * 3.  Establecer un contrato base para los repositorios que obtienen contenido de la API de Xtream.
 *
 * @property xtreamApiService La instancia del servicio Retrofit para comunicarse con la API de Xtream.
 * Se declara como 'protected' para que solo las clases que hereden de esta
 * puedan acceder a ella.
 */
abstract class BaseContentRepository(
    protected val xtreamApiService: XtreamApiService
) {

    /**
     * Obtiene las categorías de los canales de TV en vivo desde el servidor.
     * Es una función 'open' para que, si en el futuro un repositorio necesita un comportamiento
     * especial, pueda sobrescribirla.
     *
     * @param user El nombre de usuario para la autenticación.
     * @param pass La contraseña para la autenticación.
     * @return Una lista de objetos [Category] o una lista vacía si ocurre un error de red.
     */
    open suspend fun getLiveCategories(user: String, pass: String): List<Category> {
        return try {
            // Realiza la llamada a la API de forma segura.
            val response = xtreamApiService.getLiveCategories(user = user, pass = pass)
            // Si la respuesta es exitosa, devuelve el cuerpo (la lista de categorías),
            // de lo contrario, devuelve una lista vacía.
            response.body() ?: emptyList()
        } catch (e: IOException) {
            // En caso de una excepción de red (ej. sin internet), devuelve una lista vacía.
            emptyList()
        }
    }

    /**
     * Obtiene las categorías de las películas (VOD) desde el servidor.
     *
     * @param user El nombre de usuario para la autenticación.
     * @param pass La contraseña para la autenticación.
     * @return Una lista de objetos [Category] o una lista vacía si ocurre un error.
     */
    open suspend fun getMovieCategories(user: String, pass: String): List<Category> {
        return try {
            val response = xtreamApiService.getMovieCategories(user = user, pass = pass)
            response.body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    /**
     * Obtiene las categorías de las series desde el servidor.
     *
     * @param user El nombre de usuario para la autenticación.
     * @param pass La contraseña para la autenticación.
     * @return Una lista de objetos [Category] o una lista vacía si ocurre un error.
     */
    open suspend fun getSeriesCategories(user: String, pass: String): List<Category> {
        return try {
            val response = xtreamApiService.getSeriesCategories(user = user, pass = pass)
            response.body() ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }
}
