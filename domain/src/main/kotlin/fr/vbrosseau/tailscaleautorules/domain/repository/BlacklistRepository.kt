package fr.vbrosseau.tailscaleautorules.domain.repository

import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid
import kotlinx.coroutines.flow.Flow

/**
 * Liste des réseaux Wi-Fi de confiance.
 *
 * L'unicité est une règle métier, pas un détail de base : deux entrées
 * équivalentes rendraient la suppression ambiguë pour l'utilisateur. Elle
 * s'apprécie sur la forme canonique du SSID (voir `asSsidKey`), donc « Maison »
 * et « maison » sont un doublon.
 */
interface BlacklistRepository {
    /** Liste observable, ordonnée pour un affichage stable. */
    fun observeAll(): Flow<List<BlacklistedSsid>>

    /** Instantané des SSID, sous la forme attendue par le moteur de règles. */
    suspend fun currentSsids(): Set<String>

    /** Échoue avec [DuplicateSsidException] si un équivalent existe déjà. */
    suspend fun add(ssid: String): Result<Unit>

    /** Renomme une entrée. Échoue si le nouveau nom entre en conflit. */
    suspend fun update(
        id: Long,
        ssid: String,
    ): Result<Unit>

    suspend fun remove(id: Long)
}

/** Un SSID équivalent est déjà enregistré. */
class DuplicateSsidException(ssid: String) : Exception(
    "Le réseau « $ssid » figure déjà dans la liste.",
)
