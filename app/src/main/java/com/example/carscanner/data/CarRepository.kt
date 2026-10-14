package com.example.carscanner.data

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.carscanner.domain.AnalysisOutcome
import com.example.carscanner.domain.CarScan
import com.example.carscanner.domain.Rarity
import com.example.carscanner.domain.SortMode
import com.example.carscanner.domain.PointsCalculator
import com.example.carscanner.domain.ScanException
import com.example.carscanner.domain.SearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface SaveOutcome {
    data class Saved(val car: CarEntity, val duplicate: Boolean) : SaveOutcome
    data object NoCar : SaveOutcome
}

data class CorrectionPreview(val scan: CarScan, val points: Int)

data class ProfileStats(
    val cars: Int = 0,
    val points: Int = 0,
    val rarityCounts: Map<Rarity, Int> = emptyMap(),
    val totalValue: Long = 0,
    val uniqueModels: Int = 0,
    val bestCar: CarEntity? = null,
    val priciestCar: CarEntity? = null,
    val topMake: String? = null,
    val topMakeCount: Int = 0,
    val firstScan: Long? = null,
    val scansThisWeek: Int = 0,
)

class CarRepository(
    private val context: Context,
    private val dao: CarDao,
    private val gemini: GeminiService,
) {
    fun pagedCars(userId: String, query: String, rarity: Rarity?, sort: SortMode): Flow<PagingData<CarEntity>> =
        Pager(PagingConfig(pageSize = 20, prefetchDistance = 10, enablePlaceholders = false)) {
            dao.pagingSource(userId, SearchQuery.escapeLike(query.trim()), rarity?.name ?: "", sort.ordinal)
        }.flow

    fun summary(userId: String): Flow<ProfileStats> = combine(
        dao.observeCount(userId),
        dao.observeTotalPoints(userId),
        dao.observeRarityCounts(userId),
        dao.observeTotalValue(userId),
        dao.observeUniqueModels(userId),
    ) { count, points, rarities, value, unique ->
        ProfileStats(
            cars = count,
            points = points,
            rarityCounts = rarities.associate { Rarity.parse(it.rarity) to it.total },
            totalValue = value,
            uniqueModels = unique,
        )
    }

    fun profileStats(userId: String): Flow<ProfileStats> {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val extras = combine(
            dao.observeBestCar(userId),
            dao.observePriciestCar(userId),
            dao.observeTopMake(userId),
            dao.observeFirstScan(userId),
            dao.observeScansSince(userId, weekAgo),
        ) { best, priciest, topMake, first, week ->
            ProfileStats(
                bestCar = best,
                priciestCar = priciest,
                topMake = topMake?.make,
                topMakeCount = topMake?.total ?: 0,
                firstScan = first,
                scansThisWeek = week,
            )
        }
        return combine(summary(userId), extras) { a, b ->
            a.copy(
                bestCar = b.bestCar,
                priciestCar = b.priciestCar,
                topMake = b.topMake,
                topMakeCount = b.topMakeCount,
                firstScan = b.firstScan,
                scansThisWeek = b.scansThisWeek,
            )
        }
    }

    suspend fun scan(userId: String, uri: Uri): SaveOutcome = withContext(Dispatchers.IO) {
        val bitmap = ImageUtils.loadBitmap(context, uri)
            ?: throw ScanException("Couldn't read that image.")

        when (val outcome = gemini.analyze(ImageUtils.toBase64Jpeg(bitmap))) {
            AnalysisOutcome.NotACar -> SaveOutcome.NoCar
            is AnalysisOutcome.Car -> {
                val scan = outcome.scan
                val duplicate = dao.countModel(userId, scan.make, scan.model) > 0
                val points = PointsCalculator.calculate(scan, duplicate)

                val file = File(context.filesDir, "scans/${UUID.randomUUID()}.jpg")
                ImageUtils.saveJpeg(bitmap, file)

                val entity = CarEntity(
                    userId = userId,
                    make = scan.make,
                    model = scan.model,
                    yearText = scan.yearText,
                    bodyStyle = scan.bodyStyle,
                    rarity = scan.rarity.name,
                    productionCount = scan.productionCount,
                    valueUsd = scan.valueUsd,
                    funFact = scan.funFact,
                    confidence = scan.confidence,
                    points = points,
                    imagePath = file.absolutePath,
                    scannedAt = System.currentTimeMillis(),
                )
                SaveOutcome.Saved(entity.copy(id = dao.insert(entity)), duplicate)
            }
        }
    }

    suspend fun lookupCorrection(car: CarEntity, query: String): CorrectionPreview = withContext(Dispatchers.IO) {
        when (val outcome = gemini.lookup(query)) {
            AnalysisOutcome.NotACar ->
                throw ScanException("Couldn't find that car. Try the make, model and year.")
            is AnalysisOutcome.Car -> {
                val scan = outcome.scan.copy(confidence = 1f) // the player confirmed it
                val duplicate = dao.countModelExcluding(car.userId, scan.make, scan.model, car.id) > 0
                CorrectionPreview(scan, PointsCalculator.calculate(scan, duplicate))
            }
        }
    }

    suspend fun applyCorrection(car: CarEntity, preview: CorrectionPreview): CarEntity = withContext(Dispatchers.IO) {
        val scan = preview.scan
        val updated = car.copy(
            make = scan.make,
            model = scan.model,
            yearText = scan.yearText,
            bodyStyle = scan.bodyStyle,
            rarity = scan.rarity.name,
            productionCount = scan.productionCount,
            valueUsd = scan.valueUsd,
            funFact = scan.funFact,
            confidence = scan.confidence,
            points = preview.points,
        )
        dao.update(updated)
        updated
    }

    suspend fun delete(car: CarEntity) = withContext(Dispatchers.IO) {
        dao.delete(car)
        File(car.imagePath).delete()
    }
}
