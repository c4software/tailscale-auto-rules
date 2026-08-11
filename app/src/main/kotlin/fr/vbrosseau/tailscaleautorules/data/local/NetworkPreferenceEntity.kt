package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Préférence de réseau en base.
 *
 * [networkKey] est la clé canonique du réseau (SPECS.md §4.2), indexée en
 * unique : c'est la base qui garantit « une volonté par réseau », et non une
 * vérification applicative que deux écritures concurrentes pourraient
 * contourner. [ssid] conserve le SSID tel que saisi ou diffusé, pour
 * l'affichage.
 */
@Entity(
    tableName = "network_preference",
    indices = [Index(value = ["network_key"], unique = true)],
)
data class NetworkPreferenceEntity(
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
