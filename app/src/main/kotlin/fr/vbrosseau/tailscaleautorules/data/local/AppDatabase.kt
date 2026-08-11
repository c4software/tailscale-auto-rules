package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base locale de l'application.
 *
 * Elle ne contient que des **collections** : les préférences scalaires vivent
 * dans DataStore (SPECS.md §9). Les deux supports ne se recouvrent jamais.
 */
@Database(
    entities = [BlacklistedSsidEntity::class, JournalEntryEntity::class, NetworkPreferenceEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blacklistDao(): BlacklistDao
    abstract fun journalDao(): JournalDao
    abstract fun networkPreferenceDao(): NetworkPreferenceDao

    companion object {
        const val NAME = "tailscale-auto-rules.db"

        /**
         * v2 : table des exceptions dynamiques (SPECS.md §4.5).
         *
         * Le SQL reproduit exactement ce que Room génère pour l'entité — le
         * schéma versionné en fait foi, et le test de migration valide la
         * correspondance : une divergence ferait planter l'ouverture de la
         * base chez tous les utilisateurs qui migrent.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `network_exception` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`network_key` TEXT NOT NULL, " +
                        "`ssid` TEXT, " +
                        "`desired_state` TEXT NOT NULL, " +
                        "`epoch_millis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_network_exception_network_key` " +
                        "ON `network_exception` (`network_key`)",
                )
            }
        }
    }
}
