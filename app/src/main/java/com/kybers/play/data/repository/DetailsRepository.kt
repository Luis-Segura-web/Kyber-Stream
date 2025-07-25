package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.BuildConfig
import com.kybers.play.data.local.MovieDetailsCacheDao
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.model.MovieWithDetails
import com.kybers.play.data.remote.ExternalApiService
import com.kybers.play.data.remote.model.FilmographyItem
import com.kybers.play.data.remote.model.Movie
import com.kybers.play.data.remote.model.Series
import com.kybers.play.data.remote.model.TMDbCastMember
import com.kybers.play.data.remote.model.TMDbMovieDetails
import com.kybers.play.data.remote.model.TMDbMovieResult
import com.kybers.play.data.remote.model.TMDbTvDetails
import com.kybers.play.data.remote.model.TMDbTvResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * --- ¡CLASE AÑADIDA! ---
 * Data class para agrupar la filmografía de un actor.
 * La movemos aquí desde el antiguo ContentRepository.
 */
data class ActorFilmography(
    val biography: String?,
    val availableItems: List<FilmographyItem>,
    val unavailableItems: List<FilmographyItem>
)

/**
 * Repositorio especializado en obtener y cachear detalles enriquecidos de APIs externas.
 * Su única responsabilidad es gestionar los metadatos de películas y series.
 *
 * @property tmdbApiService El servicio Retrofit para la API de TMDb.
 * @property movieDetailsCacheDao El DAO para interactuar con la caché de detalles en la base de datos.
 */
class DetailsRepository(
    private val tmdbApiService: ExternalApiService,
    private val movieDetailsCacheDao: MovieDetailsCacheDao
) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Obtiene los detalles de una película, primero desde la caché local y, si no existen
     * o han expirado, los busca en la API de TMDb.
     *
     * @param movie La película para la cual se quieren obtener los detalles.
     * @return Un objeto [MovieWithDetails] que contiene la película original y sus detalles cacheados.
     */
    suspend fun getMovieDetails(movie: Movie): MovieWithDetails {
        val cacheExpiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7) // Caché válido por 7 días
        val cachedDetails = movieDetailsCacheDao.getByStreamId(movie.streamId)

        if (cachedDetails != null && cachedDetails.lastUpdated > cacheExpiry) {
            Log.d("DetailsRepository", "Detalles para '${movie.name}' encontrados en caché.")
            return MovieWithDetails(movie, cachedDetails)
        }

        val tmdbId = movie.tmdbId?.toIntOrNull()
        if (tmdbId == null || tmdbId <= 0) {
            Log.w("DetailsRepository", "'${movie.name}' no tiene un TMDB ID válido.")
            return MovieWithDetails(movie, null)
        }

        Log.d("DetailsRepository", "Buscando detalles en TMDb para '${movie.name}' (TMDB ID: $tmdbId)")
        val fetchedDetails = fetchFromTMDbById(movie.streamId, tmdbId)

        fetchedDetails?.let {
            Log.d("DetailsRepository", "Guardando nuevos detalles de TMDb en caché para '${movie.name}'.")
            movieDetailsCacheDao.insertOrUpdate(it)
        }

        return MovieWithDetails(movie, fetchedDetails)
    }

    /**
     * Realiza la llamada a la API de TMDb para obtener los detalles de una película específica por su ID.
     *
     * @param streamId El ID interno de la película en nuestro sistema.
     * @param tmdbId El ID de la película en la base de datos de TMDb.
     * @return Un objeto [MovieDetailsCache] listo para ser guardado, o null si falla la obtención.
     */
    private suspend fun fetchFromTMDbById(streamId: Int, tmdbId: Int): MovieDetailsCache? {
        try {
            val response = tmdbApiService.getMovieDetailsTMDb(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) {
                val details = response.body() ?: return null

                val certification = findMovieCertification(details)
                val castList: List<TMDbCastMember> = details.credits?.cast ?: emptyList()
                val recommendations: List<TMDbMovieResult> = details.recommendations?.results?.take(10) ?: emptyList()

                // Adaptadores de Moshi para convertir las listas a JSON
                val castAdapter = moshi.adapter<List<TMDbCastMember>>(
                    Types.newParameterizedType(List::class.java, TMDbCastMember::class.java)
                )
                val recommendationsAdapter = moshi.adapter<List<TMDbMovieResult>>(
                    Types.newParameterizedType(List::class.java, TMDbMovieResult::class.java)
                )

                return MovieDetailsCache(
                    streamId = streamId,
                    tmdbId = tmdbId,
                    plot = details.overview,
                    posterUrl = details.getFullPosterUrl(),
                    backdropUrl = details.getFullBackdropUrl(),
                    releaseYear = details.releaseDate?.substringBefore("-"),
                    rating = details.voteAverage,
                    certification = certification,
                    castJson = castAdapter.toJson(castList),
                    recommendationsJson = recommendationsAdapter.toJson(recommendations),
                    lastUpdated = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e("DetailsRepository", "Fallo al obtener detalles de TMDb para ID $tmdbId: ${e.message}")
        }
        return null
    }

    /**
     * Busca la clasificación por edades (certification) más relevante (de EEUU) para una película.
     */
    fun findMovieCertification(details: TMDbMovieDetails?): String? {
        val usRelease = details?.releaseDates?.results?.find { it.countryCode == "US" }
        return usRelease?.releaseDates?.find { it.type == 3 }?.certification?.takeIf { it.isNotBlank() }
            ?: usRelease?.releaseDates?.firstOrNull()?.certification?.takeIf { it.isNotBlank() }
    }

    /**
     * Busca la clasificación por edades más relevante (de EEUU) para una serie.
     */
    fun findTvCertification(details: TMDbTvDetails?): String? {
        return details?.contentRatings?.results?.find { it.countryCode == "US" }?.rating?.takeIf { it.isNotBlank() }
    }

    /**
     * Obtiene los detalles de una película de TMDb directamente por su ID.
     */
    suspend fun getTMDbDetails(tmdbId: Int): TMDbMovieDetails? {
        return try {
            val response = tmdbApiService.getMovieDetailsTMDb(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("DetailsRepository", "Fallo en la llamada a TMDb para Movie ID $tmdbId: ${e.message}")
            null
        }
    }

    /**
     * Obtiene los detalles de una serie de TMDb directamente por su ID.
     */
    suspend fun getTMDbTvDetails(tvId: Int): TMDbTvDetails? {
        return try {
            val response = tmdbApiService.getTvDetailsTMDb(
                tvId = tvId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("DetailsRepository", "Fallo en la llamada a TMDb para TV ID $tvId: ${e.message}")
            null
        }
    }


    /**
     * Obtiene la filmografía completa (películas y series) y biografía de un actor.
     *
     * @param actorId El ID del actor en TMDb.
     * @param allLocalMovies Lista de todas las películas locales para cruzar datos.
     * @param allLocalSeries Lista de todas las series locales.
     * @return Un objeto [ActorFilmography] con los datos encontrados.
     */
    suspend fun getActorFilmography(actorId: Int, allLocalMovies: List<Movie>, allLocalSeries: List<Series>): ActorFilmography = coroutineScope {
        val biographyJob = async {
            try {
                tmdbApiService.getPersonDetails(actorId, BuildConfig.TMDB_API_KEY).body()?.biography
            } catch (e: Exception) { null }
        }
        val movieCreditsJob = async {
            try {
                tmdbApiService.getPersonMovieCredits(actorId, BuildConfig.TMDB_API_KEY).body()?.cast ?: emptyList()
            } catch (e: Exception) { emptyList<TMDbMovieResult>() }
        }
        val tvCreditsJob = async {
            try {
                tmdbApiService.getPersonTvCredits(actorId, BuildConfig.TMDB_API_KEY).body()?.cast ?: emptyList()
            } catch (e: Exception) { emptyList<TMDbTvResult>() }
        }

        val biography = biographyJob.await()
        val movieCredits = movieCreditsJob.await()
        val tvCredits = tvCreditsJob.await()

        val fullFilmography: List<FilmographyItem> = (movieCredits + tvCredits).distinctBy { it.id }

        // Mapeamos los TMDb IDs de nuestras películas y series locales para una búsqueda rápida.
        val localMovieTmdbIds = allLocalMovies.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()
        // val localSeriesTmdbIds = allLocalSeries.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet() // Descomentar cuando las series tengan tmdbId

        // Separamos la filmografía en disponible y no disponible en nuestro servicio.
        val (available, unavailable) = fullFilmography.partition { filmographyItem ->
            when (filmographyItem.mediaType) {
                "movie" -> localMovieTmdbIds.contains(filmographyItem.id)
                "tv" -> false // Cambiar a `localSeriesTmdbIds.contains(filmographyItem.id)` cuando esté disponible
                else -> false
            }
        }

        return@coroutineScope ActorFilmography(biography, available, unavailable)
    }

    /**
     * Devuelve un mapa con todos los detalles cacheados para un acceso rápido en memoria.
     * La clave del mapa será el streamId.
     */
    suspend fun getAllCachedMovieDetailsMap(): Map<Int, MovieDetailsCache> {
        return movieDetailsCacheDao.getAllDetails().associateBy { it.streamId }
    }
}
