package fr.vbrosseau.tailscaleautorules.domain.model

/**
 * SSID déclaré de confiance par l'utilisateur.
 *
 * [id] est attribué par la persistance : l'interface a besoin d'une identité
 * stable pour renommer ou supprimer une entrée, ce que la valeur textuelle ne
 * fournit pas — elle change précisément lors d'un renommage.
 */
data class BlacklistedSsid(
    val id: Long,
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Un SSID enregistré ne peut pas être vide." }
    }
}
