package com.kybers.play.data.repository

import android.util.Log
import com.kybers.play.BuildConfig
import com.kybers.play.data.local.ActorDetailsCacheDao
import com.kybers.play.data.local.EpisodeDetailsCacheDao
import com.kybers.play.data.local.MovieDetailsCacheDao
import com.kybers.play.data.local.SeriesDetailsCacheDao
import com.kybers.play.data.local.model.ActorDetailsCache
import com.kybers.play.data.local.model.EpisodeDetailsCache
import com.kybers.play.data.local.model.MovieDetailsCache
import com.kybers.play.data.local.model.SeriesDetailsCache
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
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

data class ActorFilmography(
    val biography: String?,
    val availableItems: List<FilmographyItem>,
    val unavailableItems: List<FilmographyItem>
)

class DetailsRepository(
    private val tmdbApiService: ExternalApiService,
    private val movieDetailsCacheDao: MovieDetailsCacheDao,
    private val seriesDetailsCacheDao: SeriesDetailsCacheDao,
    private val actorDetailsCacheDao: ActorDetailsCacheDao,
    private val episodeDetailsCacheDao: EpisodeDetailsCacheDao
) {

    private val moshi = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(FilmographyItem::class.java, "mediaType")
                .withSubtype(TMDbMovieResult::class.java, "movie")
                .withSubtype(TMDbTvResult::class.java, "tv")
        )
        .add(KotlinJsonAdapterFactory())
        .build()

    private val cacheExpiry = TimeUnit.DAYS.toMillis(7)

    suspend fun getMovieDetails(movie: Movie): MovieWithDetails {
        val cachedDetails = movieDetailsCacheDao.getByStreamId(movie.streamId)

        if (cachedDetails != null && System.currentTimeMillis() - cachedDetails.lastUpdated < cacheExpiry) {
            return MovieWithDetails(movie, cachedDetails)
        }

        val tmdbId = movie.tmdbId?.toIntOrNull() ?: return MovieWithDetails(movie, null)
        val fetchedDetails = fetchMovieFromTMDb(movie.streamId, tmdbId)
        fetchedDetails?.let { movieDetailsCacheDao.insertOrUpdate(it) }
        return MovieWithDetails(movie, fetchedDetails)
    }

    suspend fun getSeriesDetails(series: Series): SeriesDetailsCache? {
        val cachedDetails = seriesDetailsCacheDao.getBySeriesId(series.seriesId)

        if (cachedDetails != null && System.currentTimeMillis() - cachedDetails.lastUpdated < cacheExpiry) {
            return cachedDetails
        }

        val tmdbId = series.tmdbId?.toIntOrNull() ?: return null
        val fetchedDetails = fetchSeriesFromTMDb(series.seriesId, tmdbId)
        fetchedDetails?.let { seriesDetailsCacheDao.insertOrUpdate(it) }
        return fetchedDetails
    }

    suspend fun getEpisodeImageFromTMDb(tvId: Int, seasonNumber: Int, episodeNumber: Int, episodeId: String): String? {
        val cachedEpisode = episodeDetailsCacheDao.getByEpisodeId(episodeId)
        if (cachedEpisode != null && System.currentTimeMillis() - cachedEpisode.lastUpdated < cacheExpiry) {
            return cachedEpisode.imageUrl
        }

        return try {
            val response = tmdbApiService.getEpisodeDetailsTMDb(
                tvId = tvId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) {
                val imageUrl = response.body()?.getFullStillUrl()
                episodeDetailsCacheDao.insertOrUpdate(
                    EpisodeDetailsCache(
                        episodeId = episodeId,
                        imageUrl = imageUrl,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                imageUrl
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("DetailsRepository", "Fallo al obtener detalles del episodio de TMDB", e)
            null
        }
    }

    private suspend fun fetchMovieFromTMDb(streamId: Int, tmdbId: Int): MovieDetailsCache? {
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
                val similar: List<TMDbMovieResult> = details.similar?.results?.take(10) ?: emptyList()

                val castAdapter = moshi.adapter<List<TMDbCastMember>>(Types.newParameterizedType(List::class.java, TMDbCastMember::class.java))
                val recommendationsAdapter = moshi.adapter<List<TMDbMovieResult>>(Types.newParameterizedType(List::class.java, TMDbMovieResult::class.java))

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
                    similarJson = recommendationsAdapter.toJson(similar), // Usamos el mismo adaptador
                    collectionId = details.collection?.id,
                    collectionName = details.collection?.name,
                    lastUpdated = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e("DetailsRepository", "Fallo al obtener detalles de película de TMDb para ID $tmdbId: ${e.message}")
        }
        return null
    }

    private suspend fun fetchSeriesFromTMDb(seriesId: Int, tmdbId: Int): SeriesDetailsCache? {
        try {
            val response = tmdbApiService.getTvDetailsTMDb(
                tvId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
            if (response.isSuccessful) {
                val details = response.body() ?: return null

                val certification = findTvCertification(details)
                val castList: List<TMDbCastMember> = details.credits?.cast ?: emptyList()
                val recommendations: List<TMDbTvResult> = details.recommendations?.results?.take(10) ?: emptyList()

                val castAdapter = moshi.adapter<List<TMDbCastMember>>(Types.newParameterizedType(List::class.java, TMDbCastMember::class.java))
                val recommendationsAdapter = moshi.adapter<List<TMDbTvResult>>(Types.newParameterizedType(List::class.java, TMDbTvResult::class.java))

                return SeriesDetailsCache(
                    seriesId = seriesId,
                    tmdbId = tmdbId,
                    plot = details.overview,
                    posterUrl = details.getFullPosterUrl(),
                    backdropUrl = details.getFullBackdropUrl(),
                    firstAirYear = details.firstAirDate?.substringBefore("-"),
                    rating = details.voteAverage,
                    certification = certification,
                    castJson = castAdapter.toJson(castList),
                    recommendationsJson = recommendationsAdapter.toJson(recommendations),
                    lastUpdated = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e("DetailsRepository", "Fallo al obtener detalles de serie de TMDb para ID $tmdbId: ${e.message}")
        }
        return null
    }

    fun findMovieCertification(details: TMDbMovieDetails?): String? {
        val usRelease = details?.releaseDates?.results?.find { it.countryCode == "US" }
        return usRelease?.releaseDates?.find { it.type == 3 }?.certification?.takeIf { it.isNotBlank() }
            ?: usRelease?.releaseDates?.firstOrNull()?.certification?.takeIf { it.isNotBlank() }
    }

    fun findTvCertification(details: TMDbTvDetails?): String? {
        return details?.contentRatings?.results?.find { it.countryCode == "US" }?.rating?.takeIf { it.isNotBlank() }
    }

    suspend fun getTMDbDetails(tmdbId: Int): TMDbMovieDetails? {
        return try {
            val response = tmdbApiService.getMovieDetailsTMDb(movieId = tmdbId, apiKey = BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTMDbTvDetails(tvId: Int): TMDbTvDetails? {
        return try {
            val response = tmdbApiService.getTvDetailsTMDb(tvId = tvId, apiKey = BuildConfig.TMDB_API_KEY)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getActorFilmography(actorId: Int, allLocalMovies: List<Movie>, allLocalSeries: List<Series>): ActorFilmography = coroutineScope {
        val cachedActor = actorDetailsCacheDao.getByActorId(actorId)
        if (cachedActor != null && System.currentTimeMillis() - cachedActor.lastUpdated < cacheExpiry) {
            val type = Types.newParameterizedType(List::class.java, FilmographyItem::class.java)
            val adapter = moshi.adapter<List<FilmographyItem>>(type)
            val filmography = cachedActor.filmographyJson?.let { adapter.fromJson(it) } ?: emptyList()
            return@coroutineScope processFilmography(cachedActor.biography, filmography, allLocalMovies, allLocalSeries)
        }

        val biographyJob = async { tmdbApiService.getPersonDetails(actorId, BuildConfig.TMDB_API_KEY).body()?.biography }
        val movieCreditsJob = async { tmdbApiService.getPersonMovieCredits(actorId, BuildConfig.TMDB_API_KEY).body()?.cast ?: emptyList() }
        val tvCreditsJob = async { tmdbApiService.getPersonTvCredits(actorId, BuildConfig.TMDB_API_KEY).body()?.cast ?: emptyList() }

        val biography = biographyJob.await()
        val movieCredits = movieCreditsJob.await()
        val tvCredits = tvCreditsJob.await()

        val fullFilmography: List<FilmographyItem> = (movieCredits.map { it as FilmographyItem } + tvCredits.map { it as FilmographyItem }).distinctBy { it.id }

        val type = Types.newParameterizedType(List::class.java, FilmographyItem::class.java)
        val adapter = moshi.adapter<List<FilmographyItem>>(type)
        actorDetailsCacheDao.insertOrUpdate(
            ActorDetailsCache(
                actorId = actorId,
                biography = biography,
                filmographyJson = adapter.toJson(fullFilmography),
                lastUpdated = System.currentTimeMillis()
            )
        )

        return@coroutineScope processFilmography(biography, fullFilmography, allLocalMovies, allLocalSeries)
    }

    private fun processFilmography(biography: String?, filmography: List<FilmographyItem>, allLocalMovies: List<Movie>, allLocalSeries: List<Series>): ActorFilmography {
        val localMovieTmdbIds = allLocalMovies.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()
        val localSeriesTmdbIds = allLocalSeries.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()

        val (available, unavailable) = filmography.partition { item ->
            when (item.mediaType) {
                "movie" -> localMovieTmdbIds.contains(item.id)
                "tv" -> localSeriesTmdbIds.contains(item.id)
                else -> false
            }
        }
        return ActorFilmography(biography, available, unavailable)
    }

    suspend fun getAllCachedMovieDetailsMap(): Map<Int, MovieDetailsCache> {
        return movieDetailsCacheDao.getAllDetails().associateBy { it.streamId }
    }
}
