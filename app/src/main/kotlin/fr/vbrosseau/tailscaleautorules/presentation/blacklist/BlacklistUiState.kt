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
    val error: BlacklistError? = null,
) {
    val canAddCurrentSsid: Boolean
        get() = currentSsid != null && !isCurrentSsidAlreadyListed
}

/** Échecs qu'un écran doit savoir raconter à l'utilisateur. */
enum class BlacklistError {
    DUPLICATE,
    BLANK,
    UNKNOWN,
}
