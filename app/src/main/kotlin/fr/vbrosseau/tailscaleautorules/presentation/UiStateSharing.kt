package fr.vbrosseau.tailscaleautorules.presentation

import kotlinx.coroutines.flow.SharingStarted

/**
 * Politique de publication commune aux `uiState` des ViewModels.
 *
 * `WhileSubscribed` coupe les observations — rappels réseau, lecture continue
 * de Room — dès que plus aucun écran ne collecte : un ViewModel en vie ne
 * justifie pas de garder des callbacks système enregistrés pendant que
 * l'application est en arrière-plan. Les cinq secondes de grâce couvrent une
 * rotation ou un passage éclair par une autre destination sans tout
 * réenregistrer.
 */
val UiStateSharing: SharingStarted = SharingStarted.WhileSubscribed(5_000)
