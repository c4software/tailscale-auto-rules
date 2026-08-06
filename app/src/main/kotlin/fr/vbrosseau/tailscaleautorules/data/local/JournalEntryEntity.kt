package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Changement d'état consigné.
 *
 * L'horodatage est indexé : c'est le seul critère de tri et de purge, et il est
 * consulté à chaque enregistrement.
 */
@Entity(
    tableName = "journal_entry",
    indices = [Index(value = ["epoch_millis"])],
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "epoch_millis")
    val epochMillis: Long,

    @ColumnInfo(name = "previous_state")
    val previousState: String,

    @ColumnInfo(name = "new_state")
    val newState: String,

    @ColumnInfo(name = "rule_id")
    val ruleId: String,
)
