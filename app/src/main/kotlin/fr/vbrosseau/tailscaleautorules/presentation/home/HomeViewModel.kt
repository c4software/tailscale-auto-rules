package fr.vbrosseau.tailscaleautorules.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkContext
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeEverything()
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
            // `observeEverything` le mettra à jour dès que le changement aura eu
            // lieu — ou ne le mettra pas à jour s'il n'a pas lieu, ce qui est
            // précisément l'information utile.
            _uiState.update { it.copy(isSynchronizing = false) }
        }
    }

    /**
     * Coupe l'automatisation depuis l'accueil.
     *
     * Comme l'action de la notification, elle ne fait que basculer la
     * préférence : l'observation des réglages tenue par l'application arrête le
     * service et retire la notification, et `observeEverything` reflète le
     * nouvel état.
     */
    fun disableAutomation() {
        viewModelScope.launch {
            settingsRepository.updateAppSettings { it.copy(isServiceEnabled = false) }
        }
    }

    /**
     * Les sources sont **combinées** : rien ne s'affiche tant que chacune n'a
     * pas livré un premier constat. Collectées séparément, elles rempliraient
     * l'écran morceau par morceau — tunnel inconnu, puis réseau, puis journal —
     * et chaque valeur par défaut passerait à l'écran pour une donnée.
     */
    private fun observeEverything() {
        viewModelScope.launch {
            combine(
                // Le flux stabilisé n'émet qu'après sa fenêtre de debounce : à
                // l'ouverture de l'écran, cette attente serait un écran vide.
                // Un premier constat immédiat la couvre ; la stabilisation
                // reprend ses droits pour les transitions suivantes.
                networkObserver.observe().onStart { emit(networkObserver.current()) },
                tunnelSnapshots(),
                journalRepository.observeRecent(),
                settingsRepository.observeAppSettings(),
                ::Observation,
            ).collect { observed ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transport = observed.network.transport,
                        ssid = observed.network.ssid,
                        isTailscaleInstalled = observed.tunnel.isInstalled,
                        tunnelState = observed.tunnel.state,
                        lastChange = observed.entries.firstOrNull(),
                        isAutomationEnabled = observed.settings.isServiceEnabled,
                    )
                }
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
    private fun tunnelSnapshots(): Flow<TunnelSnapshot> = flow {
        if (!controller.isAvailable()) {
            emit(TunnelSnapshot(isInstalled = false, state = TunnelState.UNKNOWN))
            return@flow
        }

        emitAll(
            controller.observeRunning().map { isRunning ->
                TunnelSnapshot(
                    isInstalled = true,
                    state = if (isRunning) TunnelState.ENABLED else TunnelState.DISABLED,
                )
            },
        )
    }

    /** Ce que le client Tailscale laisse constater : présent, et actif ou non. */
    private data class TunnelSnapshot(val isInstalled: Boolean, val state: TunnelState)

    /** Premier constat complet, tel que `combine` le livre. */
    private data class Observation(
        val network: NetworkContext,
        val tunnel: TunnelSnapshot,
        val entries: List<JournalEntry>,
        val settings: AppSettings,
    )
}
