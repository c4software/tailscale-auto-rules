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
        observeTunnel()
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
            // L'état affiché n'est pas relu ici : la commande est asynchrone et
            // la relecture arriverait avant que le tunnel ait bougé.
            // `observeTunnel` le mettra à jour dès que le changement aura eu
            // lieu — ou ne le mettra pas à jour s'il n'a pas lieu, ce qui est
            // précisément l'information utile.
            _uiState.update { it.copy(isSynchronizing = false) }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkObserver.observe().collect { context ->
                _uiState.update { it.copy(transport = context.transport, ssid = context.ssid) }
            }
        }
    }

    /**
     * Suit l'état réel du tunnel, quelle qu'en soit la cause.
     *
     * Il peut être coupé depuis le client officiel ou une tuile de réglages
     * rapides, sans que l'application y soit pour rien. L'observer est le seul
     * moyen d'afficher un état constaté plutôt que supposé.
     */
    private fun observeTunnel() {
        viewModelScope.launch {
            val isInstalled = controller.isAvailable()
            _uiState.update { it.copy(isTailscaleInstalled = isInstalled) }

            if (!isInstalled) {
                _uiState.update { it.copy(tunnelState = TunnelState.UNKNOWN) }
                return@launch
            }

            controller.observeRunning().collect { isRunning ->
                _uiState.update {
                    it.copy(
                        tunnelState = if (isRunning) TunnelState.ENABLED else TunnelState.DISABLED,
                    )
                }
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

}
