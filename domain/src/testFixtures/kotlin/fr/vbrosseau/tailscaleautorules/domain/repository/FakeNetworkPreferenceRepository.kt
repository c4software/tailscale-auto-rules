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
 * Exceptions dynamiques en mémoire.
 *
 * Le remplacement par clé est appliqué réellement — l'identité de l'entrée
 * survit au nouveau geste — pour que les tests en aval rencontrent le même
 * comportement qu'en production.
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

    override suspend fun remove(id: Long) {
        entries.value = entries.value.filterNot { it.id == id }
    }
}
