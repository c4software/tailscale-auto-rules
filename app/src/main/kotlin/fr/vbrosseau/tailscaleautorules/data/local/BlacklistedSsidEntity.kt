package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SSID de confiance en base.
 *
 * [canonicalValue] est la forme normalisée de [value], stockée et indexée en
 * unique. C'est la base qui garantit alors l'unicité, et non une vérification
 * applicative que deux écritures concurrentes pourraient contourner.
 */
@Entity(
    tableName = "blacklisted_ssid",
    indices = [Index(value = ["canonical_value"], unique = true)],
)
data class BlacklistedSsidEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "canonical_value")
    val canonicalValue: String,
)
