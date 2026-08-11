package fr.vbrosseau.tailscaleautorules.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Première migration du projet : elle s'éprouve contre les schémas versionnés,
 * pas sur parole. Une divergence entre le SQL de la migration et ce que Room
 * attend ferait planter l'ouverture de la base chez tous les utilisateurs qui
 * mettent à jour.
 */
@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun theMigrationPreservesDataAndCreatesTheExceptionTable() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO blacklisted_ssid (value, canonical_value) VALUES ('Maison', 'maison')")
            execSQL(
                "INSERT INTO journal_entry (epoch_millis, previous_state, new_state, rule_id) " +
                    "VALUES (1000, 'DISABLED', 'ENABLED', 'mobile-network')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT value FROM blacklisted_ssid").use { cursor ->
            assertTrue(cursor.moveToFirst(), "La blacklist doit survivre à la migration.")
            assertEquals("Maison", cursor.getString(0))
        }
        db.query("SELECT rule_id FROM journal_entry").use { cursor ->
            assertTrue(cursor.moveToFirst(), "Le journal doit survivre à la migration.")
            assertEquals("mobile-network", cursor.getString(0))
        }

        db.execSQL(
            "INSERT INTO network_exception (network_key, ssid, desired_state, epoch_millis) " +
                "VALUES ('cellular', NULL, 'DISABLED', 2000)",
        )
        db.query("SELECT COUNT(*) FROM network_exception").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun theUniqueIndexOnTheNetworkKeyIsCreatedByTheMigration() {
        // « Une mémoire par réseau » est garanti par la base : la migration
        // doit créer l'index unique, pas seulement la table.
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        db.execSQL(
            "INSERT INTO network_exception (network_key, ssid, desired_state, epoch_millis) " +
                "VALUES ('wifi:maison', 'Maison', 'ENABLED', 1000)",
        )

        assertFailsWith<SQLiteConstraintException> {
            db.execSQL(
                "INSERT INTO network_exception (network_key, ssid, desired_state, epoch_millis) " +
                    "VALUES ('wifi:maison', 'Maison', 'DISABLED', 2000)",
            )
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
