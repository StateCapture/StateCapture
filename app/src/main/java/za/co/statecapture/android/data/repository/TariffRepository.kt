package za.co.statecapture.android.data.repository

import android.content.Context
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import retrofit2.Retrofit
import za.co.statecapture.android.data.AppDatabase
import za.co.statecapture.android.data.TariffIndexEntity
import za.co.statecapture.android.data.TariffProviderEntity
import za.co.statecapture.android.data.network.TariffApi
import za.co.statecapture.android.domain.model.TariffIndexItem
import za.co.statecapture.android.domain.model.TariffProvider
import za.co.statecapture.android.util.AppConstants
import java.time.LocalDate

class TariffRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val database = AppDatabase.getDatabase(context)
    private val tariffDao = database.tariffDao()

    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConstants.TARIFF_BASE_URL)
        .addConverterFactory(json.asConverterFactory(MediaType.parse("application/json")!!))
        .build()

    private val api = retrofit.create(TariffApi::class.java)

    // Per-provider in-memory cache
    private val providerCache = mutableMapOf<String, TariffProvider>()

    suspend fun ensureIndexLoaded() = withContext(Dispatchers.IO) {
        try {
            val response = api.getIndex()
            val entities = response.plans.map { item ->
                TariffIndexEntity(
                    id = item.id,
                    name = item.name,
                    type = item.type,
                    color = item.color,
                    providerId = item.providerId,
                    files = item.files
                )
            }
            tariffDao.upsertIndex(entities)
            // Delete any provider rows that are no longer present in the index
            val currentIds = response.plans.map { it.id }
            tariffDao.deleteProvidersNotIn(currentIds)
            // Clear cached providers – the index may have changed (new files/validity periods)
            providerCache.clear()
            // Store index timestamps for debugging and UI display
            try {
                val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                // Assuming the API response contains a field 'lastUpdated' matching the JSON 'last_updated'
                val lastUpdated = response.lastUpdated?.toString() ?: ""
                prefs.edit()
                    .putString(AppConstants.KEY_INDEX_LAST_UPDATED, lastUpdated)
                    .putLong(AppConstants.KEY_INDEX_DOWNLOAD_TIME, System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) {
                Log.e("TariffRepository", "Failed to store index timestamps", e)
            }
        } catch (e: Exception) {
            Log.e("TariffRepository", "Failed to load index from network", e)
            // It's okay if we fail, we'll just rely on the DB cache
        }
    }

    suspend fun getAllProviders(): List<TariffIndexItem> = getProvidersForCurrentFY()


    /**
     * Returns providers that have tariff data valid for the current financial year.
     * This filters index entries whose file validity period includes today's date.
     */
    suspend fun getProvidersForCurrentFY(): List<TariffIndexItem> = withContext(Dispatchers.IO) {
        // Ensure we have the latest index
        ensureIndexLoaded()
        val today = LocalDate.now()
        tariffDao.getIndex()
            .filter { indexItem ->
                indexItem.files.any { file ->
                    val validFrom = LocalDate.parse(file.validFrom)
                    val validTo = file.validTo?.let { LocalDate.parse(it) }
                    (today.isEqual(validFrom) || today.isAfter(validFrom)) &&
                            (validTo == null || today.isEqual(validTo) || today.isBefore(validTo))
                }
            }
            .map { it.toDomain() }
    }




    suspend fun getProvider(id: String, date: LocalDate = LocalDate.now()): TariffProvider? =
        withContext(Dispatchers.IO) {
            // Look up the required file path from the index
            var indexItem = tariffDao.getIndexItem(id)
            var effectiveId = id
            if (indexItem == null) {
                // Attempt to find a newer sub‑provider sharing the same base prefix
                val base = id.substringBeforeLast("_")
                val candidates = tariffDao.getIndex()
                    .filter { it.id.startsWith(base) }
                    .sortedByDescending { it.files.maxByOrNull { f -> f.validFrom }?.validFrom }
                if (candidates.isNotEmpty()) {
                    indexItem = candidates.first()
                    effectiveId = indexItem.id
                } else {
                    return@withContext null
                }
            }

            // Find the file that covers the requested date
            val fileEntry = indexItem.files.find { file ->
                val validFrom = LocalDate.parse(file.validFrom)
                val validTo = file.validTo?.let { LocalDate.parse(it) }

                (date.isEqual(validFrom) || date.isAfter(validFrom)) &&
                        (validTo == null || date.isEqual(validTo) || date.isBefore(validTo))
            } ?: indexItem.files.lastOrNull() // fallback to the latest known file if date is in the far future

            // Check cache / DB
            var entity = tariffDao.getProviderById(effectiveId)

            // If we found a specific file for this date, check if our local entity already has a period covering the date
            var hasPeriodForDate = false
            if (entity != null) {
                hasPeriodForDate = entity.periods.any { period ->
                    val validFrom = LocalDate.parse(period.validFrom)
                    val validTo = period.validTo?.let { LocalDate.parse(it) }
                    (date.isEqual(validFrom) || date.isAfter(validFrom)) &&
                            (validTo == null || date.isEqual(validTo) || date.isBefore(validTo))
                }
            }

            if (!hasPeriodForDate && fileEntry != null) {
                try {
                    // Download specific file
                    val fileData = api.getProviderFile(fileEntry.path)
                    val downloadedTariff = fileData.tariffs.find { it.id == id }

                    if (downloadedTariff != null) {
                        val officialUrl = fileData.officialUrl

                        if (entity == null) {
                            entity = TariffProviderEntity(
                                id = downloadedTariff.id,
                                name = downloadedTariff.name,
                                type = downloadedTariff.type,
                                color = downloadedTariff.color,
                                officialUrl = officialUrl,
                                periods = downloadedTariff.periods
                            )
                        } else {
                            // Merge periods: keep existing, add new ones (prevent exact duplicates by validFrom)
                            val existingFromDates = entity.periods.map { it.validFrom }.toSet()
                            val newPeriods = downloadedTariff.periods.filter { it.validFrom !in existingFromDates }
                            entity = entity.copy(
                                officialUrl = officialUrl ?: entity.officialUrl,
                                periods = (entity.periods + newPeriods).sortedBy { it.validFrom }
                            )
                        }
                        tariffDao.insertAll(listOf(entity))
                        providerCache[id] = entity.toDomain()
                    }
                } catch (e: Exception) {
                    Log.e("TariffRepository", "Failed to load provider file: ${fileEntry.path}", e)
                }
            }

            // Return from cache or DB
            providerCache[effectiveId]?.let { return@withContext it }
            entity?.toDomain()?.also {
                // Cache under both the effective id and the original request id for future lookups
                providerCache[effectiveId] = it
                if (effectiveId != id) providerCache[id] = it
            }
        }

    private fun TariffIndexEntity.toDomain() = TariffIndexItem(
        id = id,
        name = name,
        type = type,
        color = color,
        providerId = providerId,
        files = files
    )

    private fun TariffProviderEntity.toDomain() = TariffProvider(
        id = id,
        name = name,
        type = type,
        color = color,
        officialUrl = officialUrl,
        periods = periods
    )
}
