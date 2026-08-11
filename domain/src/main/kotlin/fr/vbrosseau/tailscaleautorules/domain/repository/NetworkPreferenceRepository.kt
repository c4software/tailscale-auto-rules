package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import kotlinx.coroutines.flow.Flow

/**
 * Gestes manuels mémorisés, un par réseau (SPECS.md §3.3 et §4.5).
 *
 * L'unicité par clé est une règle métier : deux exceptions sur le même réseau
 * seraient deux réponses contradictoires à la même question. Un nouveau geste
 * **remplace** donc l'entrée existante — jamais de suppression implicite ; on
 * ne revient au comportement automatique qu'en supprimant l'exception.
 */
interface NetworkPreferenceRepository {
    /** Liste observable, du geste le plus récent au plus ancien. */
    fun observeAll(): Flow<List<NetworkPreference>>

    /** Instantané sous la forme attendue par le moteur de règles. */
    suspend fun current(): Map<NetworkPreferenceKey, TunnelState>

    /**
     * Mémorise un geste, en remplaçant l'éventuelle exception du même réseau.
     *
     * L'horodatage est posé par l'implémentation, à partir de l'horloge
     * injectée : l'appelant n'a pas à connaître l'heure.
     */
    suspend fun upsert(
        key: NetworkPreferenceKey,
        ssid: String?,
        desiredState: TunnelState,
    )

    suspend fun remove(id: Long)
}
