package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {

    /** Tri sur la forme canonique : l'ordre affiché ne dépend pas de la casse saisie. */
    @Query("SELECT * FROM blacklisted_ssid ORDER BY canonical_value ASC")
    fun observeAll(): Flow<List<BlacklistedSsidEntity>>

    @Query("SELECT * FROM blacklisted_ssid")
    suspend fun getAll(): List<BlacklistedSsidEntity>

    /** Lève une contrainte d'unicité si la forme canonique existe déjà. */
    @Insert
    suspend fun insert(entity: BlacklistedSsidEntity)

    @Update
    suspend fun update(entity: BlacklistedSsidEntity)

    @Query("SELECT * FROM blacklisted_ssid WHERE id = :id")
    suspend fun findById(id: Long): BlacklistedSsidEntity?

    @Query("DELETE FROM blacklisted_ssid WHERE id = :id")
    suspend fun deleteById(id: Long)
}
