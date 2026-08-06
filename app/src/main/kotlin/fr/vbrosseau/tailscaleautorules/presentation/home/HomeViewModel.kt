package fr.vbrosseau.tailscaleautorules.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alimente l'écran d'accueil.
 *
 * Il ne décide rien : la synchronisation est déléguée au cas d'usage, et l'état
 * affiché est **constaté**, jamais déduit de la dernière décision. C'est ce qui
 * rend visible une divergence entre ce qui a été demandé au client Tailscale et
 * ce qu'il a réellement fait (SPECS.md §3.3).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val networkObserver: NetworkObserver,
    private val journalRepository: JournalRepository,
    private val controller: TailscaleController,
    private val synchronizeTunnel: SynchronizeTunnelUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeNetwork()
        observeJournal()
        viewModelScope.launch { refreshTunnelState() }
    }

    /**
     * Synchronisation manuelle.
     *
     * Elle passe par le cas d'usage sans contexte, qui relit donc le réseau
     * sans attendre le debounce — c'est ce que l'utilisateur attend d'un bouton.
     */
    fun synchronize() {
        if (_uiState.value.isSynchronizing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSynchronizing = true) }
            synchronizeTunnel()
            refreshTunnelState()
            _uiState.update { it.copy(isSynchronizing = false) }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkObserver.observe().collect { context ->
                _uiState.update { it.copy(transport = context.transport, ssid = context.ssid) }
                // Le tunnel peut avoir bougé pendant le changement de réseau :
                // on reconstate plutôt que de supposer.
                refreshTunnelState()
            }
        }
    }

    private fun observeJournal() {
        viewModelScope.launch {
            journalRepository.observeRecent().collect { entries ->
                _uiState.update { it.copy(lastChange = entries.firstOrNull()) }
            }
        }
    }

    private suspend fun refreshTunnelState() {
        val isInstalled = controller.isAvailable()
        _uiState.update {
            it.copy(
                isTailscaleInstalled = isInstalled,
                tunnelState = when {
                    !isInstalled -> TunnelState.UNKNOWN
                    controller.isRunning() -> TunnelState.ENABLED
                    else -> TunnelState.DISABLED
                },
            )
        }
    }
}
