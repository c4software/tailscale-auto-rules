package fr.vbrosseau.tailscaleautorules.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État de l'écran des paramètres (SPECS.md §6.3).
 *
 * [needsNotificationPermission] est dérivé ici plutôt qu'à l'écran : c'est une
 * règle d'affichage, et la recalculer dans un Composable la disperserait.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings.Defaults,
    val canNotify: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val versionName: String = "",
) {
    /** L'option est demandée, mais la permission manque. */
    val needsNotificationPermission: Boolean
        get() = settings.showPersistentNotification && !canNotify
}

/**
 * Alimente l'écran des paramètres.
 *
 * Chaque bascule est une opération nommée plutôt qu'un `update` générique
 * exposé à l'interface : un Composable ne doit pas pouvoir composer un état
 * arbitraire.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val systemStatus: SystemStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAppSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        refreshSystemStatus()
    }

    /**
     * Relit ce que seule la plateforme sait.
     *
     * Permission de notification et exemption de batterie se modifient **hors**
     * de l'application. Il faut donc les reconstater au retour à l'écran, faute
     * de quoi l'interface afficherait durablement un état périmé.
     */
    fun refreshSystemStatus() {
        _uiState.update {
            it.copy(
                canNotify = systemStatus.canNotify(),
                isIgnoringBatteryOptimizations = systemStatus.isIgnoringBatteryOptimizations(),
                versionName = systemStatus.versionName,
            )
        }
    }

    fun setServiceEnabled(enabled: Boolean) = update { it.copy(isServiceEnabled = enabled) }

    fun setStartOnBoot(enabled: Boolean) = update { it.copy(startOnBoot = enabled) }

    fun setPersistentNotification(enabled: Boolean) =
        update { it.copy(showPersistentNotification = enabled) }

    fun setVerboseLogging(enabled: Boolean) = update { it.copy(verboseLogging = enabled) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateAppSettings(transform) }
    }
}
