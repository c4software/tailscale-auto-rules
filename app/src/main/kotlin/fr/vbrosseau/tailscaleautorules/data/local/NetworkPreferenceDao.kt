package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkPreferenceDao {

    /** Du geste le plus récent au plus ancien, comme le journal. */
    @Query("SELECT * FROM network_exception ORDER BY epoch_millis DESC, id DESC")
    fun observeAll(): Flow<List<NetworkPreferenceEntity>>

    @Query("SELECT * FROM network_exception")
    suspend fun getAll(): List<NetworkPreferenceEntity>

    @Query("SELECT * FROM network_exception WHERE network_key = :networkKey")
    suspend fun findByKey(networkKey: String): NetworkPreferenceEntity?

    @Insert
    suspend fun insert(entity: NetworkPreferenceEntity)

    @Update
    suspend fun update(entity: NetworkPreferenceEntity)

    /**
     * Remplace l'exception du même réseau en conservant son identité.
     *
     * En une transaction : entre la recherche et l'écriture, un geste
     * concurrent ne doit pas pouvoir créer un doublon que l'index unique
     * transformerait en échec d'insertion.
     */
    @Transaction
    suspend fun upsertByKey(entity: NetworkPreferenceEntity) {
        val existing = findByKey(entity.networkKey)
        if (existing == null) insert(entity) else update(entity.copy(id = existing.id))
    }

    @Query("DELETE FROM network_exception WHERE id = :id")
    suspend fun deleteById(id: Long)
}
