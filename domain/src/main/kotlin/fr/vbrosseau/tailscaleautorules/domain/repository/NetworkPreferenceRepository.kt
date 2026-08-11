package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import kotlinx.coroutines.flow.Flow

/**
 * Préférences de réseau, une par réseau (SPECS.md §4.2) : tunnel toujours
 * coupé — le réseau de confiance d'hier — ou toujours actif, l'absence valant
 * automatisme.
 *
 * Déclaration et apprentissage écrivent au même endroit : l'unicité par clé
 * est une règle métier — deux préférences sur le même réseau seraient deux
 * réponses contradictoires à la même question — et la dernière volonté gagne,
 * qu'elle vienne d'un geste ou de l'écran. On ne revient à l'automatisme
 * qu'en supprimant la préférence.
 */
interface NetworkPreferenceRepository {
    /** Liste observable, de la volonté la plus récente à la plus ancienne. */
    fun observeAll(): Flow<List<NetworkPreference>>

    /** Instantané sous la forme attendue par le moteur de règles. */
    suspend fun current(): Map<NetworkPreferenceKey, TunnelState>

    /**
     * Enregistre une volonté — déclarée ou apprise — en remplaçant l'éventuelle
     * préférence du même réseau.
     *
     * L'horodatage est posé par l'implémentation, à partir de l'horloge
     * injectée : l'appelant n'a pas à connaître l'heure.
     */
    suspend fun upsert(
        key: NetworkPreferenceKey,
        ssid: String?,
        desiredState: TunnelState,
    )

    /**
     * Renomme le réseau d'une préférence Wi-Fi.
     *
     * Échoue avec [DuplicateSsidException] si un équivalent canonique existe
     * déjà : le renommage ne doit jamais fusionner silencieusement deux
     * volontés.
     */
    suspend fun update(
        id: Long,
        ssid: String,
    ): Result<Unit>

    suspend fun remove(id: Long)
}

/** Un réseau équivalent est déjà enregistré. */
class DuplicateSsidException(ssid: String) : Exception(
    "Le réseau « $ssid » figure déjà dans la liste.",
)
