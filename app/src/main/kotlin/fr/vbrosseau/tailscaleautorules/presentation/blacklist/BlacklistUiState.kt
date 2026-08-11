package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference

/**
 * État de l'écran des réseaux.
 *
 * Une seule liste : les préférences de réseau (SPECS.md §4.2), déclarées ou
 * apprises. [currentSsid] est `null` lorsque le SSID courant est
 * indisponible ; l'action d'ajout rapide est alors sans objet, ce que
 * [canAddCurrentSsid] exprime directement plutôt que de laisser l'interface
 * le recalculer.
 */
data class BlacklistUiState(
    val preferences: List<NetworkPreference> = emptyList(),
    val currentSsid: String? = null,
    val isCurrentSsidAlreadyListed: Boolean = false,
    val canReadSsid: Boolean = true,
    val isMobileRuleEnabled: Boolean = true,
    val error: BlacklistError? = null,
    val isLoading: Boolean = false,
) {
    val canAddCurrentSsid: Boolean
        get() = currentSsid != null && !isCurrentSsidAlreadyListed

    /**
     * L'explication de permission n'a de sens que sur cet écran, et seulement
     * si la localisation manque : c'est ici que l'utilisateur découvre que ses
     * réseaux ne pourront pas être reconnus.
     */
    val needsLocationPermission: Boolean
        get() = !canReadSsid
}

/** Échecs qu'un écran doit savoir raconter à l'utilisateur. */
enum class BlacklistError {
    DUPLICATE,
    BLANK,
    UNKNOWN,
}
