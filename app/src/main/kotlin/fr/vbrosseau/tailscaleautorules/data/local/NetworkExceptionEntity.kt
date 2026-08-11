package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Exception dynamique en base.
 *
 * [networkKey] est la clé canonique du réseau (SPECS.md §4.5), indexée en
 * unique : c'est la base qui garantit « une mémoire par réseau », et non une
 * vérification applicative que deux écritures concurrentes pourraient
 * contourner. [ssid] conserve le SSID tel que diffusé, pour l'affichage.
 */
@Entity(
    tableName = "network_exception",
    indices = [Index(value = ["network_key"], unique = true)],
)
data class NetworkExceptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "network_key")
    val networkKey: String,

    @ColumnInfo(name = "ssid")
    val ssid: String?,

    @ColumnInfo(name = "desired_state")
    val desiredState: String,

    @ColumnInfo(name = "epoch_millis")
    val epochMillis: Long,
)
