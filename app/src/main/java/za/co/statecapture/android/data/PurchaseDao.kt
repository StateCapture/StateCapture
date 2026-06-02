package za.co.statecapture.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insert(purchase: Purchase)

    @Update
    suspend fun update(purchase: Purchase)

    @Query("SELECT * FROM purchases WHERE meterId = :meterId ORDER BY timestamp DESC")
    fun getPurchasesForMeter(meterId: Int): Flow<List<Purchase>>
    
    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Query("" +
        "SELECT SUM(kwhYield) FROM purchases " +
        "WHERE meterId = :meterId " +
        "AND timestamp >= :startOfMonth"
    )
    suspend fun getMonthlyTotalKwh(meterId: Int, startOfMonth: Long): Double?

    @Query("" +
        "SELECT SUM(kwhYield) FROM purchases " +
        "WHERE meterId = :meterId " +
        "AND timestamp >= :startOfMonth " +
        "AND timestamp < :endOfMonth"
    )
    suspend fun getMonthlyTotalKwhBetween(meterId: Int, startOfMonth: Long, endOfMonth: Long): Double?

    @Query("SELECT SUM(amountCents) FROM purchases")
    fun getAllTimeTotalCents(): Flow<Double?>

    @Query("SELECT SUM(kwhYield) FROM purchases")
    fun getAllTimeTotalKwh(): Flow<Double?>

    @Query("SELECT SUM(vatAmountCents) FROM purchases")
    fun getAllTimeTotalVatCents(): Flow<Double?>

    @Query("SELECT SUM(amountCents) FROM purchases WHERE timestamp >= :since")
    fun getTotalCentsSince(since: Long): Flow<Double?>

    @Query("SELECT SUM(kwhYield) FROM purchases WHERE timestamp >= :since")
    fun getTotalKwhSince(since: Long): Flow<Double?>

    @Query("SELECT SUM(vatAmountCents) FROM purchases WHERE timestamp >= :since")
    fun getTotalVatCentsSince(since: Long): Flow<Double?>

    @Query("SELECT MIN(timestamp) FROM purchases")
    suspend fun getFirstPurchaseTimestamp(): Long?

    @Query("DELETE FROM purchases WHERE id = :purchaseId")
    suspend fun delete(purchaseId: Long)

    /**
     * Count of distinct calendar months (YYYY-MM) that have at least one purchase.
     * Used for the monthly average calculation on the Dashboard.
     */
    @Query("SELECT COUNT(DISTINCT strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch'))) FROM purchases")
    fun getDistinctMonthCount(): Flow<Int?>
}
