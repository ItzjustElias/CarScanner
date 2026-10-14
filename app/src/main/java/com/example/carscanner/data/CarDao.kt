package com.example.carscanner.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class RarityCount(val rarity: String, val total: Int)
data class MakeCount(val make: String, val total: Int)

@Dao
interface CarDao {
    @Query(
        "SELECT * FROM cars WHERE userId = :userId " +
            "AND (:query = '' OR (make || ' ' || model) LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR note LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR id IN (SELECT carId FROM car_tags WHERE tag LIKE '%' || :query || '%' ESCAPE '\\')) " +
            "AND (:rarity = '' OR rarity = :rarity) " +
            "AND (:tag = '' OR id IN (SELECT carId FROM car_tags WHERE tag = :tag)) " +
            "ORDER BY CASE :sort WHEN 1 THEN points WHEN 2 THEN spottedAt ELSE scannedAt END DESC, id DESC"
    )
    fun pagingSource(userId: String, query: String, rarity: String, tag: String, sort: Int): PagingSource<Int, CarEntity>

    @Query("SELECT COUNT(*) FROM cars WHERE userId = :userId")
    fun observeCount(userId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(points), 0) FROM cars WHERE userId = :userId")
    fun observeTotalPoints(userId: String): Flow<Int>

    @Query("SELECT rarity, COUNT(*) AS total FROM cars WHERE userId = :userId GROUP BY rarity")
    fun observeRarityCounts(userId: String): Flow<List<RarityCount>>

    @Query("SELECT COALESCE(SUM(valueUsd), 0) FROM cars WHERE userId = :userId")
    fun observeTotalValue(userId: String): Flow<Long>

    @Query(
        "SELECT COUNT(*) FROM (SELECT DISTINCT LOWER(make) AS a, LOWER(model) AS b " +
            "FROM cars WHERE userId = :userId)"
    )
    fun observeUniqueModels(userId: String): Flow<Int>

    @Query("SELECT * FROM cars WHERE userId = :userId ORDER BY points DESC LIMIT 1")
    fun observeBestCar(userId: String): Flow<CarEntity?>

    @Query("SELECT * FROM cars WHERE userId = :userId AND valueUsd IS NOT NULL ORDER BY valueUsd DESC LIMIT 1")
    fun observePriciestCar(userId: String): Flow<CarEntity?>

    @Query(
        "SELECT make, COUNT(*) AS total FROM cars WHERE userId = :userId " +
            "GROUP BY make COLLATE NOCASE ORDER BY total DESC LIMIT 1"
    )
    fun observeTopMake(userId: String): Flow<MakeCount?>

    @Query("SELECT MIN(scannedAt) FROM cars WHERE userId = :userId")
    fun observeFirstScan(userId: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM cars WHERE userId = :userId AND scannedAt >= :since")
    fun observeScansSince(userId: String, since: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM cars WHERE userId = :userId " +
            "AND make = :make COLLATE NOCASE AND model = :model COLLATE NOCASE"
    )
    suspend fun countModel(userId: String, make: String, model: String): Int

    @Query(
        "SELECT COUNT(*) FROM cars WHERE userId = :userId AND id != :excludeId " +
            "AND make = :make COLLATE NOCASE AND model = :model COLLATE NOCASE"
    )
    suspend fun countModelExcluding(userId: String, make: String, model: String, excludeId: Long): Int

    @Query("UPDATE cars SET note = :note, spottedAt = :spottedAt WHERE id = :id")
    suspend fun updateDetails(id: Long, note: String, spottedAt: Long)

    @Update
    suspend fun update(car: CarEntity)

    // deleting last photo deletes hash with it I think (TODO: check this)
    @Query("SELECT * FROM cars WHERE userId = :userId AND photoHash = :hash LIMIT 1")
    suspend fun findByPhotoHash(userId: String, hash: String): CarEntity?

    @Query("SELECT tag FROM car_tags WHERE carId = :carId ORDER BY tag")
    fun observeTags(carId: Long): Flow<List<String>>

    @Query(
        "SELECT DISTINCT t.tag FROM car_tags t JOIN cars c ON c.id = t.carId " +
            "WHERE c.userId = :userId ORDER BY t.tag"
    )
    fun observeAllTags(userId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM car_tags WHERE carId = :carId")
    suspend fun countTags(carId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: CarTag)

    @Query("DELETE FROM car_tags WHERE carId = :carId AND tag = :tag")
    suspend fun deleteTag(carId: Long, tag: String)

    @Insert
    suspend fun insert(car: CarEntity): Long

    @Delete
    suspend fun delete(car: CarEntity)
}
