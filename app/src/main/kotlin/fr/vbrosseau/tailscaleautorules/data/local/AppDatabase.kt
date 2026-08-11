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
    entities = [JournalEntryEntity::class, NetworkPreferenceEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
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

        /**
         * v3 : la blacklist et les exceptions fusionnent en préférences de
         * réseau (SPECS.md §4.2).
         *
         * Les exceptions sont copiées telles quelles ; la blacklist est versée
         * en « toujours coupé » — sa clé étant dérivée de la forme canonique
         * déjà en base — **sauf** là où un geste a déjà tranché : l'exception,
         * plus récente qu'une déclaration d'avant la fusion, gagne. Les
         * entrées migrées de la blacklist reçoivent l'horodatage zéro : une
         * volonté d'avant la fusion est, par construction, la plus ancienne.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `network_preference` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`network_key` TEXT NOT NULL, " +
                        "`ssid` TEXT, " +
                        "`desired_state` TEXT NOT NULL, " +
                        "`epoch_millis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_network_preference_network_key` " +
                        "ON `network_preference` (`network_key`)",
                )
                db.execSQL(
                    "INSERT INTO `network_preference` (`network_key`, `ssid`, `desired_state`, `epoch_millis`) " +
                        "SELECT `network_key`, `ssid`, `desired_state`, `epoch_millis` FROM `network_exception`",
                )
                db.execSQL(
                    "INSERT INTO `network_preference` (`network_key`, `ssid`, `desired_state`, `epoch_millis`) " +
                        "SELECT 'wifi:' || `canonical_value`, `value`, 'DISABLED', 0 FROM `blacklisted_ssid` " +
                        "WHERE 'wifi:' || `canonical_value` NOT IN " +
                        "(SELECT `network_key` FROM `network_preference`)",
                )
                db.execSQL("DROP TABLE `network_exception`")
                db.execSQL("DROP TABLE `blacklisted_ssid`")
            }
        }
    }
}
