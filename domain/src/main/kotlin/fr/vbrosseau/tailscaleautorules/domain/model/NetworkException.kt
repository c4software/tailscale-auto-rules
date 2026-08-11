package fr.vbrosseau.tailscaleautorules.domain.model

/**
 * Clé canonique identifiant un réseau pour les exceptions dynamiques
 * (SPECS.md §4.5).
 *
 * Le Wi-Fi est identifié par son SSID canonique ; le cellulaire n'expose aucun
 * identifiant, l'exception y est donc globale — une seule clé pour toutes les
 * données mobiles. Les autres situations n'ont pas de clé : sans identité
 * stable, une exception rejouerait un geste sur un réseau qui n'est pas celui
 * qui l'a vu naître.
 */
@JvmInline
value class NetworkExceptionKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Une clé de réseau ne peut pas être vide." }
    }

    companion object {
        /** Toutes les données mobiles, faute d'identifiant plus fin. */
        val Cellular = NetworkExceptionKey("cellular")

        /**
         * Dérive la clé du réseau courant, ou `null` s'il n'est pas
         * identifiable : réseau non validé, Wi-Fi sans SSID lisible, transport
         * non couvert. `null` interdit à la fois l'apprentissage et le rejeu.
         */
        fun from(context: NetworkContext): NetworkExceptionKey? {
            if (!context.isUsable) return null
            return when (context.transport) {
                NetworkTransport.WIFI -> context.ssid?.let { NetworkExceptionKey("wifi:${it.asSsidKey()}") }
                NetworkTransport.CELLULAR -> Cellular
                else -> null
            }
        }
    }
}

/**
 * Geste manuel mémorisé pour un réseau (SPECS.md §3.3).
 *
 * @param id attribué par la persistance ; identité stable pour la suppression.
 * @param key clé canonique du réseau, unique parmi les exceptions.
 * @param ssid SSID tel que diffusé, conservé pour l'affichage — la clé n'en
 *   garde que la forme canonique. `null` en cellulaire, qui n'a pas de nom.
 * @param desiredState état du tunnel que l'utilisateur a choisi sur ce réseau.
 * @param epochMillis date du dernier geste mémorisé.
 */
data class NetworkException(
    val id: Long,
    val key: NetworkExceptionKey,
    val ssid: String?,
    val desiredState: TunnelState,
    val epochMillis: Long,
) {
    init {
        require(desiredState != TunnelState.UNKNOWN) {
            "Une exception mémorise un choix ferme : ENABLED ou DISABLED, jamais UNKNOWN."
        }
        require((key == NetworkExceptionKey.Cellular) == (ssid == null)) {
            "Le SSID d'affichage accompagne exactement les clés Wi-Fi (key=${key.value}, ssid=$ssid)."
        }
        require(ssid == null || ssid.isNotBlank()) {
            "Un SSID renseigné ne peut pas être vide : utiliser null en cellulaire."
        }
    }
}
