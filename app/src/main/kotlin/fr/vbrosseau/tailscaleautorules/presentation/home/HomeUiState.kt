package fr.vbrosseau.tailscaleautorules.presentation.home

import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState

/**
 * Tout ce que l'écran d'accueil affiche, et rien d'autre.
 *
 * L'état est **complètement descriptif** : un Composable le rend sans jamais
 * rien dériver. Une valeur qui manquerait ici serait le signe d'un calcul en
 * train de fuir vers l'interface.
 */
data class HomeUiState(
    val tunnelState: TunnelState = TunnelState.UNKNOWN,
    val transport: NetworkTransport = NetworkTransport.NONE,
    val ssid: String? = null,
    val isTailscaleInstalled: Boolean = true,
    val isSynchronizing: Boolean = false,
    val lastChange: JournalEntry? = null,
)
