package fr.vbrosseau.tailscaleautorules.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkExceptionKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.tailscale.TailscaleController
import fr.vbrosseau.tailscaleautorules.domain.usecase.DetectManualOverrideUseCase
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.presentation.UiStateSharing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
    networkObserver: NetworkObserver,
    journalRepository: JournalRepository,
    private val controller: TailscaleController,
    private val synchronizeTunnel: SynchronizeTunnelUseCase,
    private val detectManualOverride: DetectManualOverrideUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val isSynchronizing = MutableStateFlow(false)

    /**
     * Les sources sont **combinées** : rien ne s'affiche tant que chacune n'a
     * pas livré un premier constat. Collectées séparément, elles rempliraient
     * l'écran morceau par morceau — tunnel inconnu, puis réseau, puis journal —
     * et chaque valeur par défaut passerait à l'écran pour une donnée.
     *
     * Publié en `WhileSubscribed` : les rappels système — réseau et VPN — ne
     * restent pas enregistrés quand aucun écran ne regarde, et chaque retour à
     * l'écran repart d'un constat frais.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        // Le flux stabilisé n'émet qu'après sa fenêtre de debounce : à
        // l'ouverture de l'écran, cette attente serait un écran vide. Un
        // premier constat immédiat la couvre ; la stabilisation reprend ses
        // droits pour les transitions suivantes.
        networkObserver.observe().onStart { emit(networkObserver.current()) },
        tunnelSnapshots(),
        journalRepository.observeRecent(),
        settingsRepository.observeAppSettings(),
        isSynchronizing,
    ) { network, tunnel, entries, settings, synchronizing ->
        val lastChange = entries.firstOrNull()

        HomeUiState(
            tunnelState = tunnel.state,
            transport = network.transport,
            ssid = network.ssid,
            isTailscaleInstalled = tunnel.isInstalled,
            isSynchronizing = synchronizing,
            lastChange = lastChange,
            isAutomationEnabled = settings.isServiceEnabled,
            // Recalculé à chaque constat : un tunnel activé à la main sur un
            // réseau de confiance doit se dire comme tel, pas s'afficher comme
            // si une règle l'avait voulu.
            manualOverride = detectManualOverride(network, tunnel.state, lastChange),
            willMemorizeManualGesture = settings.isLearningEnabled &&
                NetworkExceptionKey.from(network) != null,
            isLearningPromptVisible = !settings.isLearningPrompted,
        )
    }.stateIn(viewModelScope, UiStateSharing, HomeUiState(isLoading = true))

    /**
     * Synchronisation manuelle.
     *
     * Elle passe par le cas d'usage sans contexte, qui relit donc le réseau
     * sans attendre le debounce — c'est ce que l'utilisateur attend d'un bouton.
     */
    fun synchronize() {
        if (isSynchronizing.value) return

        viewModelScope.launch {
            isSynchronizing.value = true
            synchronizeTunnel()
            // L'état affiché n'est pas relu ici : la commande est asynchrone et
            // la relecture arriverait avant que le tunnel ait bougé. Le flux
            // combiné le mettra à jour dès que le changement aura eu lieu — ou
            // ne le mettra pas à jour s'il n'a pas lieu, ce qui est précisément
            // l'information utile.
            isSynchronizing.value = false
        }
    }

    /**
     * Coupe l'automatisation depuis l'accueil.
     *
     * Comme l'action de la notification, elle ne fait que basculer la
     * préférence : l'observation des réglages tenue par l'application arrête le
     * service et retire la notification, et le flux combiné reflète le nouvel
     * état.
     */
    fun disableAutomation() {
        viewModelScope.launch {
            settingsRepository.updateAppSettings { it.copy(isServiceEnabled = false) }
        }
    }

    /**
     * Répond à l'invitation du premier lancement (SPECS.md §6.1).
     *
     * Le choix est enregistré **et** la question marquée comme posée : quelle
     * que soit la réponse, l'invitation ne revient jamais — le réglage reste
     * modifiable aux paramètres.
     */
    fun chooseLearning(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAppSettings {
                it.copy(isLearningEnabled = enabled, isLearningPrompted = true)
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
}
