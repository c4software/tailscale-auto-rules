package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreference
import fr.vbrosseau.tailscaleautorules.domain.model.NetworkPreferenceKey
import fr.vbrosseau.tailscaleautorules.domain.model.TunnelState
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.DuplicateSsidException
import fr.vbrosseau.tailscaleautorules.domain.repository.NetworkPreferenceRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.SettingsRepository
import fr.vbrosseau.tailscaleautorules.domain.rule.MobileNetworkRule
import fr.vbrosseau.tailscaleautorules.domain.usecase.SynchronizeTunnelUseCase
import fr.vbrosseau.tailscaleautorules.presentation.SystemStatus
import fr.vbrosseau.tailscaleautorules.presentation.UiStateSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alimente l'écran des réseaux.
 *
 * Les règles — unicité, forme canonique, remplacement par la dernière volonté
 * — appartiennent au repository des préférences. Ce ViewModel se contente de
 * **traduire** ses échecs en quelque chose que l'interface sait afficher, et
 * de relancer un cycle après chaque modification (SPECS.md §5) : un changement
 * de préférence doit produire son effet immédiatement, pas au prochain
 * changement de réseau.
 */
@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val preferenceRepository: NetworkPreferenceRepository,
    private val settingsRepository: SettingsRepository,
    private val synchronizeTunnel: SynchronizeTunnelUseCase,
    private val systemStatus: SystemStatus,
    networkObserver: NetworkObserver,
) : ViewModel() {

    private val error = MutableStateFlow<BlacklistError?>(null)
    private val canReadSsid = MutableStateFlow(systemStatus.canReadSsid())

    /**
     * Instance locale plutôt qu'injectée : seuls son identifiant et ses
     * réglages par défaut servent ici, et la règle est sans état.
     */
    private val mobileRule = MobileNetworkRule()

    private val isMobileRuleEnabled = settingsRepository.observeRuleSettings()
        .map { settings -> (settings[mobileRule.id] ?: mobileRule.defaultSettings).isEnabled }

    /**
     * Le SSID courant n'enrichit que l'ajout rapide : il démarre à « inconnu »
     * pour que la liste ne l'attende jamais. Le flux réseau est stabilisé par
     * une fenêtre de deux secondes et reste muet tant qu'aucun réseau ne
     * correspond — en faire une condition d'affichage retenait l'écran entier.
     */
    private val currentSsid = networkObserver.observe()
        .map { it.ssid }
        .onStart { emit(null) }

    /**
     * Publié en `WhileSubscribed` : l'observation du réseau ne vit que
     * lorsqu'un écran regarde.
     */
    val uiState: StateFlow<BlacklistUiState> = combine(
        preferenceRepository.observeAll(),
        currentSsid,
        canReadSsid,
        isMobileRuleEnabled,
        error,
    ) { preferences, ssid, canRead, mobileRuleEnabled, currentError ->
        BlacklistUiState(
            preferences = preferences,
            currentSsid = ssid,
            isCurrentSsidAlreadyListed = ssid != null &&
                preferences.any { it.key == NetworkPreferenceKey.forWifi(ssid) },
            canReadSsid = canRead,
            isMobileRuleEnabled = mobileRuleEnabled,
            error = currentError,
        )
    }.stateIn(viewModelScope, UiStateSharing, BlacklistUiState(isLoading = true))

    /**
     * Relit l'autorisation de lecture du SSID.
     *
     * Elle s'accorde dans les réglages système : sans reconstat au retour à
     * l'écran, l'explication resterait affichée alors que la permission vient
     * d'être donnée.
     */
    fun refreshSystemStatus() {
        canReadSsid.value = systemStatus.canReadSsid()
    }

    /**
     * Active ou désactive la règle « Réseau mobile ».
     *
     * Un cycle est lancé dans la foulée (SPECS.md §5) : activer la règle en
     * étant déjà en données mobiles doit monter le tunnel immédiatement, pas au
     * prochain changement de réseau. La désactivation, elle, laisse l'état tel
     * quel — aucune règle ne se prononçant plus, le cycle ne commande rien. La
     * priorité éventuellement surchargée est conservée.
     */
    fun setMobileRuleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.currentRuleSettings()[mobileRule.id]
                ?: mobileRule.defaultSettings
            settingsRepository.setRuleSettings(mobileRule.id, current.copy(isEnabled = enabled))
            synchronizeTunnel()
        }
    }

    /**
     * Déclare une préférence — un réseau déjà connu voit simplement sa volonté
     * remplacée, comme le ferait un geste (SPECS.md §4.2).
     */
    fun add(ssid: String, tunnelEnabled: Boolean) {
        val trimmed = ssid.trim()
        if (trimmed.isEmpty()) {
            error.value = BlacklistError.BLANK
            return
        }
        viewModelScope.launch {
            preferenceRepository.upsert(
                key = NetworkPreferenceKey.forWifi(trimmed),
                ssid = trimmed,
                desiredState = if (tunnelEnabled) TunnelState.ENABLED else TunnelState.DISABLED,
            )
            error.value = null
            synchronizeTunnel()
        }
    }

    /** Déclare le réseau courant de confiance : tunnel toujours coupé. */
    fun addCurrentSsid() {
        val ssid = uiState.value.currentSsid ?: return
        add(ssid, tunnelEnabled = false)
    }

    /** Retourne la volonté d'un réseau, sans toucher à son identité. */
    fun setPreferenceEnabled(preference: NetworkPreference, tunnelEnabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.upsert(
                key = preference.key,
                ssid = preference.ssid,
                desiredState = if (tunnelEnabled) TunnelState.ENABLED else TunnelState.DISABLED,
            )
            synchronizeTunnel()
        }
    }

    fun rename(id: Long, ssid: String) {
        val trimmed = ssid.trim()
        if (trimmed.isEmpty()) {
            error.value = BlacklistError.BLANK
            return
        }
        viewModelScope.launch {
            error.value = preferenceRepository.update(id, trimmed).toError()
            if (error.value == null) synchronizeTunnel()
        }
    }

    /** Rend le réseau à l'automatisme, par un cycle immédiat. */
    fun remove(id: Long) {
        viewModelScope.launch {
            preferenceRepository.remove(id)
            synchronizeTunnel()
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun Result<Unit>.toError(): BlacklistError? = exceptionOrNull()?.let { cause ->
        when (cause) {
            is DuplicateSsidException -> BlacklistError.DUPLICATE
            is IllegalArgumentException -> BlacklistError.BLANK
            else -> BlacklistError.UNKNOWN
        }
    }
}
