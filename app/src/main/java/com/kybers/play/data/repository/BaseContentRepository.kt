package com.kybers.play.data.repository

import com.kybers.play.data.remote.XtreamApiService
import com.kybers.play.data.remote.model.Category
import java.io.IOException

/**
 * --- ¡CLASE ABSTRACTA REFACTORIZADA! ---
 * Ahora es una clase abstracta con métodos abstractos. Esto elimina el código
 * que no se usaba y crea un contrato más estricto, forzando a todas las clases
 * hijas a implementar su propia lógica para obtener categorías.
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
}
