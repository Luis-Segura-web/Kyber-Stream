package com.kybers.play.data.model

import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbMovieResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Una clase de datos que une la información original de la Película
 * con sus detalles enriquecidos y cacheados.
 * ¡AHORA CON FUNCIONES AUXILIARES INTELIGENTES!
 */
data class MovieWithDetails(
    val movie: Movie,
    val details: MovieDetailsCache?
) {
    // ¡CAMBIO CLAVE! Usamos Moshi para una gestión de JSON unificada.
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * ¡NUEVO Y CORREGIDO! Convierte el string JSON del reparto en una lista de objetos TMDbCastMember usando Moshi.
     * Si no hay datos, devuelve una lista vacía.
     */
    fun getCastList(): List<TMDbCastMember> {
        if (details?.castJson.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, TMDbCastMember::class.java)
            val adapter = moshi.adapter<List<TMDbCastMember>>(type)
            adapter.fromJson(details.castJson!!) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * ¡NUEVO Y CORREGIDO! Convierte el string JSON de recomendaciones en una lista de objetos TMDbMovieResult usando Moshi.
     * Si no hay datos, devuelve una lista vacía.
     */
    fun getRecommendationList(): List<TMDbMovieResult> {
        if (details?.recommendationsJson.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, TMDbMovieResult::class.java)
            val adapter = moshi.adapter<List<TMDbMovieResult>>(type)
            adapter.fromJson(details.recommendationsJson!!) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}