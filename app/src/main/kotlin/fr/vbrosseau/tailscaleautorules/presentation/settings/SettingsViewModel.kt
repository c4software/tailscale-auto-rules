package fr.vbrosseau.tailscaleautorules.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de l'écran des paramètres (SPECS.md §6.3). */
data class SettingsUiState(
    val settings: AppSettings = AppSettings.Defaults,
)

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAppSettings().collect { settings ->
                _uiState.value = SettingsUiState(settings)
            }
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
