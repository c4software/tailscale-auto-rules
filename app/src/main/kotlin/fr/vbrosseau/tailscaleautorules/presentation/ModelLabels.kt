package fr.vbrosseau.tailscaleautorules.presentation

import androidx.annotation.StringRes
import fr.vbrosseau.tailscaleautorules.R
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState

/**
 * Libellés des types du domaine.
 *
 * Le `when` est exhaustif sans branche `else` : ajouter une valeur au domaine
 * casse alors la compilation ici, ce qui garantit qu'aucun état ne s'affichera
 * jamais sans libellé.
 */
@StringRes
fun TunnelState.labelRes(): Int = when (this) {
    TunnelState.ENABLED -> R.string.home_tunnel_enabled
    TunnelState.DISABLED -> R.string.home_tunnel_disabled
    TunnelState.UNKNOWN -> R.string.home_tunnel_unknown
}

@StringRes
fun NetworkTransport.labelRes(): Int = when (this) {
    NetworkTransport.NONE -> R.string.home_transport_none
    NetworkTransport.WIFI -> R.string.home_transport_wifi
    NetworkTransport.CELLULAR -> R.string.home_transport_cellular
    NetworkTransport.ETHERNET -> R.string.home_transport_ethernet
    NetworkTransport.OTHER -> R.string.home_transport_other
}
