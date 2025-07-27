package com.kybers.play.data.model

import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbMovieResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * --- ¡CLASE ACTUALIZADA! ---
 * Ahora tiene funciones de ayuda para acceder a toda la nueva información de la caché.
 */
data class MovieWithDetails(
    val movie: Movie,
    val details: MovieDetailsCache?
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

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

    fun getSimilarList(): List<TMDbMovieResult> {
        if (details?.similarJson.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, TMDbMovieResult::class.java)
            val adapter = moshi.adapter<List<TMDbMovieResult>>(type)
            adapter.fromJson(details.similarJson!!) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCollectionId(): Int? {
        return details?.collectionId
    }
}
