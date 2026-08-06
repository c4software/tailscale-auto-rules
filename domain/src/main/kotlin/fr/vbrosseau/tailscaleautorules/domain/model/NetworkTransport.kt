package fr.vbrosseau.tailscaleautorules.domain.model

/**
 * Transport porteur de la connexion courante.
 *
 * [NONE] modélise explicitement l'absence de réseau plutôt qu'un `null` : une
 * règle traite ce cas comme n'importe quel autre, sans branchement particulier.
 *
 * [OTHER] couvre les transports que l'application n'a pas de raison de
 * distinguer (Bluetooth, VPN, USB…). Le jour où une règle en a besoin, la
 * valeur correspondante est ajoutée ici.
 */
enum class NetworkTransport {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
}
