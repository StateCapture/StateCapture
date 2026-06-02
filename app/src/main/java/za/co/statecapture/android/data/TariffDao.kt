package za.co.statecapture.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TariffDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(providers: List<TariffProviderEntity>)

    @Query("SELECT * FROM tariff_providers")
    suspend fun getAllProviders(): List<TariffProviderEntity>

    @Query("SELECT * FROM tariff_providers WHERE id = :id")
    suspend fun getProviderById(id: String): TariffProviderEntity?

    @Query("SELECT COUNT(*) FROM tariff_providers")
    suspend fun getCount(): Int
}
