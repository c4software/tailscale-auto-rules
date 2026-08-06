package fr.vbrosseau.tailscaleautorules.domain.model

/**
 * État du tunnel Tailscale tel que l'application le perçoit.
 *
 * [UNKNOWN] n'est pas un cas dégradé mais un état nominal : le client officiel
 * peut être absent, ou ne pas avoir encore répondu. Le distinguer évite de
 * confondre « désactivé » et « indéterminé », et donc d'agir à tort.
 */
enum class TunnelState {
    ENABLED,
    DISABLED,
    UNKNOWN,
}
