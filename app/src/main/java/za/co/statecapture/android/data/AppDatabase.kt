package za.co.statecapture.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import androidx.room.TypeConverters

@Database(entities = [Meter::class, Purchase::class, TariffProviderEntity::class, TariffIndexEntity::class, Reminder::class], version = 9, exportSchema = false)
@TypeConverters(TariffConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun meterDao(): MeterDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun tariffDao(): TariffDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE purchases ADD COLUMN vatAmountCents REAL NOT NULL DEFAULT 0.0")
                db.execSQL("UPDATE purchases SET vatAmountCents = ROUND(amountCents * 0.15, 0) WHERE vatAmountCents = 0.0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Update old provider IDs to new ones in enriched tariffs.json
                db.execSQL("UPDATE meters SET providerId = 'tshwane_prepaid' WHERE providerId = 'tshwane_residential'")
                // Add other mappings if necessary
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tariff_providers (id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, color TEXT, periods TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tariff_providers ADD COLUMN officialUrl TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS tariff_index (id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, color TEXT, providerId TEXT NOT NULL, files TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, frequency TEXT NOT NULL, dayValue INTEGER NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, isEnabled INTEGER NOT NULL DEFAULT 1)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "statecapture_database"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
