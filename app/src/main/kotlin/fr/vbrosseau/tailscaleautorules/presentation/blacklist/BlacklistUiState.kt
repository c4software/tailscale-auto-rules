package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import fr.vbrosseau.tailscaleautorules.domain.model.BlacklistedSsid

/**
 * État de l'écran de gestion des réseaux de confiance.
 *
 * [currentSsid] est `null` lorsque le SSID courant est indisponible ; l'action
 * d'ajout rapide est alors sans objet, ce que [canAddCurrentSsid] exprime
 * directement plutôt que de laisser l'interface le recalculer.
 */
data class BlacklistUiState(
    val entries: List<BlacklistedSsid> = emptyList(),
    val currentSsid: String? = null,
    val isCurrentSsidAlreadyListed: Boolean = false,
    val canReadSsid: Boolean = true,
    val error: BlacklistError? = null,
    val isLoading: Boolean = false,
) {
    val canAddCurrentSsid: Boolean
        get() = currentSsid != null && !isCurrentSsidAlreadyListed

    /**
     * L'explication de permission n'a de sens que sur cet écran, et seulement
     * si la localisation manque : c'est ici que l'utilisateur découvre que ses
     * réseaux de confiance ne pourront pas être reconnus.
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
