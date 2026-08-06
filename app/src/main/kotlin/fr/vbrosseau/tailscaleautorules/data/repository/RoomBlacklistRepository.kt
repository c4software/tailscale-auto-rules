package fr.vbrosseau.tailscaleautorules.data.repository

import android.database.sqlite.SQLiteConstraintException
import fr.vbrosseau.tailscaleautorules.data.local.BlacklistDao
import fr.vbrosseau.tailscaleautorules.data.local.BlacklistedSsidEntity
import fr.vbrosseau.tailscaleautorules.di.IoDispatcher
import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid
import fr.vbrosseau.tailscaleautorules.domain.model.asSsidKey
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.DuplicateSsidException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBlacklistRepository @Inject constructor(
    private val dao: BlacklistDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BlacklistRepository {

    override fun observeAll(): Flow<List<BlacklistedSsid>> = dao.observeAll()
        .map { entities -> entities.map { BlacklistedSsid(id = it.id, value = it.value) } }
        .flowOn(ioDispatcher)

    override suspend fun currentSsids(): Set<String> = withContext(ioDispatcher) {
        dao.getAll().map { it.value }.toSet()
    }

    override suspend fun add(ssid: String): Result<Unit> = withContext(ioDispatcher) {
        val trimmed = ssid.trim()
        insertOrFail(trimmed) {
            dao.insert(
                BlacklistedSsidEntity(value = trimmed, canonicalValue = trimmed.asSsidKey()),
            )
        }
    }

    override suspend fun update(id: Long, ssid: String): Result<Unit> = withContext(ioDispatcher) {
        val trimmed = ssid.trim()
        val existing = dao.findById(id)
            ?: return@withContext Result.failure(NoSuchElementException("Aucune entrée $id."))

        insertOrFail(trimmed) {
            dao.update(
                existing.copy(value = trimmed, canonicalValue = trimmed.asSsidKey()),
            )
        }
    }

    override suspend fun remove(id: Long) = withContext(ioDispatcher) {
        dao.deleteById(id)
    }

    /**
     * L'unicité est déléguée à l'index de la base plutôt que vérifiée avant
     * écriture : un contrôle applicatif laisserait une fenêtre entre la
     * lecture et l'insertion où deux ajouts concurrents passeraient tous deux.
     */
    private suspend fun insertOrFail(ssid: String, write: suspend () -> Unit): Result<Unit> = try {
        write()
        Result.success(Unit)
    } catch (_: SQLiteConstraintException) {
        Result.failure(DuplicateSsidException(ssid))
    }
}
