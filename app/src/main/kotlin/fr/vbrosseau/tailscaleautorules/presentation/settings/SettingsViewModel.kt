package fr.vbrosseau.tailscaleautorules.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.automation.NotificationRefresher
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.settings.AppSettings
import fr.vbrosseau.tailscaleautorules.presentation.SystemStatus
import fr.vbrosseau.tailscaleautorules.presentation.UiStateSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val canReadSsid: Boolean = false,
    val canReadSsidInBackground: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val versionName: String = "",
    val isLoading: Boolean = false,
) {
    /**
     * L'automatisation est active, mais la permission de notification manque.
     *
     * Le service démarre sans elle ; sa notification reste simplement
     * invisible, et l'utilisateur ne voit alors plus rien de l'état du tunnel.
     */
    val needsNotificationPermission: Boolean
        get() = settings.notificationIsVisible && !canNotify

    /**
     * Le démarrage au boot est demandé, mais la localisation n'est accordée
     * que « pendant l'utilisation » : Android refusera alors de démarrer le
     * service depuis l'arrière-plan, et l'automatisation attendra l'ouverture
     * de l'application. Sans localisation du tout, rien à proposer — la
     * permission d'arrière-plan ne s'accorde qu'au-dessus de celle de premier
     * plan, que seules les règles Wi-Fi justifient.
     */
    val needsBackgroundLocation: Boolean
        get() = settings.isServiceEnabled &&
            settings.startOnBoot &&
            canReadSsid &&
            !canReadSsidInBackground
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
    private val notificationRefresher: NotificationRefresher,
) : ViewModel() {

    private val systemState = MutableStateFlow(SystemState())

    /**
     * Les valeurs d'usine d'`AppSettings` sont indiscernables de réglages
     * réels : l'état attend la première lecture de DataStore, sans quoi chaque
     * interrupteur s'afficherait en position d'usine avant de sauter sur sa
     * vraie valeur.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeAppSettings(),
        systemState,
    ) { settings, system ->
        SettingsUiState(
            settings = settings,
            canNotify = system.canNotify,
            canReadSsid = system.canReadSsid,
            canReadSsidInBackground = system.canReadSsidInBackground,
            isIgnoringBatteryOptimizations = system.isIgnoringBatteryOptimizations,
            versionName = system.versionName,
        )
    }.stateIn(viewModelScope, UiStateSharing, SettingsUiState(isLoading = true))

    init {
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
        systemState.value = SystemState(
            canNotify = systemStatus.canNotify(),
            canReadSsid = systemStatus.canReadSsid(),
            canReadSsidInBackground = systemStatus.canReadSsidInBackground(),
            isIgnoringBatteryOptimizations = systemStatus.isIgnoringBatteryOptimizations(),
            versionName = systemStatus.versionName,
        )

        // L'utilisateur peut avoir accordé la permission depuis les réglages
        // système : sans ce rappel, la notification ne serait publiée qu'à la
        // prochaine modification d'un réglage.
        //
        // Seule la notification est réalignée, jamais l'observation : relancer
        // le service à chaque reprise d'écran n'aurait aucun effet utile.
        viewModelScope.launch { notificationRefresher.refreshNotificationIfEnabled() }
    }

    fun setServiceEnabled(enabled: Boolean) = update { it.copy(isServiceEnabled = enabled) }

    /**
     * L'apprentissage des gestes (SPECS.md §3.3). Basculer marque aussi la
     * question du premier lancement comme posée : un choix pris ici la rendrait
     * redondante sur l'accueil.
     */
    fun setLearningEnabled(enabled: Boolean) =
        update { it.copy(isLearningEnabled = enabled, isLearningPrompted = true) }

    fun setStartOnBoot(enabled: Boolean) = update { it.copy(startOnBoot = enabled) }

    fun setVerboseLogging(enabled: Boolean) = update { it.copy(verboseLogging = enabled) }

    /**
     * Rouvre le parcours de premier lancement (SPECS.md §6.5).
     *
     * Rouvrir la question suffit : le portail de la racine observe cette
     * préférence et remplace l'ossature sur-le-champ ; la clôture du parcours
     * la reposera.
     */
    fun replayOnboarding() = update { it.copy(isOnboardingDone = false) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateAppSettings(transform) }
    }

    /** Ce que seule la plateforme sait, reconstaté à chaque reprise d'écran. */
    private data class SystemState(
        val canNotify: Boolean = true,
        val canReadSsid: Boolean = false,
        val canReadSsidInBackground: Boolean = true,
        val isIgnoringBatteryOptimizations: Boolean = true,
        val versionName: String = "",
    )
}
