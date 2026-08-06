package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid
import fr.vbrosseau.tailscaleautorules.domain.model.asSsidKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Blacklist en mémoire.
 *
 * Elle applique réellement la règle d'unicité canonique : un test en aval qui
 * s'appuie dessus doit rencontrer le même comportement qu'en production, sans
 * quoi le Fake masquerait le bogue qu'il est censé aider à trouver.
 */
class FakeBlacklistRepository(initial: List<String> = emptyList()) : BlacklistRepository {
    private var nextId = 1L
    private val entries =
        MutableStateFlow(
            initial.map { BlacklistedSsid(id = nextId++, value = it) },
        )

    override fun observeAll(): Flow<List<BlacklistedSsid>> = entries.asStateFlow()

    override suspend fun currentSsids(): Set<String> = entries.value.map { it.value }.toSet()

    override suspend fun add(ssid: String): Result<Unit> {
        val trimmed = ssid.trim()
        if (isTaken(trimmed, exceptId = null)) {
            return Result.failure(DuplicateSsidException(trimmed))
        }
        entries.value += BlacklistedSsid(id = nextId++, value = trimmed)
        return Result.success(Unit)
    }

    override suspend fun update(
        id: Long,
        ssid: String,
    ): Result<Unit> {
        val trimmed = ssid.trim()
        if (isTaken(trimmed, exceptId = id)) {
            return Result.failure(DuplicateSsidException(trimmed))
        }
        entries.value =
            entries.value.map { entry ->
                if (entry.id == id) entry.copy(value = trimmed) else entry
            }
        return Result.success(Unit)
    }

    override suspend fun remove(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }

    private fun isTaken(
        ssid: String,
        exceptId: Long?,
    ): Boolean = entries.value.any { it.id != exceptId && it.value.asSsidKey() == ssid.asSsidKey() }
}
