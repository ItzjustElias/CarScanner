package com.example.carscanner.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class RarityCount(val rarity: String, val total: Int)
data class MakeCount(val make: String, val total: Int)

@Dao
interface CarDao {
    @Query(
        "SELECT * FROM cars WHERE userId = :userId " +
                "AND (:query = '' OR (make || ' ' || model) LIKE '%' || :query || '%') " +
                "AND (:rarity = '' OR rarity = :rarity) " +
                "ORDER BY CASE WHEN :sort = 1 THEN points ELSE scannedAt END DESC, id DESC"
    )
    fun pagingSource(userId: String, query: String, rarity: String, sort: Int): PagingSource<Int, CarEntity>

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

    @Update
    suspend fun update(car: CarEntity)

    @Insert
    suspend fun insert(car: CarEntity): Long

    @Delete
    suspend fun delete(car: CarEntity)
}
