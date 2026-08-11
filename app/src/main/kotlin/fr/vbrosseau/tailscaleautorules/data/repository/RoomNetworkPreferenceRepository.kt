package fr.vbrosseau.tailscaleautorules.data.repository

import fr.vbrosseau.tailscaleautorules.data.local.NetworkPreferenceDao
import fr.vbrosseau.tailscaleautorules.data.local.NetworkPreferenceEntity
import fr.vbrosseau.tailscaleautorules.di.IoDispatcher
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.repository.NetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomNetworkPreferenceRepository @Inject constructor(
    private val dao: NetworkPreferenceDao,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NetworkPreferenceRepository {

    override fun observeAll(): Flow<List<NetworkPreference>> = dao.observeAll()
        .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
        .flowOn(ioDispatcher)

    override suspend fun current(): Map<NetworkPreferenceKey, TunnelState> = withContext(ioDispatcher) {
        dao.getAll()
            .mapNotNull { it.toDomainOrNull() }
            .associate { it.key to it.desiredState }
    }

    override suspend fun upsert(
        key: NetworkPreferenceKey,
        ssid: String?,
        desiredState: TunnelState,
    ) = withContext(ioDispatcher) {
        dao.upsertByKey(
            NetworkPreferenceEntity(
                networkKey = key.value,
                ssid = ssid,
                desiredState = desiredState.name,
                epochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    override suspend fun remove(id: Long) = withContext(ioDispatcher) { dao.deleteById(id) }

    /**
     * Une ligne illisible est ignorée plutôt que fatale — même politique que le
     * journal : une base écrite par une version ultérieure ne doit ni faire
     * planter l'affichage, ni surtout laisser une ligne aberrante piloter le
     * tunnel.
     */
    private fun NetworkPreferenceEntity.toDomainOrNull(): NetworkPreference? {
        val state =
            TunnelState.entries.firstOrNull { it.name == desiredState }
                ?.takeIf { it != TunnelState.UNKNOWN }
                ?: return null

        return runCatching {
            NetworkPreference(
                id = id,
                key = NetworkPreferenceKey(networkKey),
                ssid = ssid,
                desiredState = state,
                epochMillis = epochMillis,
            )
        }.getOrNull()
    }
}
