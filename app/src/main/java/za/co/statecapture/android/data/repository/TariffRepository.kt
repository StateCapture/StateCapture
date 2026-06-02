package za.co.statecapture.android.data.repository

import android.content.Context
import za.co.statecapture.android.R
import za.co.statecapture.android.data.AppDatabase
import za.co.statecapture.android.data.TariffProviderEntity
import za.co.statecapture.android.domain.model.TariffData
import za.co.statecapture.android.domain.model.TariffProvider
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TariffRepository(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val database = AppDatabase.getDatabase(context)
    private val tariffDao = database.tariffDao()

    // Per-provider in-memory cache
    private val providerCache = mutableMapOf<String, TariffProvider>()

    suspend fun getTariffData(): TariffData = withContext(Dispatchers.IO) {
        // For backwards compatibility or when full list is needed
        val providers = getAllProviders()
        TariffData(version = "1.0", lastUpdated = "", providers = providers)
    }

    suspend fun getAllProviders(): List<TariffProvider> = withContext(Dispatchers.IO) {
        ensureDataLoaded()
        val entities = tariffDao.getAllProviders()
        entities.map { entity ->
            // Update cache while we're at it
            providerCache[entity.id] ?: entity.toDomain().also { providerCache[entity.id] = it }
        }
    }

    suspend fun getProvider(id: String): TariffProvider? = withContext(Dispatchers.IO) {
        // Check cache first
        providerCache[id]?.let { return@withContext it }

        ensureDataLoaded()
        val entity = tariffDao.getProviderById(id)
        entity?.toDomain()?.also {
            providerCache[id] = it
        }
    }

    private suspend fun ensureDataLoaded() {
        val inputStream = context.resources.openRawResource(R.raw.tariffs)
        val jsonString = InputStreamReader(inputStream).readText()
        val data = json.decodeFromString<TariffData>(jsonString)
        
        val prefs = context.getSharedPreferences("tariff_prefs", Context.MODE_PRIVATE)
        val storedLastUpdated = prefs.getString("last_updated", "")

        if (tariffDao.getCount() == 0 || data.lastUpdated != storedLastUpdated) {
            val entities = data.providers.map { it.toEntity() }
            tariffDao.insertAll(entities)
            prefs.edit().putString("last_updated", data.lastUpdated).apply()
            providerCache.clear()
        }
    }

    private fun TariffProvider.toEntity() = TariffProviderEntity(
        id = id,
        name = name,
        type = type,
        color = color,
        periods = periods
    )

    private fun TariffProviderEntity.toDomain() = TariffProvider(
        id = id,
        name = name,
        type = type,
        color = color,
        periods = periods
    )
}
