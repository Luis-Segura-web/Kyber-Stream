package com.kybers.play.data.repository

import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import com.kybers.play.data.remote.model.UserInfo

/**
 * --- ¡CLASE ABSTRACTA ACTUALIZADA! ---
 * Ahora incluye un método concreto para obtener la información del usuario,
 * que puede ser reutilizado por todos los repositorios hijos.
 */
abstract class BaseContentRepository(
    protected val xtreamApiService: XtreamApiService
) {

    /**
     * Obtiene las categorías de los canales de TV en vivo.
     * @param userId El ID del usuario, necesario para la lógica de caché.
     */
    abstract suspend fun getLiveCategories(user: String, pass: String, userId: Int): List<Category>

    /**
     * Obtiene las categorías de las películas (VOD).
     * @param userId El ID del usuario, necesario para la lógica de caché.
     */
    abstract suspend fun getMovieCategories(user: String, pass: String, userId: Int): List<Category>

    /**
     * Obtiene las categorías de las series.
     * @param userId El ID del usuario, necesario para la lógica de caché.
     */
    abstract suspend fun getSeriesCategories(user: String, pass: String, userId: Int): List<Category>

    /**
     * --- ¡NUEVO MÉTODO AÑADIDO! ---
     * Obtiene la información de la cuenta del usuario desde la API de Xtream Codes.
     * Es un método concreto (no abstracto) para que no tengamos que repetirlo.
     */
    suspend fun getUserInfo(user: String, pass: String): UserInfo? {
        return try {
            val response = xtreamApiService.getUserInfo(user, pass)
            if (response.isSuccessful) {
                response.body()?.userInfo
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
