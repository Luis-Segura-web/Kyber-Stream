package com.kybers.play.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kybers.play.data.local.model.ActorDetailsCache
import com.kybers.play.data.local.model.CategoryCache
import com.kybers.play.data.local.model.EpisodeDetailsCache

/**
 * --- ¡NUEVO DAO! ---
 * DAO para la entidad ActorDetailsCache.
 * Define los métodos para interactuar con la tabla 'actor_details_cache'.
 */
@Dao
interface ActorDetailsCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(details: ActorDetailsCache)

    @Query("SELECT * FROM actor_details_cache WHERE actorId = :actorId")
    suspend fun getByActorId(actorId: Int): ActorDetailsCache?
}

/**
 * --- ¡NUEVO DAO! ---
 * DAO para la entidad EpisodeDetailsCache.
 * Define los métodos para interactuar con la tabla 'episode_details_cache'.
 */
@Dao
interface EpisodeDetailsCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(details: EpisodeDetailsCache)

    @Query("SELECT * FROM episode_details_cache WHERE episodeId = :episodeId")
    suspend fun getByEpisodeId(episodeId: String): EpisodeDetailsCache?

    @Query("SELECT * FROM episode_details_cache")
    suspend fun getAll(): List<EpisodeDetailsCache>
}

/**
 * --- ¡NUEVO DAO! ---
 * DAO para la entidad CategoryCache.
 * Define los métodos para interactuar con la tabla 'category_cache'.
 */
@Dao
interface CategoryCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(categoryCache: CategoryCache)

    @Query("SELECT * FROM category_cache WHERE userId = :userId AND type = :type LIMIT 1")
    suspend fun getCategories(userId: Int, type: String): CategoryCache?

    @Query("DELETE FROM category_cache WHERE userId = :userId AND type = :type")
    suspend fun deleteCategories(userId: Int, type: String)
}
