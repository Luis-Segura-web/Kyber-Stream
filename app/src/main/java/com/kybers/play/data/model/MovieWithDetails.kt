package com.kybers.play.data.model

import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.remote.model.Movie

/**
 * Una clase de datos que une la información original de la Película
 * con sus detalles enriquecidos y cacheados. Esto nos permite pasar
 * un solo objeto a la UI con toda la información necesaria.
 */
data class MovieWithDetails(
    val movie: Movie,
    val details: MovieDetailsCache?
)
