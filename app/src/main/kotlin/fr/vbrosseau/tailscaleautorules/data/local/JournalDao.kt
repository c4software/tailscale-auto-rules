package fr.vbrosseau.tailscaleautorules.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entry ORDER BY epoch_millis DESC, id DESC")
    fun observeRecent(): Flow<List<JournalEntryEntity>>

    @Query("SELECT COUNT(*) FROM journal_entry")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: JournalEntryEntity)

    /**
     * Supprime tout ce qui dépasse les [limit] entrées les plus récentes.
     *
     * Le tri de la sous-requête reprend exactement celui de [observeRecent] :
     * si les deux divergeaient, la purge pourrait supprimer une entrée que
     * l'utilisateur voit encore à l'écran.
     */
    @Query(
        """
        DELETE FROM journal_entry
        WHERE id NOT IN (
            SELECT id FROM journal_entry ORDER BY epoch_millis DESC, id DESC LIMIT :limit
        )
        """,
    )
    suspend fun trimTo(limit: Int)

    /**
     * Insère puis purge, en une seule transaction.
     *
     * Séparer les deux laisserait une fenêtre où deux enregistrements
     * concurrents dépasseraient la capacité sans jamais être purgés.
     */
    @Transaction
    suspend fun insertAndTrim(entity: JournalEntryEntity, limit: Int) {
        insert(entity)
        trimTo(limit)
    }

    @Query("DELETE FROM journal_entry")
    suspend fun clear()
}
