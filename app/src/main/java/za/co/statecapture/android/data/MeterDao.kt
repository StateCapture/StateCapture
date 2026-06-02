package za.co.statecapture.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterDao {
    @Query("SELECT * FROM meters ORDER BY displayOrder ASC, name ASC")
    fun getAllMeters(): Flow<List<Meter>>

    @Query("SELECT * FROM meters WHERE id = :id LIMIT 1")
    fun getMeterById(id: Int): Flow<Meter?>

    @Query("SELECT * FROM meters WHERE isDefault = 1 LIMIT 1")
    fun getDefaultMeter(): Flow<Meter?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeter(meter: Meter): Long

    @Update
    suspend fun updateMeter(meter: Meter)

    @Update
    suspend fun updateMeters(meters: List<Meter>)

    @Delete
    suspend fun deleteMeter(meter: Meter)
}
