package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La migration **fusionnante** : blacklist et exceptions deviennent des
 * préférences de réseau. L'enjeu est le conflit — un geste a pu déjà trancher
 * un réseau blacklisté, et c'est lui, plus récent qu'une déclaration d'avant
 * la fusion, qui doit gagner.
 */
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun blacklistAndExceptionsMergeIntoPreferences() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO blacklisted_ssid (value, canonical_value) VALUES ('Maison', 'maison')")
            execSQL("INSERT INTO blacklisted_ssid (value, canonical_value) VALUES ('Bureau', 'bureau')")
            // Le geste a déjà tranché « Maison » : tunnel maintenu actif.
            execSQL(
                "INSERT INTO network_exception (network_key, ssid, desired_state, epoch_millis) " +
                    "VALUES ('wifi:maison', 'Maison', 'ENABLED', 5000)",
            )
            execSQL(
                "INSERT INTO network_exception (network_key, ssid, desired_state, epoch_millis) " +
                    "VALUES ('cellular', NULL, 'DISABLED', 6000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        val rows = mutableMapOf<String, Pair<String, Long>>()
        db.query("SELECT network_key, desired_state, epoch_millis FROM network_preference").use { cursor ->
            while (cursor.moveToNext()) {
                rows[cursor.getString(0)] = cursor.getString(1) to cursor.getLong(2)
            }
        }

        assertEquals(3, rows.size)
        // Le geste, plus récent, gagne sur la déclaration d'avant la fusion.
        assertEquals("ENABLED" to 5000L, rows["wifi:maison"])
        assertEquals("DISABLED" to 6000L, rows["cellular"])
        // La blacklist sans geste devient « toujours coupé », la plus ancienne.
        assertEquals("DISABLED" to 0L, rows["wifi:bureau"])
    }

    @Test
    fun theOldTablesAreDropped() {
        helper.createDatabase(TEST_DB, 2).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name IN ('blacklisted_ssid', 'network_exception')",
        ).use { cursor ->
            assertTrue(!cursor.moveToFirst(), "Les anciennes tables ne doivent pas survivre.")
        }
    }

    @Test
    fun theWholeChainFromTheFirstVersionHolds() {
        // Un terminal resté en v1 migre d'un trait : 1 → 2 → 3.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO blacklisted_ssid (value, canonical_value) VALUES ('Maison', 'maison')")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
        )

        db.query("SELECT network_key, desired_state FROM network_preference").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("wifi:maison", cursor.getString(0))
            assertEquals("DISABLED", cursor.getString(1))
        }
    }

    private companion object {
        const val TEST_DB = "migration-2-3-test.db"
    }
}
