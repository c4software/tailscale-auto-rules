package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.time.Clock
import fr.vbrosseau.tailscaleautorules.domain.time.FakeClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Préférences de réseau en mémoire.
 *
 * Le remplacement par clé et le refus de doublon au renommage sont appliqués
 * réellement — l'identité de l'entrée survit au nouveau geste — pour que les
 * tests en aval rencontrent le même comportement qu'en production.
 */
class FakeNetworkPreferenceRepository(private val clock: Clock = FakeClock()) : NetworkPreferenceRepository {
    private var nextId = 1L
    private val entries = MutableStateFlow(emptyList<NetworkPreference>())

    override fun observeAll(): Flow<List<NetworkPreference>> = entries.asStateFlow()

    override suspend fun current(): Map<NetworkPreferenceKey, TunnelState> =
        entries.value.associate { it.key to it.desiredState }

    override suspend fun upsert(
        key: NetworkPreferenceKey,
        ssid: String?,
        desiredState: TunnelState,
    ) {
        val existing = entries.value.firstOrNull { it.key == key }
        val entry =
            NetworkPreference(
                id = existing?.id ?: nextId++,
                key = key,
                ssid = ssid,
                desiredState = desiredState,
                epochMillis = clock.nowEpochMillis(),
            )
        entries.value =
            (listOf(entry) + entries.value.filterNot { it.key == key })
                .sortedByDescending { it.epochMillis }
    }

    /**
     * Pré-remplissage synchrone, pour les tests qui construisent leur décor
     * dans un initialiseur — là où une fonction suspendue n'a pas sa place.
     */
    fun seed(
        key: NetworkPreferenceKey,
        ssid: String?,
        desiredState: TunnelState,
    ) {
        val seeded =
            NetworkPreference(
                id = nextId++,
                key = key,
                ssid = ssid,
                desiredState = desiredState,
                epochMillis = clock.nowEpochMillis(),
            )
        entries.value = entries.value.filterNot { it.key == key } + seeded
    }

    override suspend fun update(
        id: Long,
        ssid: String,
    ): Result<Unit> {
        val trimmed = ssid.trim()
        val newKey = NetworkPreferenceKey.forWifi(trimmed)
        if (entries.value.any { it.id != id && it.key == newKey }) {
            return Result.failure(DuplicateSsidException(trimmed))
        }
        entries.value =
            entries.value.map { entry ->
                if (entry.id == id) entry.copy(key = newKey, ssid = trimmed) else entry
            }
        return Result.success(Unit)
    }

    override suspend fun remove(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }
}
