package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base locale de l'application.
 *
 * Elle ne contient que des **collections** : les préférences scalaires vivent
 * dans DataStore (SPECS.md §9). Les deux supports ne se recouvrent jamais.
 */
@Database(
    entities = [BlacklistedSsidEntity::class, JournalEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blacklistDao(): BlacklistDao
    abstract fun journalDao(): JournalDao

    companion object {
        const val NAME = "tailscale-auto-rules.db"
    }
}
