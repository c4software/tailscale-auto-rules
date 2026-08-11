package fr.vbrosseau.tailscaleautorules.presentation.home

import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkTransport
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.usecase.ManualOverride

/**
 * Tout ce que l'écran d'accueil affiche, et rien d'autre.
 *
 * L'état est **complètement descriptif** : un Composable le rend sans jamais
 * rien dériver. Une valeur qui manquerait ici serait le signe d'un calcul en
 * train de fuir vers l'interface.
 *
 * [isLoading] distingue « rien n'a encore été constaté » des valeurs par
 * défaut : sans lui, l'écran rendrait un tunnel inconnu et aucun réseau le
 * temps de la première observation, et ce vide se lirait comme une donnée.
 */
data class HomeUiState(
    val tunnelState: TunnelState = TunnelState.UNKNOWN,
    val transport: NetworkTransport = NetworkTransport.NONE,
    val ssid: String? = null,
    val isTailscaleInstalled: Boolean = true,
    val isSynchronizing: Boolean = false,
    val lastChange: JournalEntry? = null,
    val isAutomationEnabled: Boolean = true,
    val manualOverride: ManualOverride? = null,
    /**
     * Un geste constaté sera mémorisé comme exception (SPECS.md §3.3) :
     * apprentissage actif **et** réseau identifiable. La carte d'intervention
     * manuelle en tire son texte — annoncer une mémorisation qui n'aura pas
     * lieu serait un mensonge.
     */
    val willMemorizeManualGesture: Boolean = false,
    /** L'invitation unique du premier lancement (SPECS.md §6.1). */
    val isLearningPromptVisible: Boolean = false,
    val isLoading: Boolean = false,
)
