package fr.vbrosseau.tailscaleautorules.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.presentation.UiStateSharing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État du portail de premier lancement (SPECS.md §6.5).
 *
 * [isLoading] retient l'ossature le temps de la première lecture de
 * DataStore : sans lui, l'application afficherait fugitivement le parcours à
 * chaque démarrage — la valeur d'usine étant « non clos » — avant de sauter
 * sur l'écran d'accueil.
 */
data class OnboardingUiState(
    val isDone: Boolean = false,
    val isLoading: Boolean = false,
)

/** Décide si le parcours de premier lancement doit se montrer, et le clôt. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<OnboardingUiState> = settingsRepository.observeAppSettings()
        .map { OnboardingUiState(isDone = it.isOnboardingDone) }
        .stateIn(viewModelScope, UiStateSharing, OnboardingUiState(isLoading = true))

    /**
     * Clôt le parcours sur la réponse de la dernière page.
     *
     * Le choix d'apprentissage est enregistré **et** la question marquée comme
     * posée : le parcours ne revient jamais, quelle que soit la réponse, et le
     * réglage reste modifiable aux paramètres.
     */
    fun finish(learningEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAppSettings {
                it.copy(
                    isOnboardingDone = true,
                    isLearningEnabled = learningEnabled,
                    isLearningPrompted = true,
                )
            }
        }
    }
}
