package fr.vbrosseau.tailscaleautorules.domain.model

/**
 * Instantané de l'état observable du terminal, seule entrée du moteur de règles.
 *
 * C'est une valeur immuable et auto-cohérente : une règle n'a jamais besoin
 * d'interroger le système, et deux contextes égaux produisent nécessairement la
 * même décision.
 *
 * @param transport transport porteur de la connexion, [NetworkTransport.NONE]
 *   s'il n'y en a aucun.
 * @param isAirplaneModeOn mode avion actif. Indépendant de [transport] : un
 *   Wi-Fi peut rester actif en mode avion.
 * @param isInternetValidated le système a confirmé un accès Internet effectif.
 *   Distingue « associé au réseau » de « réseau utilisable ».
 * @param ssid SSID du réseau Wi-Fi, ou `null` s'il est indisponible (permission
 *   absente, réseau masqué). `null` signifie « inconnu », jamais « aucun ».
 */
data class NetworkContext(
    val transport: NetworkTransport,
    val isAirplaneModeOn: Boolean = false,
    val isInternetValidated: Boolean = false,
    val ssid: String? = null,
) {
    init {
        require(ssid == null || transport == NetworkTransport.WIFI) {
            "Un SSID ne peut être renseigné que sur un transport Wi-Fi (transport=$transport)."
        }
        require(ssid == null || ssid.isNotBlank()) {
            "Un SSID renseigné ne peut pas être vide : utiliser null lorsqu'il est indisponible."
        }
    }

    /** Vrai dès qu'un transport porte la connexion, même sans accès Internet confirmé. */
    val isConnected: Boolean get() = transport != NetworkTransport.NONE

    /**
     * Vrai lorsque le réseau est réellement exploitable.
     *
     * C'est le critère retenu par SPECS.md §4.4 : un réseau associé mais non
     * validé est traité comme une absence de réseau, et ne déclenche donc
     * aucune décision.
     */
    val isUsable: Boolean get() = isConnected && isInternetValidated

    companion object {
        /** Contexte sans aucun réseau, mode avion inactif. */
        val Disconnected = NetworkContext(transport = NetworkTransport.NONE)
    }
}
