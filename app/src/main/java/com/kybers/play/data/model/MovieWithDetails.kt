package com.kybers.play.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbMovieResult

/**
 * Una clase de datos que une la información original de la Película
 * con sus detalles enriquecidos y cacheados.
 * ¡AHORA CON FUNCIONES AUXILIARES INTELIGENTES!
 */
data class MovieWithDetails(
    val movie: Movie,
    val details: MovieDetailsCache?
) {
    /**
     * ¡NUEVO! Convierte el string JSON del reparto en una lista de objetos TMDbCastMember.
     * Si no hay datos, devuelve una lista vacía.
     */
    fun getCastList(): List<TMDbCastMember> {
        if (details?.castJson == null) return emptyList()
        return try {
            val type = object : TypeToken<List<TMDbCastMember>>() {}.type
            Gson().fromJson(details.castJson, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * ¡NUEVO! Convierte el string JSON de recomendaciones en una lista de objetos TMDbMovieResult.
     * Si no hay datos, devuelve una lista vacía.
     */
    fun getRecommendationList(): List<TMDbMovieResult> {
        if (details?.recommendationsJson == null) return emptyList()
        return try {
            val type = object : TypeToken<List<TMDbMovieResult>>() {}.type
            Gson().fromJson(details.recommendationsJson, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
